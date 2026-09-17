package com.example.modrpg.skills.nodes.melee;

import com.example.modrpg.skills.PlayerSkills;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class SpinAttackSkill extends SkillNode {

    public SpinAttackSkill() {
        super(
                SkillRegistry.NODE_SPIN_ATTACK,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Torbellino 360°"),
                Component.literal("Ejecuta un giro con tu espada golpeando y empujando a todos los enemigos a tu alrededor."),
                NodeType.ACTIVE_ABILITY,
                120 // 6 segundos de cooldown (120 ticks)
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        ServerLevel level = (ServerLevel) player.level();
        double radius = 4.0;

        player.swing(InteractionHand.MAIN_HAND, true);

        AABB box = player.getBoundingBox().inflate(radius, 1.5, radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                e -> e != player && e.isAlive() && !e.isAlliedTo(player)
        );

        float baseDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float bonus = 1.0f + (skills.getBranchLevel(SkillRegistry.BRANCH_MELEE) * 0.015f);
        float totalDamage = baseDamage * bonus;

        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().playerAttack(player), totalDamage);
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            target.knockback(0.7, -dx, -dz);
        }

        // Partículas en círculo 360°
        int points = 16;
        for (int i = 0; i < points; i++) {
            double angle = i * (2 * Math.PI / points);
            double px = player.getX() + (Math.cos(angle) * 2.2);
            double pz = player.getZ() + (Math.sin(angle) * 2.2);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, px, player.getY() + 0.8, pz, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CRIT, px, player.getY() + 0.8, pz, 2, 0.1, 0.1, 0.1, 0.05);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.6f, 0.8f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.8f, 1.4f);

        player.displayClientMessage(Component.literal("§b§l🌀 ¡ATAQUE GIRATORIO! §fImpactados: §e" + targets.size()), true);
    }
}