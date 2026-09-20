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

import java.util.List;

public class MegacutSkill extends SkillNode {

    public MegacutSkill() {
        super(
                SkillRegistry.NODE_MEGACUT,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Semidefinitiva: Megacorte"),
                Component.literal("Lanza una gigantesca onda cortante frontal de 12 bloques que atraviesa enemigos infligiendo daño masivo."),
                NodeType.ACTIVE_ABILITY,
                400 // 20 segundos
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        ServerLevel level = (ServerLevel) player.level();
        player.swing(InteractionHand.MAIN_HAND, true);

        Vec3 look = player.getLookAngle();
        Vec3 start = player.getEyePosition();
        float baseDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 3.0f;

        // 1. Efectos visuales a lo largo de la trayectoria (12 bloques)
        for (int i = 1; i <= 12; i++) {
            Vec3 point = start.add(look.scale(i));
            level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, point.x, point.y, point.z, 1, 0.2, 0.2, 0.2, 0.0);
        }

        // 2. OPTIMIZACIÓN: 1 sola consulta de AABB para todo el sector en lugar de 12
        Vec3 end = start.add(look.scale(12.0));
        AABB sweepBox = new AABB(start, end).inflate(1.5, 1.0, 1.5);

        List<LivingEntity> potentialTargets = level.getEntitiesOfClass(
                LivingEntity.class,
                sweepBox,
                e -> e != player && e.isAlive() && !e.isAlliedTo(player)
        );

        int hitCount = 0;
        for (LivingEntity enemy : potentialTargets) {
            // Verificar si el enemigo está alineado con la trayectoria del corte
            Vec3 toEnemy = enemy.position().subtract(start);
            double projection = toEnemy.dot(look);

            if (projection > 0 && projection <= 12.5) {
                Vec3 closestPoint = start.add(look.scale(projection));
                if (closestPoint.distanceToSqr(enemy.position()) <= 2.25) { // Radio de 1.5 bloques
                    enemy.hurt(player.damageSources().playerAttack(player), baseDamage);
                    enemy.knockback(1.2, -look.x, -look.z);
                    hitCount++;
                }
            }
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 1.2f);
        player.displayClientMessage(Component.literal("§9§l⚡ ¡MEGACORTE FRONTAL! §fImpactados: §e" + hitCount), true);
    }
}