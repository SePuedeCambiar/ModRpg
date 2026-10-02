package com.example.modrpg.ai.goals;

import com.example.modrpg.ai.EnemyArchetype;
import com.example.modrpg.ai.SquadCoordinator;
import com.example.modrpg.ai.feedback.SquadBarkManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Maniobra de Flanqueo Táctico F.E.A.R. (Sprint 4):
 * El hostigador ligero (como la Tejedora del Vacío) rodea al objetivo buscando su punto ciego real (> 75°).
 * - Incorpora anclaje al suelo 3D en cuevas y laderas.
 * - Si no existe ruta transitable al flanco, aborta limpiamente sin atascarse en la pared.
 * - Al situarse a espaldas del blanco, asesta un golpe crítico de emboscada.
 */
public class TacticalFlankGoal extends Goal {

    private final Mob mob;
    private final EnemyArchetype archetype;

    private Vec3 targetFlankPos = null;
    private int flankTimer = 0;
    private long lastFlankAttemptTick = -1000L;

    public TacticalFlankGoal(Mob mob, EnemyArchetype archetype) {
        this.mob = mob;
        this.archetype = archetype;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Solo para hostigadores ligeros y flanqueadores (no vanguardia pesada)
        if (archetype.isAggressiveRush() || mob.isPassenger()) return false;

        // No flanquear si está aturdido por postura rota
        if (mob.getTags().contains("modrpg_staggered")) return false;

        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        if (squad == null || squad.isInPanic()) return false;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        // Rango de flanqueo: entre 5 y 18 bloques
        double distSq = mob.distanceToSqr(target);
        if (distSq <= 25.0 || distSq >= 324.0) return false;

        // Cooldown de 3 segundos (60 ticks) entre maniobras de flanqueo
        long currentTick = mob.level().getGameTime();
        if (currentTick - lastFlankAttemptTick < 60L) return false;

        // SPRINT 4 FIX: Validar si el entorno 3D ofrece un suelo transitable para flanquear.
        // Si el jugador está pegado a la pared de una cueva y getFlankingPosition devuelve null,
        // canUse() retorna false, permitiendo que el mob ataque a distancia en vez de atascarse.
        Vec3 calculatedPos = squad.getFlankingPosition(mob, target);
        if (calculatedPos == null) return false;

        this.targetFlankPos = calculatedPos;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.getTags().contains("modrpg_staggered")) return false;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        if (squad == null || squad.isInPanic()) return false;

        double distSq = mob.distanceToSqr(target);

        // Termina si:
        // A) Se acerca a menos de 3 bloques (combate cuerpo a cuerpo inmediato).
        // B) Se aleja a más de 20 bloques.
        // C) Lleva más de 6 segundos (120 ticks) en la maniobra.
        // D) El punto de flanqueo ya no es válido.
        return distSq > 9.0 && distSq < 400.0 && flankTimer < 120 && targetFlankPos != null;
    }

    @Override
    public void start() {
        this.flankTimer = 0;

        if (targetFlankPos != null) {
            mob.getNavigation().moveTo(targetFlankPos.x, targetFlankPos.y, targetFlankPos.z, 1.25);
        }

        if (mob.level() instanceof ServerLevel level) {
            SquadBarkManager.triggerBark(mob, SquadBarkManager.BarkType.FLANKING, level);
        }
    }

    @Override
    public void stop() {
        this.targetFlankPos = null;
        this.flankTimer = 0;
        this.lastFlankAttemptTick = mob.level().getGameTime();
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            this.stop();
            return;
        }

        flankTimer++;

        // Recalcular el flanco cada segundo para adaptarse al movimiento del jugador
        if (flankTimer % 20 == 0) {
            recalculateFlankVector();
        }

        // Navegar hacia el punto lateral/trasero
        if (targetFlankPos != null) {
            mob.getNavigation().moveTo(targetFlankPos.x, targetFlankPos.y, targetFlankPos.z, 1.25);
        }

        // Failsafe de atasco en cuevas: si lleva 2 segundos intentando avanzar y la navegación se detuvo
        if (flankTimer > 40 && !mob.getNavigation().isInProgress()) {
            this.stop();
            return;
        }

        // =========================================================================
        // DETECCIÓN DE PUNTO CIEGO Y EMBOSCADA SORPRESA (> 75° respecto al frente)
        // =========================================================================
        Vec3 playerLook = target.getLookAngle().normalize();
        Vec3 toMob = mob.position().subtract(target.position()).normalize();
        double dotProduct = playerLook.dot(toMob);
        double distSq = mob.distanceToSqr(target);

        // Si dotProduct < 0.25, el mob está situado en el cuadrante ciego del jugador
        if (dotProduct < 0.25) {
            // A) Golpe directo de emboscada si entra en rango cuerpo a cuerpo (<= 4.5 bloques)
            if (distSq <= 20.25 && mob.level() instanceof ServerLevel level) {
                mob.doHurtTarget(target);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, mob.getX(), mob.getEyeY(), mob.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                        SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.2f, 1.4f);

                // Emboscada asestada con éxito: finalizar maniobra
                this.stop();
                return;
            }

            // B) Telegrafiado sensorial sutil (con debounce de 25 ticks para no spamear audio)
            if (flankTimer % 25 == 0 && distSq < 64.0 && mob.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getEyeY(), mob.getZ(), 4, 0.15, 0.15, 0.15, 0.05);
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                        SoundEvents.NOTE_BLOCK_SNARE.get(), SoundSource.HOSTILE, 0.8f, 1.8f);
            }
        }
    }

    private void recalculateFlankVector() {
        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        LivingEntity target = mob.getTarget();
        if (squad != null && target != null) {
            Vec3 newPos = squad.getFlankingPosition(mob, target);
            if (newPos != null) {
                this.targetFlankPos = newPos;
            } else {
                // Si el jugador se pegó a la pared y ya no hay flanco posible, terminar la meta limpiamente
                this.stop();
            }
        }
    }
}