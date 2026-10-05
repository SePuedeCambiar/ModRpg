package com.example.modrpg.ai.goals;

import com.example.modrpg.ai.EnemyArchetype;
import com.example.modrpg.ai.SquadCoordinator;
import com.example.modrpg.ai.feedback.SquadBarkManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Maniobra de Flanqueo Táctico F.E.A.R.:
 * El hostigador ligero (Tejedora del Vacío) rodea al objetivo buscando su punto ciego real (> 75°).
 * - SPRINT 1 FIX: requiresUpdateEveryTick() = true (20 Hz reales).
 * - SPRINT 3 FIX (B4): Rango de golpe acotado a 2.5m, crítico de emboscada (+50% daño) y swing.
 * - SPRINT 3 FIX (A3): Throttling de pathfinding cada 12 ticks para no saturar A*.
 * - SPRINT 4 FIX: Anclaje al suelo 3D en cuevas y laderas.
 */
public class TacticalFlankGoal extends Goal {

    private final Mob mob;
    private final EnemyArchetype archetype;

    private Vec3 targetFlankPos = null;
    private int flankTimer = 0;
    private long lastFlankAttemptTick = -1000L;

    // A3 FIX: Throttling de cálculo de ruta
    private int repathDelay = 0;
    private Vec3 lastNavPos = null;

    public TacticalFlankGoal(Mob mob, EnemyArchetype archetype) {
        this.mob = mob;
        this.archetype = archetype;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (mob == null) return false;

        // Solo para hostigadores ligeros y flanqueadores (no vanguardia pesada)
        if (archetype != null && archetype.isAggressiveRush() || mob.isPassenger()) return false;

        // No flanquear si está aturdido por postura rota
        if (mob.getTags().contains("modrpg_staggered")) return false;

        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        if (squad == null || squad.isInPanic()) return false;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        // Rango táctico de flanqueo: entre 5 y 18 bloques
        double distSq = mob.distanceToSqr(target);
        if (distSq <= 25.0 || distSq >= 324.0) return false;

        // Cooldown de 3 segundos (60 ticks) entre maniobras de flanqueo
        long currentTick = mob.level().getGameTime();
        if (currentTick - lastFlankAttemptTick < 60L) return false;

        // SPRINT 4 FIX: Validar si el entorno 3D ofrece un suelo transitable para flanquear.
        Vec3 calculatedPos = squad.getFlankingPosition(mob, target);
        if (calculatedPos == null) return false;

        this.targetFlankPos = calculatedPos;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (mob == null || mob.getTags().contains("modrpg_staggered")) return false;

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
        this.repathDelay = 0;
        this.lastNavPos = null;

        if (targetFlankPos != null) {
            mob.getNavigation().moveTo(targetFlankPos.x, targetFlankPos.y, targetFlankPos.z, 1.25);
            lastNavPos = targetFlankPos;
            repathDelay = 12;
        }

        if (mob.level() instanceof ServerLevel level) {
            SquadBarkManager.triggerBark(mob, SquadBarkManager.BarkType.FLANKING, level);
        }
    }

    @Override
    public void stop() {
        this.targetFlankPos = null;
        this.flankTimer = 0;
        this.repathDelay = 0;
        this.lastNavPos = null;
        this.lastFlankAttemptTick = (mob != null) ? mob.level().getGameTime() : -1000L;
        if (this.mob != null) {
            this.mob.getNavigation().stop();
        }
    }

    @Override
    public void tick() {
        if (mob == null) return;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            this.stop();
            return;
        }

        flankTimer++;

        // Recalcular el vector de flanco cada segundo para adaptarse al movimiento del jugador
        if (flankTimer % 20 == 0) {
            recalculateFlankVector();
        }

        // A3 FIX: Navegación regulada con throttling (evita recalcular A* cada tick)
        if (targetFlankPos != null) {
            if (--repathDelay <= 0 || lastNavPos == null || targetFlankPos.distanceToSqr(lastNavPos) > 4.0) {
                mob.getNavigation().moveTo(targetFlankPos.x, targetFlankPos.y, targetFlankPos.z, 1.25);
                lastNavPos = targetFlankPos;
                repathDelay = 12;
            }
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
            // B4 FIX: Golpe crítico de emboscada a 2.5 bloques (distSq <= 6.25) en lugar de 4.5 bloques
            if (distSq <= 6.25 && mob.level() instanceof ServerLevel level) {
                float baseDmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
                if (baseDmg <= 0.0f) baseDmg = 2.0f;

                // Aplica 50% de daño extra crítico por impacto en punto ciego y anima el brazo
                target.hurt(mob.damageSources().mobAttack(mob), baseDmg * 1.5f);
                mob.swing(InteractionHand.MAIN_HAND, true);

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
                // Si el jugador se pegó a una pared y ya no hay ruta viable, abortar limpiamente
                this.stop();
            }
        }
    }
}