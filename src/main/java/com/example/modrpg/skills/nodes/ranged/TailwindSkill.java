package com.example.modrpg.skills.nodes.ranged;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

public class TailwindSkill extends SkillNode {

    public TailwindSkill() {
        super(
                SkillRegistry.NODE_TAILWIND,
                SkillRegistry.BRANCH_RANGED,
                Component.literal("Viento a Favor"),
                Component.literal("Las flechas disparadas viajan un +80% más rápido y con mayor precisión."),
                NodeType.PASSIVE_STAT,
                0
        );
    }

    @Override
    public void onArrowShoot(ServerPlayer player, EntityJoinLevelEvent event, AbstractArrow arrow, PlayerSkills skills) {
        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1.8));

        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.CLOUD, arrow.getX(), arrow.getY(), arrow.getZ(), 10, 0.2, 0.2, 0.2, 0.08);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0, 0, 0, 0);

        player.displayClientMessage(Component.literal("§b💨 ¡Viento a Favor activado! (+80% Velocidad)"), true);
    }
}