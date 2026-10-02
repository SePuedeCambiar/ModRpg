package com.example.modrpg.ai.nemesis.goals;

import com.example.modrpg.ai.nemesis.NemesisCaptain;
import com.example.modrpg.ai.nemesis.NemesisDialogueHelper;
import com.example.modrpg.ai.nemesis.NemesisSavedData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Meta de Supervivencia Némesis (Sprint 4):
 * Cuando la vida del capitán cae por debajo del 25%, detona una bomba de humo,
 * aplica ceguera táctica y huye buscando rutas de escape transitables (en cuevas o superficie).
 * Al romper la línea de visión o ganar distancia, guarda su progreso en disco y despawnea.
 */
public class NemesisEscapeGoal extends Goal {

    private final Mob mob;
    private ServerPlayer targetPlayer = null;
    private Vec3 lastKnownPlayerPos = null;

    private boolean smokeTriggered = false;
    private int escapeTicks = 0;
    private int pathRecalcDelay = 0;

    public NemesisEscapeGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!mob.getTags().contains("modrpg_nemesis_captain") || mob.isPassenger()) {
            return false;
        }

        // Se activa cuando la vida cae a menos del 25%
        if (mob.getHealth() >= (mob.getMaxHealth() * 0.25f)) {
            return false;
        }

        if (mob.getTarget() instanceof ServerPlayer player && player.isAlive()) {
            this.targetPlayer = player;
            this.lastKnownPlayerPos = player.position();
            return true;
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        // SPRINT 4 FIX: No depender de mob.getTarget() != null porque la ceguera/humo anula el target vanilla
        return escapeTicks < 240 && (targetPlayer != null || lastKnownPlayerPos != null);
    }

    @Override
    public void start() {
        this.smokeTriggered = false;
        this.escapeTicks = 0;
        this.pathRecalcDelay = 0;

        if (mob.getTarget() instanceof ServerPlayer player) {
            this.targetPlayer = player;
            this.lastKnownPlayerPos = player.position();
        }
    }

    @Override
    public void stop() {
        this.escapeTicks = 0;
        this.pathRecalcDelay = 0;
        this.targetPlayer = null;
        this.lastKnownPlayerPos = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) mob.level();

        // 1. Actualizar última posición conocida del jugador si sigue con vida
        if (targetPlayer != null && targetPlayer.isAlive()) {
            this.lastKnownPlayerPos = targetPlayer.position();
        }

        if (lastKnownPlayerPos == null) {
            this.stop();
            return;
        }

        // =========================================================================
        // 2. ESTALLIDO DE BOMBA DE HUMO Y CEGUERA TÁCTICA (Solo una vez al inicio)
        // =========================================================================
        if (!smokeTriggered) {
            smokeTriggered = true;

            // Cortina densa de humo
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, mob.getX(), mob.getY() + 0.5, mob.getZ(), 45, 1.2, 0.6, 1.2, 0.05);
            level.sendParticles(ParticleTypes.FLASH, mob.getX(), mob.getEyeY(), mob.getZ(), 2, 0, 0, 0, 0);

            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.4f, 0.7f);

            // Ceguera de 2 segundos si el jugador está en un radio de 8 bloques de la bomba
            if (targetPlayer != null && mob.distanceToSqr(targetPlayer) <= 64.0) {
                targetPlayer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            }

            // Diálogo dramático de huida
            UUID captainId = mob.getPersistentData().getUUID("modrpg_nemesis_uuid");
            var nemesisData = NemesisSavedData.get(level);
            var captain = nemesisData.getCaptain(captainId);
            if (captain != null && targetPlayer != null) {
                NemesisDialogueHelper.triggerEscape(level, mob, captain, targetPlayer);
            }
        }

        escapeTicks++;

        // =========================================================================
        // 3. NAVEGACIÓN 3D SEGURA LEJOS DEL JUGADOR (Pathfinding adaptativo en cuevas)
        // =========================================================================
        if (--pathRecalcDelay <= 0 || mob.getNavigation().isDone()) {
            pathRecalcDelay = 15; // Recalcular cada 0.75s para evitar lag de pathfinding continuo

            Vec3 fleePos = null;

            // SPRINT 4 FIX: Usar DefaultRandomPos nativo de Minecraft para encontrar bloques de aire transitables
            if (mob instanceof PathfinderMob pathfinderMob) {
                fleePos = DefaultRandomPos.getPosAway(pathfinderMob, 16, 7, lastKnownPlayerPos);
            }

            if (fleePos != null) {
                mob.getNavigation().moveTo(fleePos.x, fleePos.y, fleePos.z, 1.40);
            } else {
                // Fallback: Si DefaultRandomPos no encuentra salida inmediata (ej. túnel de 1x2),
                // proyectar un vector inverso suave hacia donde haya aire
                Vec3 awayDir = mob.position().subtract(lastKnownPlayerPos);
                if (awayDir.lengthSqr() > 1e-4) {
                    Vec3 fallback = mob.position().add(awayDir.normalize().scale(8.0));
                    mob.getNavigation().moveTo(fallback.x, fallback.y, fallback.z, 1.30);
                }
            }
        }

        // =========================================================================
        // 4. CONDICIONES DE ESCAPE EXITOSO Y DESPAWN SEGURO
        // =========================================================================
        double distSq = mob.distanceToSqr(lastKnownPlayerPos);
        boolean lostLoS = (targetPlayer == null) || !mob.getSensing().hasLineOfSight(targetPlayer);

        // El Némesis escapa con éxito si:
        // A) Supera los 26 bloques de distancia en línea recta (distSq > 676).
        // B) Rompió la línea de visión tras 4 segundos de huida y está a más de 12 bloques (distSq > 144).
        // C) Failsafe: lleva 10 segundos huyendo (escapeTicks > 200) y el jugador no lo ve.
        boolean canEscapeCleanly = (distSq > 676.0)
                || (lostLoS && escapeTicks > 80 && distSq > 144.0)
                || (lostLoS && escapeTicks > 200);

        if (canEscapeCleanly) {
            UUID captainId = mob.getPersistentData().getUUID("modrpg_nemesis_uuid");
            var nemesisData = NemesisSavedData.get(level);
            var captain = nemesisData.getCaptain(captainId);

            if (captain != null) {
                captain.setStatus(NemesisCaptain.Status.WAITING_REVENGE);
                captain.addPrestige(25); // Gana prestigio por sobrevivir
                nemesisData.addOrUpdateCaptain(captain);
            }

            // Limpieza de memoria temporal de intro/diálogos
            NemesisDialogueHelper.clearNemesisMemory(mob.getUUID());

            // Efecto de desvanecimiento
            level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
            mob.discard(); // Desaparece limpiamente del mundo sin soltar botín
        }
    }
}