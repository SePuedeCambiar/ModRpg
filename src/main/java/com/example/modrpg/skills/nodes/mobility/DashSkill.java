package com.example.modrpg.skills.nodes.mobility;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.SkillEconomy;
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

public class DashSkill extends SkillNode {

    public DashSkill() {
        super(
                SkillRegistry.NODE_DASH,
                SkillRegistry.BRANCH_MOBILITY,
                Component.literal("Embestida Evasiva (Dash)"),
                Component.literal("Un veloz impulso acrobático que te otorga invulnerabilidad total a todo daño durante la esquiva."),
                NodeType.ACTIVE_ABILITY,
                60 // 3 segundos de recarga
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        // 1. Calcular vector horizontal puro (evita que apuntar al piso frene el impulso)
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0, look.z).normalize();
        if (horizontal.lengthSqr() < 1e-4) {
            horizontal = Vec3.directionFromRotation(0, player.getYRot()).normalize();
        }

        // 2. Impulso enérgico hacia el frente
        Vec3 dashVelocity = new Vec3(horizontal.x * 1.5, 0.15, horizontal.z * 1.5);
        player.setDeltaMovement(dashVelocity);
        player.resetFallDistance();
        player.hurtMarked = true;

        // Forzar actualización inmediata en el cliente (elimina el rubberbanding/tirones)
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        // 3. Activar i-frames reales (15 ticks = 0.75 segundos de inmunidad absoluta)
        skills.setDashIFrames(15);

        // 4. Efectos de partículas de estela y sonido de aire cortado
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.3, player.getZ(), 18, 0.3, 0.2, 0.3, 0.04);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.1, player.getZ(), 12, 0.2, 0.1, 0.2, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 1.8f);

        player.displayClientMessage(Component.literal("§b💨 ¡EMBESTIDA EVASIVA!"), true);

        // Sincronizar cooldown de 3 segundos al HUD
        SkillEconomy.syncSkills(player);
    }
}