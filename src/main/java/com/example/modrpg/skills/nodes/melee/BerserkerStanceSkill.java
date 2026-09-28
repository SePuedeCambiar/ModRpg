package com.example.modrpg.skills.nodes.melee;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class BerserkerStanceSkill extends SkillNode {

    public BerserkerStanceSkill() {
        super(
                SkillRegistry.NODE_BERSERKER_STANCE,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Postura Berserker"),
                Component.literal("Postura conmutable: Infunde tus ataques con furia (+30% Daño CaC), pero recibes +15% de daño. Drena 4.5 de Maná por segundo."),
                NodeType.PASSIVE_TOGGLE,
                0
        );
        // Coste superior a la regeneración natural (2.0/s) para obligar a una gestión de maná real
        this.setSustainManaCost(4.5f);
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        if (!skills.isToggleActive(this.getId())) return;

        // 1. Al atacar: +30% de daño infligido
        if (event.getSource().getDirectEntity() == player) {
            event.setAmount(event.getAmount() * 1.30f);

            ServerLevel level = (ServerLevel) player.level();
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, event.getEntity().getX(), event.getEntity().getY() + 1.0, event.getEntity().getZ(), 4, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8f, 1.6f);
        }

        // 2. Al recibir golpes: +15% de daño recibido
        if (event.getEntity() == player) {
            event.setAmount(event.getAmount() * 1.15f);
        }
    }
}