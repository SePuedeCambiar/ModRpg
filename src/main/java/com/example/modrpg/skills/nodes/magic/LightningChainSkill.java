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

import java.util.List;

public class LightningChainSkill extends SkillNode {

    public LightningChainSkill() {
        super(
                SkillRegistry.NODE_LIGHTNING_CHAIN,
                SkillRegistry.BRANCH_MAGIC,
                Component.literal("Chispa Eléctrica Encadenada"),
                Component.literal("Descarga una corriente eléctrica que electrocuta y salta entre 3 enemigos cercanos."),
                NodeType.ACTIVE_ABILITY,
                140 // 7 segundos
        );
        this.setManaCost(30.0f);
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        ServerLevel level = (ServerLevel) player.level();
        AABB searchBox = player.getBoundingBox().inflate(8.0);
        List<LivingEntity> enemies = level.getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> e != player && e.isAlive() && !e.isAlliedTo(player)
        );

        if (enemies.isEmpty()) {
            player.displayClientMessage(Component.literal("§c✖ No hay objetivos cercanos para encadenar."), true);
            return;
        }

        int chained = 0;
        int magicLevel = skills.getBranchLevel(SkillRegistry.BRANCH_MAGIC);
        float damage = 10.0f + (magicLevel * 0.3f);

        for (LivingEntity enemy : enemies) {
            if (chained >= 3) break;
            enemy.hurt(player.damageSources().magic(), damage);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, enemy.getX(), enemy.getY() + 1.0, enemy.getZ(), 25, 0.3, 0.4, 0.3, 0.15);
            chained++;
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 2.0f);
        player.displayClientMessage(Component.literal("§b⚡ ¡RAYO ENCADENADO! §fElectrocutados: §e" + chained), true);
    }
}