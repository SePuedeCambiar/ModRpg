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

public class AirJumpSkill extends SkillNode {

    public static final String AIR_JUMP_SAFE_TAG = "modrpg_air_jump_safe";

    public AirJumpSkill() {
        super(
                SkillRegistry.NODE_AIR_JUMP,
                SkillRegistry.BRANCH_MOBILITY,
                Component.literal("Salto de Viento"),
                Component.literal("Canaliza una corriente aérea que te impulsa verticalmente sin recibir daño de caída al aterrizar."),
                NodeType.ACTIVE_ABILITY,
                80 // 4 segundos de recarga
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        // 1. Resetea la distancia de caída acumulada antes de saltar
        player.resetFallDistance();

        // 2. Otorga inmunidad al impacto de este salto
        player.addTag(AIR_JUMP_SAFE_TAG);

        // 3. Aplica impulso conservando y potenciando la inercia horizontal que ya lleve el jugador
        Vec3 currentVel = player.getDeltaMovement();
        Vec3 newVel = new Vec3(currentVel.x * 1.15, 0.95, currentVel.z * 1.15);
        player.setDeltaMovement(newVel);
        player.hurtMarked = true;

        // Enviar paquete directo de movimiento para respuesta instantánea (cero tirones)
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        // 4. Efectos visuales y sonoros de ráfaga
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 16, 0.3, 0.1, 0.3, 0.08);
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY(), player.getZ(), 8, 0.2, 0.1, 0.2, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 1.2f, 1.3f);

        player.displayClientMessage(Component.literal("§a☁ ¡SALTO DE VIENTO!"), true);

        // Sincronizar cooldown al HUD de inmediato
        SkillEconomy.syncSkills(player);
    }
}