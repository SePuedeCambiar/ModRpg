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

import java.util.List;

public class HeavyTornadoSkill extends SkillNode {

    public HeavyTornadoSkill() {
        super(
                SkillRegistry.NODE_HEAVY_TORNADO,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Torbellino Ultrapesado"),
                Component.literal("Un ataque giratorio gigantesco (+50% de daño y radio ampliado) que levanta a los enemigos en el aire."),
                NodeType.ACTIVE_ABILITY,
                160 // 8 segundos de recarga
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        ServerLevel level = (ServerLevel) player.level();
        double radius = 5.5; // Radio ampliado

        player.swing(InteractionHand.MAIN_HAND, true);

        AABB box = player.getBoundingBox().inflate(radius, 2.0, radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                e -> e != player && e.isAlive() && !e.isAlliedTo(player)
        );

        float baseDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        // +50% de daño garantizado por la mejora de la habilidad
        float totalDamage = (baseDamage * 1.50f) * (1.0f + (skills.getBranchLevel(SkillRegistry.BRANCH_MELEE) * 0.02f));

        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().playerAttack(player), totalDamage);
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            // Empuje radial + elevación vertical ("ataque ultrapesado")
            target.knockback(1.0, -dx, -dz);
            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.45, 0));
        }

        // Anillo de partículas masivo
        int points = 24;
        for (int i = 0; i < points; i++) {
            double angle = i * (2 * Math.PI / points);
            double px = player.getX() + (Math.cos(angle) * 3.2);
            double pz = player.getZ() + (Math.sin(angle) * 3.2);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, px, player.getY() + 0.8, pz, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, player.getY() + 0.2, pz, 1, 0, 0.05, 0, 0.02);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.5f, 0.7f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 1.8f);

        player.displayClientMessage(Component.literal("§6§l🌪 ¡TORBELLINO ULTRAPESADO! §fEnemigos barridos: §e" + targets.size()), true);
    }
}