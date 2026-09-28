package com.example.modrpg.skills.nodes.melee;

import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketSyncMana;
import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.SkillEconomy;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class VitalCleaveSkill extends SkillNode {

    public static final int BASE_COOLDOWN_TICKS = 200;
    public static final int MAX_COOLDOWN_TICKS  = 400;

    public VitalCleaveSkill() {
        super(
                SkillRegistry.NODE_VITAL_CLEAVE,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Tajo Vital"),
                Component.literal("Estocada frontal que inflige el 10% de la vida actual del enemigo. Su tiempo de recarga aumenta según el daño infligido (10s a 20s máx.)."),
                NodeType.ACTIVE_ABILITY,
                0
        );
        this.setManaCost(15.0f);
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        ServerLevel level = (ServerLevel) player.level();
        player.swing(InteractionHand.MAIN_HAND, true);

        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double reach = 3.8;

        AABB scanBox = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.2);
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                scanBox,
                e -> e != player && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(player)
        );

        LivingEntity bestTarget = null;
        double closestDistSq = reach * reach;

        for (LivingEntity candidate : candidates) {
            Vec3 toCandidate = candidate.getBoundingBox().getCenter().subtract(eyePos);
            double distSq = toCandidate.lengthSqr();
            if (distSq <= closestDistSq) {
                double dot = look.dot(toCandidate.normalize());
                if (dot > 0.65) {
                    closestDistSq = distSq;
                    bestTarget = candidate;
                }
            }
        }

        // Caso Whiff: reembolso y sincronización sin desync
        if (bestTarget == null) {
            player.displayClientMessage(Component.literal("§c✖ No alcanzaste ningún objetivo."), true);
            skills.setCooldown(this.getId(), 30);
            skills.restoreMana(this.getManaCost());
            ModMessages.sendToPlayer(new PacketSyncMana(skills.getCurrentMana(), skills.getMaxMana()), player);
            SkillEconomy.syncSkills(player);
            return;
        }

        LivingEntity target = bestTarget;
        float currentHp = target.getHealth();

        float damage = Math.max(2.0f, currentHp * 0.10f);
        target.hurt(player.damageSources().playerAttack(player), damage);

        int additionalTicks = (int) (damage * 4.0f);
        int finalCooldownTicks = Math.min(MAX_COOLDOWN_TICKS, BASE_COOLDOWN_TICKS + additionalTicks);
        skills.setCooldown(this.getId(), finalCooldownTicks);

        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.3, 0.4, 0.3, 0.15);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + 1.0, target.getZ(), (int) Math.min(10, damage / 2), 0.2, 0.3, 0.2, 0.05);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 0.8, target.getZ(), 2, 0.1, 0.1, 0.1, 0.0);

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2f, 0.8f);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 1.4f);

        int cdSeconds = finalCooldownTicks / 20;
        player.displayClientMessage(
                Component.literal("§c§l⚔ ¡TAJO VITAL! §fDaño: §e" + String.format("%.1f", damage) +
                        " §7(10% vida actual) | Cooldown: §c" + cdSeconds + "s"),
                true
        );

        SkillEconomy.syncSkills(player);
    }

    public static int calculateCooldown(float damage) {
        int additionalTicks = (int) (damage * 4.0f);
        return Math.min(MAX_COOLDOWN_TICKS, BASE_COOLDOWN_TICKS + additionalTicks);
    }
}