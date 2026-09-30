package com.example.modrpg.skills.nodes.magic;

import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketSyncMana;
import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.SkillEconomy;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import com.example.modrpg.skills.magic.modular.ElementalReactionManager;
import com.example.modrpg.skills.magic.modular.SpellElement;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LightningChainSkill extends SkillNode {

    public LightningChainSkill() {
        super(
                SkillRegistry.NODE_LIGHTNING_CHAIN,
                SkillRegistry.BRANCH_MAGIC,
                Component.literal("Chispa Eléctrica Encadenada"),
                Component.literal("Descarga una corriente eléctrica que electrocuta y salta entre 3 enemigos cercanos."),
                NodeType.ACTIVE_ABILITY,
                140 // 7 segundos base
        );
        this.setManaCost(30.0f);
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        if (player == null || player.level().isClientSide()) return;

        ServerLevel level = (ServerLevel) player.level();
        AABB searchBox = player.getBoundingBox().inflate(9.0);

        // 1. Filtrar únicamente entidades hostiles legítimas en un radio inicial de 9 bloques
        List<LivingEntity> potentialTargets = level.getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> isValidTarget(player, e)
        );

        // 2. Reembolso defensivo si no hay objetivos para encadenar
        if (potentialTargets.isEmpty()) {
            player.displayClientMessage(Component.literal("§c✖ No hay enemigos cercanos para encadenar."), true);
            skills.restoreMana(this.getManaCost());
            skills.setCooldown(this.getId(), 20); // 1 segundo de cooldown penalizado en vez de 7s
            ModMessages.sendToPlayer(new PacketSyncMana(skills.getCurrentMana(), skills.getMaxMana()), player);
            SkillEconomy.syncSkills(player);
            return;
        }

        // 3. Algoritmo de arco eléctrico encadenado (Jugador -> Mob 1 -> Mob 2 -> Mob 3)
        List<LivingEntity> chain = new ArrayList<>();
        Set<LivingEntity> hitSet = new HashSet<>();
        LivingEntity currentSource = player;

        int magicLevel = skills.getBranchLevel(SkillRegistry.BRANCH_MAGIC);
        float damage = 10.0f + (magicLevel * 0.35f);

        for (int hop = 0; hop < 3; hop++) {
            LivingEntity nextTarget = findClosestTarget(currentSource, potentialTargets, hitSet, hop == 0 ? 9.0 : 6.5);
            if (nextTarget == null) break;

            chain.add(nextTarget);
            hitSet.add(nextTarget);
            currentSource = nextTarget;
        }

        if (chain.isEmpty()) return;

        // 4. Ejecución del rayo, arcos visuales y daño atribuido
        Vec3 arcStart = player.getEyePosition();

        for (LivingEntity victim : chain) {
            Vec3 targetCenter = victim.getBoundingBox().getCenter();

            // Dibujar arco visual entre los nodos de salto
            drawLightningArc(level, arcStart, targetCenter);

            // Daño indirecto de magia correctamente atribuido al jugador
            victim.hurt(player.damageSources().indirectMagic(player, player), damage);

            // Integración elemental para activar combos (ej. Superconductor si tiene hielo)
            ElementalReactionManager.handleElementalHit(player, victim, SpellElement.LIGHTNING, damage);

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, targetCenter.x, targetCenter.y, targetCenter.z, 20, 0.3, 0.4, 0.3, 0.15);
            level.playSound(null, targetCenter.x, targetCenter.y, targetCenter.z,
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.7f, 1.8f);

            // El siguiente arco sale desde el objetivo actual
            arcStart = targetCenter;
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 2.0f);

        player.displayClientMessage(
                Component.literal("§b⚡ ¡RAYO ENCADENADO! §fElectrocutados: §e" + chain.size()),
                true
        );

        SkillEconomy.syncSkills(player);
    }

    /**
     * Dibuja una línea de chispas eléctricas entre dos puntos del espacio.
     */
    private void drawLightningArc(ServerLevel level, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double distance = delta.length();
        int points = Math.max(3, (int) (distance * 3.5));

        for (int i = 0; i <= points; i++) {
            double progress = (double) i / points;
            Vec3 point = start.add(delta.scale(progress));

            // Variación aleatoria para simular la forma quebrada de un relámpago
            double jx = (Math.random() - 0.5) * 0.25;
            double jy = (Math.random() - 0.5) * 0.25;
            double jz = (Math.random() - 0.5) * 0.25;

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x + jx, point.y + jy, point.z + jz, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Encuentra el objetivo disponible más cercano al punto de origen actual dentro del rango máximo.
     */
    private LivingEntity findClosestTarget(LivingEntity source, List<LivingEntity> pool, Set<LivingEntity> alreadyHit, double maxDistance) {
        LivingEntity closest = null;
        double bestDistSq = maxDistance * maxDistance;

        for (LivingEntity candidate : pool) {
            if (alreadyHit.contains(candidate)) continue;

            double distSq = source.distanceToSqr(candidate);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                closest = candidate;
            }
        }

        return closest;
    }

    /**
     * Valida que la entidad sea un enemigo hostil legítimo y no un aliado, mascota o aldeano.
     */
    private boolean isValidTarget(ServerPlayer player, LivingEntity entity) {
        if (entity == null || entity == player || !entity.isAlive() || entity.isSpectator()) {
            return false;
        }

        if (MinionHelper.areAllies(player, entity)) {
            return false;
        }

        if (entity instanceof Enemy) {
            return true;
        }

        if (entity instanceof ServerPlayer otherPlayer) {
            return player.canHarmPlayer(otherPlayer) && !player.isAlliedTo(otherPlayer);
        }

        return false;
    }
}