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
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FireballSkill extends SkillNode {

    public FireballSkill() {
        super(
                SkillRegistry.NODE_FIREBALL,
                SkillRegistry.BRANCH_MAGIC,
                Component.literal("Piroclasto Elemental"),
                Component.literal("Lanza una ráfaga de fuego arcano hacia el frente que incinera y daña a todos los enemigos en su paso."),
                NodeType.ACTIVE_ABILITY,
                100 // 5 segundos de recarga
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        Vec3 start = player.getEyePosition();

        int magicLvl = skills.getBranchLevel(SkillRegistry.BRANCH_MAGIC);
        float damage = 8.0f + (magicLvl * 0.4f);

        Set<LivingEntity> hit = new HashSet<>();
        for (int i = 1; i <= 10; i++) {
            Vec3 point = start.add(look.scale(i));
            level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 5, 0.2, 0.2, 0.2, 0.05);
            level.sendParticles(ParticleTypes.LAVA, point.x, point.y, point.z, 1, 0.1, 0.1, 0.1, 0);

            AABB box = new AABB(point.x - 1.2, point.y - 1.0, point.z - 1.2, point.x + 1.2, point.y + 1.0, point.z + 1.2);
            List<LivingEntity> enemies = level.getEntitiesOfClass(
                    LivingEntity.class,
                    box,
                    e -> e != player && e.isAlive() && !e.isAlliedTo(player) && !hit.contains(e)
            );

            for (LivingEntity e : enemies) {
                hit.add(e);
                e.hurt(player.damageSources().magic(), damage);
                e.setSecondsOnFire(5 + (magicLvl / 10));
            }
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.displayClientMessage(Component.literal("§c🔥 ¡PIROCLASTO LANZADO! §fEnemigos quemados: §e" + hit.size()), true);
    }
}
