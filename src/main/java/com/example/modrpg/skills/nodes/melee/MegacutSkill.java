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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MegacutSkill extends SkillNode {

    public MegacutSkill() {
        super(
                SkillRegistry.NODE_MEGACUT,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Semidefinitiva: Megacorte"),
                Component.literal("Lanza una gigantesca onda cortante frontal de 12 bloques que atraviesa enemigos infligiendo daño masivo."),
                NodeType.ACTIVE_ABILITY,
                400 // 20 segundos de recarga
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        ServerLevel level = (ServerLevel) player.level();
        player.swing(InteractionHand.MAIN_HAND, true);

        Vec3 look = player.getLookAngle();
        Vec3 start = player.getEyePosition();
        float baseDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 3.0f;

        Set<LivingEntity> hitEntities = new HashSet<>();

        // Proyecta la onda en 12 pasos hacia el frente
        for (int i = 1; i <= 12; i++) {
            Vec3 point = start.add(look.scale(i));

            // Partículas visuales de la onda en expansión
            level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, point.x, point.y, point.z, 2, 0.3, 0.3, 0.3, 0.0);

            AABB box = new AABB(point.x - 1.5, point.y - 1.0, point.z - 1.5, point.x + 1.5, point.y + 1.0, point.z + 1.5);
            List<LivingEntity> enemies = level.getEntitiesOfClass(
                    LivingEntity.class,
                    box,
                    e -> e != player && e.isAlive() && !e.isAlliedTo(player) && !hitEntities.contains(e)
            );

            for (LivingEntity enemy : enemies) {
                hitEntities.add(enemy);
                enemy.hurt(player.damageSources().playerAttack(player), baseDamage);
                enemy.knockback(1.2, -look.x, -look.z);
            }
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 1.2f);
        player.displayClientMessage(Component.literal("§9§l⚡ ¡MEGACORTE FRONTAL! §fImpactados: §e" + hitEntities.size()), true);
    }
}