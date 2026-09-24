package com.example.modrpg.skills.nodes.magic;

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

public class HealingAuraSkill extends SkillNode {

    public HealingAuraSkill() {
        super(
                SkillRegistry.NODE_HEALING_AURA,
                SkillRegistry.BRANCH_MAGIC,
                Component.literal("Aura de Sanación"),
                Component.literal("Restaura salud al instante, limpia efectos negativos y otorga Regeneración temporal. (Coste: 35 Maná)"),
                NodeType.ACTIVE_ABILITY,
                240 // 12 segundos de recarga (240 ticks)
        );
        // Coste de Maná integrado para el Sprint 1
        this.setManaCost(35.0f);
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        int magicLvl = skills.getBranchLevel(SkillRegistry.BRANCH_MAGIC);
        float healAmount = 6.0f + (magicLvl * 0.2f); // 3 corazones base + escalado de magia

        player.heal(healAmount);
        player.removeEffect(MobEffects.POISON);
        player.removeEffect(MobEffects.WITHER);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);

        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, false));

        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.2, player.getZ(), 8, 0.4, 0.4, 0.4, 0.1);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 0.8, player.getZ(), 25, 0.5, 0.5, 0.5, 0.15);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f);

        player.displayClientMessage(Component.literal("§a✨ ¡AURA DE SANACIÓN! §fSalud restaurada: §2+" + String.format("%.1f", healAmount)), true);
    }
}