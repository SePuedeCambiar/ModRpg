package com.example.modrpg.skills.nodes.mobility;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class AirJumpSkill extends SkillNode {

    public AirJumpSkill() {
        super(
                SkillRegistry.NODE_AIR_JUMP,
                SkillRegistry.BRANCH_MOBILITY,
                Component.literal("Salto de Viento"),
                Component.literal("Canaliza una corriente aérea que te impulsa verticalmente a gran altura."),
                NodeType.ACTIVE_ABILITY,
                80 // 4 segundos de recarga
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        Vec3 currentVel = player.getDeltaMovement();
        player.setDeltaMovement(new Vec3(currentVel.x, 0.95, currentVel.z));
        player.hurtMarked = true;

        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 12, 0.3, 0.1, 0.3, 0.08);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 1.0f, 1.2f);

        player.displayClientMessage(Component.literal("§a☁ ¡SALTO DE VIENTO!"), true);
    }
}
