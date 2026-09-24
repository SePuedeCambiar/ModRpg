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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public class FlurryOfStrikesSkill extends SkillNode {

    public FlurryOfStrikesSkill() {
        super(
                SkillRegistry.NODE_FLURRY_OF_STRIKES,
                SkillRegistry.BRANCH_MOBILITY,
                Component.literal("Lluvia de Golpes (Salto Ronin)"),
                Component.literal("Otorga Velocidad III durante 10 segundos y un potente salto de apoyo hacia adelante para interceptar objetivos."),
                NodeType.ACTIVE_ABILITY,
                240 // 12 segundos de recarga
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        // 1. Velocidad aumentada por 10 segundos (200 ticks)
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 2, false, false));

        // 2. Salto acrobático de apoyo en dirección a donde mira
        Vec3 look = player.getLookAngle();
        Vec3 leap = new Vec3(look.x * 1.3, 1.10, look.z * 1.3);
        player.setDeltaMovement(leap);
        player.resetFallDistance();
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 0.5, player.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY(), player.getZ(), 12, 0.3, 0.1, 0.3, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 1.2f, 1.4f);

        player.displayClientMessage(Component.literal("§b💨 ¡SALTO RONIN ACTIVADO! §e(Velocidad III por 10s)"), true);
    }
}