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

public class DashSkill extends SkillNode {

    public DashSkill() {
        super(
                SkillRegistry.NODE_DASH,
                SkillRegistry.BRANCH_MOBILITY,
                Component.literal("Embestida Evasiva (Dash)"),
                Component.literal("Un veloz impulso acrobático que te otorga invulnerabilidad temporal a daños."),
                NodeType.ACTIVE_ABILITY,
                60 // 3 segundos de recarga
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        Vec3 look = player.getLookAngle();
        // Impulso hacia adelante conservando un poco de salto
        player.setDeltaMovement(new Vec3(look.x * 1.6, 0.25, look.z * 1.6));
        player.hurtMarked = true;

        // 1 segundo completo de inmunidad (20 ticks de invulnerabilidad / i-frames)
        player.invulnerableTime = 20;

        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.5, player.getZ(), 15, 0.3, 0.2, 0.3, 0.05);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 10, 0.2, 0.2, 0.2, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.8f);

        player.displayClientMessage(Component.literal("§b💨 ¡EMBESTIDA EVASIVA!"), true);
    }
}
