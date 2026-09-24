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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.List;

public class CounterAttackSkill extends SkillNode {

    public static final String COUNTER_ACTIVE_TAG = "modrpg_counter_active";

    public CounterAttackSkill() {
        super(
                SkillRegistry.NODE_COUNTER_ATTACK,
                SkillRegistry.BRANCH_MAGIC,
                Component.literal("Contraataque Mágico"),
                Component.literal("Entra en postura defensiva durante 1.5s. Si recibes un impacto, lo anulas y desatas un contraataque arcano sobre 2 enemigos."),
                NodeType.ACTIVE_ABILITY,
                240 // 12 segundos
        );
        this.setManaCost(25.0f);
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        player.addTag(COUNTER_ACTIVE_TAG);

        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.5f);

        player.displayClientMessage(Component.literal("§d🛡 ¡POSTURA DE CONTRAATAQUE ACTIVADA! (1.5 segundos)"), true);
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        if (event.getEntity() == player && player.getTags().contains(COUNTER_ACTIVE_TAG)) {
            player.removeTag(COUNTER_ACTIVE_TAG);
            event.setCanceled(true); // Anula el daño por completo

            ServerLevel level = (ServerLevel) player.level();
            AABB zone = player.getBoundingBox().inflate(6.0);
            List<LivingEntity> enemies = level.getEntitiesOfClass(
                    LivingEntity.class, zone,
                    e -> e != player && e.isAlive() && !e.isAlliedTo(player)
            );

            // Afecta hasta a 2 enemigos cercanos como pide el documento
            int count = 0;
            for (LivingEntity enemy : enemies) {
                if (count >= 2) break;
                enemy.hurt(player.damageSources().magic(), 18.0f);
                level.sendParticles(ParticleTypes.SONIC_BOOM, enemy.getX(), enemy.getY() + 1.0, enemy.getZ(), 1, 0, 0, 0, 0);
                count++;
            }

            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 1.8f);
            player.displayClientMessage(Component.literal("§d§l⚡ ¡CONTRAATAQUE PERFECTO! §fEnemigos repelidos: §e" + count), true);
        }
    }
}