package com.example.modrpg.skills.nodes.ranged;

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
        ServerLevel level = (ServerLevel) player.level();
        AABB searchBox = player.getBoundingBox().inflate(25.0);

        List<LivingEntity> enemies = level.getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> e != player && e.isAlive() && !e.isAlliedTo(player)
        );

        // Si no hay objetivos, penalización de 10 segundos extra (200 ticks más = 400 ticks total)
        if (enemies.isEmpty()) {
            player.displayClientMessage(Component.literal("§c✖ No se detectaron objetivos. ¡Penalización de +10s de enfriamiento!"), true);
            skills.setCooldown(this.getId(), 400);
            return;
        }

        // Seleccionar al enemigo más cercano
        LivingEntity target = enemies.get(0);
        Vec3 trajectory = target.position().subtract(player.position()).normalize();

        Arrow homingArrow = new Arrow(level, player);
        homingArrow.shoot(trajectory.x, trajectory.y + 0.35, trajectory.z, 2.5f, 0.0f);
        homingArrow.setBaseDamage(homingArrow.getBaseDamage() * 1.8);
        level.addFreshEntity(homingArrow);

        level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getEyeY(), player.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.2f, 1.3f);
        player.displayClientMessage(Component.literal("§9🎯 ¡FLECHA TELEDIRIGIDA enviada contra: §f" + target.getName().getString() + "§9!"), true);
    }
}