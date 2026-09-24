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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class LegTripSkill extends SkillNode {

    public LegTripSkill() {
        super(
                SkillRegistry.NODE_LEG_TRIP,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Golpe Bajo (Tropezón)"),
                Component.literal("Barre las piernas de los enemigos al frente infligiendo daño y derribándolos con lentitud extrema."),
                NodeType.ACTIVE_ABILITY,
                160 // 8 segundos
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        ServerLevel level = (ServerLevel) player.level();
        player.swing(InteractionHand.MAIN_HAND, true);

        AABB box = player.getBoundingBox().inflate(3.5, 1.0, 3.5);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isAlliedTo(player)
        );

        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);

        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().playerAttack(player), damage);
            // Derriba y frena en seco
            target.setDeltaMovement(target.getDeltaMovement().multiply(0.2, 0.0, 0.2));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3, false, false));
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.2, target.getZ(), 10, 0.3, 0.1, 0.3, 0.05);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2f, 0.6f);
        player.displayClientMessage(Component.literal("§c🦵 ¡GOLPE BAJO ASESINADO! §fEnemigos tropezados: §e" + targets.size()), true);
    }
}