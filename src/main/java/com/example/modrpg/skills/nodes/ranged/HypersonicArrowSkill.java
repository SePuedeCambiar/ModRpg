package com.example.modrpg.skills.nodes.ranged;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class HypersonicArrowSkill extends SkillNode {

    public static final String HYPERSONIC_TAG = "modrpg_hypersonic_arrow";

    public HypersonicArrowSkill() {
        super(
                SkillRegistry.NODE_HYPERSONIC,
                SkillRegistry.BRANCH_RANGED,
                Component.literal("Tiro Hipersónico"),
                Component.literal("Disparar mientras estás agachado acelera la flecha a velocidad supersónica y perfora 5 enemigos."),
                NodeType.ACTIVE_ABILITY,
                0
        );
    }

    @Override
    public void onArrowShoot(ServerPlayer player, EntityJoinLevelEvent event, AbstractArrow arrow, PlayerSkills skills) {
        if (!player.isShiftKeyDown()) return;

        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1.5));
        arrow.setPierceLevel((byte) 5);
        arrow.setNoGravity(true);
        arrow.addTag(HYPERSONIC_TAG);

        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.9f, 1.8f);
        level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getEyeY(), player.getZ(), 1, 0, 0, 0, 0);

        player.displayClientMessage(Component.literal("§9§l⚡ ¡TIRO HIPERSÓNICO! §f(Perforación V activada)"), true);
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow && arrow.getTags().contains(HYPERSONIC_TAG)) {
            ServerLevel level = (ServerLevel) player.level();
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, event.getEntity().getX(), event.getEntity().getY() + 1.0, event.getEntity().getZ(), 30, 0.4, 0.4, 0.4, 0.15);
            level.playSound(null, event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 2.0f);
        }
    }
}