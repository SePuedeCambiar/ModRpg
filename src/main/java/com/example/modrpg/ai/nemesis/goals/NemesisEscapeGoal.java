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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Meta de Supervivencia Némesis:
 * Si la vida del capitán cae a menos del 25% de HP, arroja una bomba de humo que ciega
 * al jugador, se burla y huye a toda velocidad. Al romper la línea de visión, se guarda en disco.
 */
public class NemesisEscapeGoal extends Goal {

    private final Mob mob;
    private boolean smokeTriggered = false;
    private int escapeTicks = 0;

    public NemesisEscapeGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!mob.getTags().contains("modrpg_nemesis_captain")) return false;
        return mob.getHealth() < (mob.getMaxHealth() * 0.25f) && mob.getTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && escapeTicks < 200;
    }

    @Override
    public void start() {
        this.smokeTriggered = false;
        this.escapeTicks = 0;
    }

    @Override
    public void tick() {
        if (!(mob.getTarget() instanceof ServerPlayer player)) return;
        ServerLevel level = (ServerLevel) mob.level();

        // 1. Estallido de bomba de humo y ceguera al jugador (una sola vez)
        if (!smokeTriggered) {
            smokeTriggered = true;

            // Partículas densas de humo de fogata
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, mob.getX(), mob.getY() + 0.5, mob.getZ(), 40, 1.2, 0.5, 1.2, 0.05);
            level.sendParticles(ParticleTypes.FLASH, mob.getX(), mob.getEyeY(), mob.getZ(), 2, 0, 0, 0, 0);

            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.4f, 0.7f);

            // Ceguera breve de 2 segundos si el jugador está a menos de 8 bloques de la bomba
            if (mob.distanceToSqr(player) <= 64.0) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            }

            // Diálogo de escape
            UUID captainId = mob.getPersistentData().getUUID("modrpg_nemesis_uuid");
            var nemesisData = NemesisSavedData.get(level);
            var captain = nemesisData.getCaptain(captainId);
            if (captain != null) {
                NemesisDialogueHelper.triggerEscape(level, mob, captain, player);
            }
        }

        escapeTicks++;

        // 2. Sprint de retirada en vector opuesto al jugador (Velocidad x1.40)
        Vec3 away = mob.position().subtract(player.position()).normalize().scale(16.0);
        mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.40);

        // 3. Despawn Seguro al escapar (Distancia > 26 bloques O rompe la línea de visión tras 4 segundos)
        double distSq = mob.distanceToSqr(player);
        boolean lostLoS = !mob.getSensing().hasLineOfSight(player);

        if ((distSq > 676.0 || (lostLoS && escapeTicks > 80))) {
            UUID captainId = mob.getPersistentData().getUUID("modrpg_nemesis_uuid");
            var nemesisData = NemesisSavedData.get(level);
            var captain = nemesisData.getCaptain(captainId);

            if (captain != null) {
                captain.setStatus(NemesisCaptain.Status.WAITING_REVENGE);
                captain.addPrestige(25); // Gana prestigio por sobrevivir
                nemesisData.addOrUpdateCaptain(captain);
            }

            level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 15, 0.3, 0.3, 0.3, 0.05);
            mob.discard(); // Desaparece limpiamente del mundo sin soltar botín
        }
    }
}