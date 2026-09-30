package com.example.modrpg.skills.nodes.ranged;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.SkillEconomy;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import com.example.modrpg.skills.nodes.magic.MinionHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class HomingArrowSkill extends SkillNode {

    public HomingArrowSkill() {
        super(
                SkillRegistry.NODE_HOMING_ARROW,
                SkillRegistry.BRANCH_RANGED,
                Component.literal("Tiro Teledirigido Elevado"),
                Component.literal("Lanza una flecha hacia los cielos que desciende buscando al enemigo más cercano. Si no hay objetivos, suma 10s de penalización."),
                NodeType.ACTIVE_ABILITY,
                200 // 10 segundos base
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        if (player == null || player.level().isClientSide()) return;

        ServerLevel level = (ServerLevel) player.level();
        AABB searchBox = player.getBoundingBox().inflate(25.0);

        // 1. SPRINT 2 FIX: Buscar entidades en un radio de 25 bloques filtrando pasivos, aldeanos y esbirros
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> isValidTarget(player, e)
        );

        // 2. Penalización de 10s si no hay ningún enemigo válido
        if (candidates.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("§c✖ No se detectaron enemigos hostiles. ¡Penalización de +10s de enfriamiento!"),
                    true
            );
            skills.setCooldown(this.getId(), 400); // 20 segundos totales
            SkillEconomy.syncSkills(player);
            return;
        }

        // 3. SPRINT 2 FIX: Seleccionar al enemigo MÁS CERCANO real (algoritmo O(N) sin asignar basura de memoria)
        LivingEntity closestTarget = null;
        double bestDistSq = Double.MAX_VALUE;

        for (LivingEntity candidate : candidates) {
            double distSq = player.distanceToSqr(candidate);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                closestTarget = candidate;
            }
        }

        if (closestTarget == null) return;

        // 4. Calcular trayectoria balística elevada hacia el centro del torso del objetivo
        Vec3 startPos = player.getEyePosition();
        Vec3 targetCenter = closestTarget.getBoundingBox().getCenter();
        Vec3 trajectory = targetCenter.subtract(startPos).normalize();

        Arrow homingArrow = new Arrow(level, player);
        // Ángulo parabólico elevado (+0.45 Y) para simular el descenso del tiro homing
        homingArrow.shoot(trajectory.x, trajectory.y + 0.45, trajectory.z, 2.6f, 0.0f);
        homingArrow.setBaseDamage(homingArrow.getBaseDamage() * 1.8);
        level.addFreshEntity(homingArrow);

        // 5. Efectos sensoriales de disparo supersónico
        level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getEyeY(), player.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.2f, 1.3f);

        player.displayClientMessage(
                Component.literal("§9🎯 ¡FLECHA TELEDIRIGIDA enviada contra: §f" + closestTarget.getName().getString() + "§9!"),
                true
        );

        SkillEconomy.syncSkills(player);
    }

    /**
     * SPRINT 2 FIX: Valida que el objetivo sea un enemigo hostil legítimo y no una mascota, aldeano o esbirro.
     */
    private boolean isValidTarget(ServerPlayer player, LivingEntity entity) {
        if (entity == null || entity == player || !entity.isAlive() || entity.isSpectator()) {
            return false;
        }

        // No atacar a esbirros propios ni aliados
        if (MinionHelper.areAllies(player, entity)) {
            return false;
        }

        // Monstruos hostiles del juego
        if (entity instanceof Enemy) {
            return true;
        }

        // Jugadores rivales en servidores PvP
        if (entity instanceof ServerPlayer otherPlayer) {
            return player.canHarmPlayer(otherPlayer) && !player.isAlliedTo(otherPlayer);
        }

        return false;
    }
}