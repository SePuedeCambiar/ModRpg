package com.example.modrpg.skills.nodes.hybrid;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

public class ArrowPropulsionSkill extends SkillNode {

    public ArrowPropulsionSkill() {
        super(
                SkillRegistry.NODE_ARROW_PROPULSION,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Impulso Acrobático de Flecha"),
                Component.literal("Disparar una flecha al suelo bajo tus pies te propulsa por los aires con caída lenta temporal."),
                NodeType.HYBRID_SYNERGY,
                60 // 3 segundos de cooldown
        );
    }

    @Override
    public void onArrowShoot(ServerPlayer player, EntityJoinLevelEvent event, AbstractArrow arrow, PlayerSkills skills) {
        // Si el jugador dispara hacia abajo (ángulo de inclinación > 55 grados)
        if (player.getXRot() > 55.0f && !skills.hasCooldown(this.getId())) {
            skills.setCooldown(this.getId(), 60);

            // Propulsión hacia arriba y hacia adelante
            Vec3 look = player.getLookAngle();
            player.setDeltaMovement(new Vec3(-look.x * 0.8, 1.35, -look.z * 0.8));
            player.hurtMarked = true;

            // Inmunidad a caída breve con caída lenta
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));

            ServerLevel level = (ServerLevel) player.level();
            level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
            level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY(), player.getZ(), 30, 0.4, 0.2, 0.4, 0.1);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.2f, 1.0f);

            player.displayClientMessage(Component.literal("§b💨 ¡PROPULSIÓN ACROBÁTICA!"), true);
        }
    }
}
