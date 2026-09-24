package com.example.modrpg.skills.nodes.mobility;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class ImpactJumpSkill extends SkillNode {

    public static final String TAG_GROUND_SLAM = "modrpg_ground_slam_active";

    public ImpactJumpSkill() {
        super(
                SkillRegistry.NODE_IMPACT_JUMP,
                SkillRegistry.BRANCH_MOBILITY,
                Component.literal("Salto con Impacto (Ground Slam)"),
                Component.literal("Te lanza por los cielos. Al colisionar contra el suelo, detona una onda sísmica de 150 de daño en área."),
                NodeType.ACTIVE_ABILITY,
                300 // 15 segundos de recarga
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        // Marca al jugador con la bandera de impacto sísmico
        player.addTag(TAG_GROUND_SLAM);

        // Impulso vertical masivo hacia arriba
        player.setDeltaMovement(new Vec3(0, 1.65, 0));
        player.resetFallDistance();
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.8f);

        player.displayClientMessage(Component.literal("§6☄ ¡SALTO SÍSMICO! §7(Prepárate para el impacto contra el suelo)"), true);
    }
}