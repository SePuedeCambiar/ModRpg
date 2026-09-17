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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class HybridHunterSkill extends SkillNode {

    public static final String HUNTER_MARK_TAG = "modrpg_hunter_mark";

    public HybridHunterSkill() {
        super(
                SkillRegistry.NODE_HYBRID_HUNTER,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Cazador Híbrido"),
                Component.literal("Las flechas marcan al enemigo. Rematarlo cuerpo a cuerpo detona la marca infligiendo +150% de daño de vacío."),
                NodeType.HYBRID_SYNERGY,
                0
        );
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        LivingEntity target = event.getEntity();
        if (target == null) return;
        ServerLevel level = (ServerLevel) player.level();

        // 1. Marcar con flecha
        if (event.getSource().getDirectEntity() instanceof AbstractArrow) {
            target.addTag(HUNTER_MARK_TAG);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false));
            level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.4, 0.4, 0.4, 0.2);
            player.displayClientMessage(Component.literal("§d🎯 ¡Enemigo Marcado! Remátalo cuerpo a cuerpo para combo."), true);
        }

        // 2. Detonar con golpe cuerpo a cuerpo directo
        else if (event.getSource().getDirectEntity() == player && target.getTags().contains(HUNTER_MARK_TAG)) {
            target.removeTag(HUNTER_MARK_TAG);
            target.removeEffect(MobEffects.GLOWING);

            event.setAmount(event.getAmount() * 2.5f);
            level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0, target.getZ(), 40, 0.5, 0.5, 0.5, 0.15);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7f, 1.6f);

            player.displayClientMessage(Component.literal("§d§l⚡ ¡COMBO HÍBRIDO EJECUTADO! (+150% Daño de Vacío)"), true);
        }
    }
}