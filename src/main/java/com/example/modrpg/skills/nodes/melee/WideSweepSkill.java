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

public class WideSweepSkill extends SkillNode {

    public WideSweepSkill() {
        super(
                SkillRegistry.NODE_WIDE_SWEEP,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Barrido Ciclónico"),
                Component.literal("Un golpe horizontal de 180° que corta a todos los enemigos frente a ti sin requerir recarga de arma."),
                NodeType.ACTIVE_ABILITY,
                100 // 5 segundos
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        ServerLevel level = (ServerLevel) player.level();
        player.swing(InteractionHand.MAIN_HAND, true);

        AABB sweepBox = player.getBoundingBox().inflate(4.0, 1.0, 4.0);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class, sweepBox,
                e -> e != player && e.isAlive() && !e.isAlliedTo(player)
        );

        float baseDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.25f;

        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().playerAttack(player), baseDamage);
            target.knockback(0.8, -(target.getX() - player.getX()), -(target.getZ() - player.getZ()));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 0.8, target.getZ(), 2, 0.1, 0.1, 0.1, 0);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 1.1f);
        player.displayClientMessage(Component.literal("§6⚔ ¡BARRIDO CICLÓNICO! §fObjetivos cortados: §e" + targets.size()), true);
    }
}