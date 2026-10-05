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
 * Meta de Supervivencia Némesis:
 * SPRINT 1 FIX: requiresUpdateEveryTick() = true.
 * SPRINT 2 FIX (D4): Cooldown de escape para prevenir bucles infinitos de humo/ceguera.
 */
public class NemesisEscapeGoal extends Goal {

    private final Mob mob;
    private ServerPlayer targetPlayer = null;
    private Vec3 lastKnownPlayerPos = null;

    private boolean smokeTriggered = false;
    private int escapeTicks = 0;
    private int pathRecalcDelay = 0;
    // D4 FIX: Cooldown para evitar bucles continuos de bomba de humo en cuevas
    private int escapeCooldownTicks = 0;

    public NemesisEscapeGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (escapeCooldownTicks > 0) {
            escapeCooldownTicks--;
            return false;
        }

        if (mob == null || !mob.getTags().contains("modrpg_nemesis_captain") || mob.isPassenger()) {
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
        return escapeTicks < 240 && (targetPlayer != null || lastKnownPlayerPos != null);
    }

    @Override
    public void start() {
        this.smokeTriggered = false;
        this.escapeTicks = 0;
        this.pathRecalcDelay = 0;

        if (mob != null && mob.getTarget() instanceof ServerPlayer player) {
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
        // D4 FIX: Si la huida finaliza sin despawnear (ej. atascado en cueva), aplicar 10s de cooldown
        this.escapeCooldownTicks = 200;
        if (this.mob != null) {
            this.mob.getNavigation().stop();
        }
    }

    @Override
    public void tick() {
        if (mob == null || mob.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) mob.level();

        if (targetPlayer != null && targetPlayer.isAlive()) {
            this.lastKnownPlayerPos = targetPlayer.position();
        }

        if (lastKnownPlayerPos == null) {
            this.stop();
            return;
        }

        // Estallido de humo y ceguera táctica una sola vez al inicio de la huida
        if (!smokeTriggered) {
            smokeTriggered = true;

            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, mob.getX(), mob.getY() + 0.5, mob.getZ(), 45, 1.2, 0.6, 1.2, 0.05);
            level.sendParticles(ParticleTypes.FLASH, mob.getX(), mob.getEyeY(), mob.getZ(), 2, 0, 0, 0, 0);

            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.4f, 0.7f);

            if (targetPlayer != null && mob.distanceToSqr(targetPlayer) <= 64.0) {
                targetPlayer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            }

            UUID captainId = mob.getPersistentData().getUUID("modrpg_nemesis_uuid");
            var nemesisData = NemesisSavedData.get(level);
            var captain = nemesisData.getCaptain(captainId);
            if (captain != null && targetPlayer != null) {
                NemesisDialogueHelper.triggerEscape(level, mob, captain, targetPlayer);
            }
        }

        escapeTicks++;

        // Navegación lejos del jugador
        if (--pathRecalcDelay <= 0 || mob.getNavigation().isDone()) {
            pathRecalcDelay = 15;

            Vec3 fleePos = null;
            if (mob instanceof PathfinderMob pathfinderMob) {
                fleePos = DefaultRandomPos.getPosAway(pathfinderMob, 16, 7, lastKnownPlayerPos);
            }

            if (fleePos != null) {
                mob.getNavigation().moveTo(fleePos.x, fleePos.y, fleePos.z, 1.40);
            } else {
                Vec3 awayDir = mob.position().subtract(lastKnownPlayerPos);
                if (awayDir.lengthSqr() > 1e-4) {
                    Vec3 fallback = mob.position().add(awayDir.normalize().scale(8.0));
                    mob.getNavigation().moveTo(fallback.x, fallback.y, fallback.z, 1.30);
                }
            }
        }

        // Condiciones de escape exitoso
        double distSq = mob.distanceToSqr(lastKnownPlayerPos);
        boolean lostLoS = (targetPlayer == null) || !mob.getSensing().hasLineOfSight(targetPlayer);

        boolean canEscapeCleanly = (distSq > 676.0)
                || (lostLoS && escapeTicks > 80 && distSq > 144.0)
                || (lostLoS && escapeTicks > 200);

        if (canEscapeCleanly) {
            UUID captainId = mob.getPersistentData().getUUID("modrpg_nemesis_uuid");
            var nemesisData = NemesisSavedData.get(level);
            var captain = nemesisData.getCaptain(captainId);

            if (captain != null) {
                captain.setStatus(NemesisCaptain.Status.WAITING_REVENGE);
                captain.addPrestige(25);
                nemesisData.addOrUpdateCaptain(captain);
            }

            // D4 FIX: Ahora sí se limpia la memoria porque el mob desaparece definitivamente del mundo
            NemesisDialogueHelper.clearNemesisMemory(mob.getUUID());

            level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
            mob.discard();
        }
    }
}