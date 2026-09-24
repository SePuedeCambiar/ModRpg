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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class BeeSwarmSkill extends SkillNode {

    public BeeSwarmSkill() {
        super(
                SkillRegistry.NODE_BEE_SWARM,
                SkillRegistry.BRANCH_MAGIC,
                Component.literal("Invocación: Enjambre Hostil"),
                Component.literal("Fija la mirada en un enemigo para invocar un enjambre de 4 abejas enfurecidas que lo asedian durante 8 segundos."),
                NodeType.ACTIVE_ABILITY,
                240 // 12 segundos de recarga
        );
        this.setManaCost(35.0f);
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        Vec3 eyePos = player.getEyePosition();

        // Buscar al enemigo vivo más alineado con el centro de la mira del jugador (hasta 16 bloques)
        AABB searchBox = player.getBoundingBox().inflate(16.0);
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> e != player && e.isAlive() && !e.isSpectator() && !MinionHelper.areAllies(player, e)
        );

        LivingEntity bestTarget = null;
        double bestAlignment = 0.70; // Requiere que esté al menos en el campo frontal

        for (LivingEntity candidate : candidates) {
            Vec3 toCandidate = candidate.getEyePosition().subtract(eyePos).normalize();
            double dot = look.dot(toCandidate);
            if (dot > bestAlignment) {
                bestAlignment = dot;
                bestTarget = candidate;
            }
        }

        if (bestTarget == null) {
            player.displayClientMessage(Component.literal("§c✖ Debes apuntar hacia un enemigo para enviar las abejas."), true);
            skills.setCooldown(this.getId(), 20); // 1s de penalización
            skills.restoreMana(35.0f); // Reembolsa el maná
            return;
        }

        // Variable final para permitir su uso seguro dentro de la lambda
        final LivingEntity target = bestTarget;
        final int beeLifespan = 160; // 8 segundos exactos

        for (int i = 0; i < 4; i++) {
            Vec3 spawnPos = target.position().add((Math.random() - 0.5) * 2.0, 1.2, (Math.random() - 0.5) * 2.0);
            MinionHelper.spawnMinion(player, EntityType.BEE, spawnPos, beeLifespan, (Bee bee) -> {
                bee.setTarget(target);
                bee.setRemainingPersistentAngerTime(beeLifespan);
            });
        }

        level.sendParticles(ParticleTypes.FALLING_HONEY, target.getX(), target.getY() + 1.0, target.getZ(), 25, 0.4, 0.4, 0.4, 0.1);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEE_POLLINATE, SoundSource.PLAYERS, 1.5f, 1.5f);
        player.displayClientMessage(Component.literal("§e🐝 ¡ENJAMBRE ENVIADO contra: §f" + target.getName().getString() + "§e!"), true);
    }
}