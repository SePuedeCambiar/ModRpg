package com.example.modrpg.skills.nodes.magic;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class NecroticDrainSkill extends SkillNode {

    public NecroticDrainSkill() {
        super(
                SkillRegistry.NODE_NECROTIC_DRAIN,
                SkillRegistry.BRANCH_MAGIC,
                Component.literal("Drenaje Necrótico"),
                Component.literal("Tus ataques drenan la esencia vital del enemigo, curándote un porcentaje del daño causado."),
                NodeType.PASSIVE_STAT,
                0
        );
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        if (event.getSource().getEntity() == player) {
            float heal = Math.max(1.0f, event.getAmount() * 0.15f);
            player.heal(heal);

            ServerLevel level = (ServerLevel) player.level();
            level.sendParticles(ParticleTypes.SOUL, event.getEntity().getX(), event.getEntity().getY() + 1.0, event.getEntity().getZ(), 5, 0.2, 0.3, 0.2, 0.05);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.6f, 1.8f);

            player.displayClientMessage(Component.literal("§5💀 Drenaje vital: §a+" + String.format("%.1f", heal)), true);
        }
    }
}
