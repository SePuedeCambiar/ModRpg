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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.List;

public class CombinedUltimateSkill extends SkillNode {

    public CombinedUltimateSkill() {
        super(
                SkillRegistry.NODE_COMBINED_ULTIMATE,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Definitiva Combinada: Lluvia de Espadas del Vacío"),
                Component.literal("El Ultracorte Final libera además una tormenta de espadas etéreas que caen del cielo bombardeando la zona."),
                NodeType.ULTIMATE,
                0
        );
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        // Se activa en sinergia cuando el Ultracorte impacta a un objetivo
        if (event.getSource().getDirectEntity() == player && skills.isNodeUnlocked(SkillRegistry.NODE_ULTRACUT)) {
            LivingEntity target = event.getEntity();
            ServerLevel level = (ServerLevel) player.level();

            // Bombardeo de partículas celestiales
            for (int i = 0; i < 20; i++) {
                double offsetX = (Math.random() - 0.5) * 8.0;
                double offsetZ = (Math.random() - 0.5) * 8.0;
                level.sendParticles(ParticleTypes.SONIC_BOOM, target.getX() + offsetX, target.getY() + 4.0, target.getZ() + offsetZ, 1, 0, -1.0, 0, 0);
            }

            AABB zone = target.getBoundingBox().inflate(7.0);
            List<LivingEntity> enemies = level.getEntitiesOfClass(
                    LivingEntity.class,
                    zone,
                    e -> e != player && e.isAlive() && !e.isAlliedTo(player)
            );

            float splashDamage = event.getAmount() * 0.40f;
            for (LivingEntity e : enemies) {
                e.hurt(player.damageSources().magic(), splashDamage);
            }

            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.4f, 1.4f);
            player.displayClientMessage(Component.literal("§d§l⚡ ¡LLUVIA DE ESPADAS DEL VACÍO! §fEnemigos alcanzados: §e" + enemies.size()), true);
        }
    }
}
