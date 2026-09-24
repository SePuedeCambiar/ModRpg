package com.example.modrpg.skills.nodes.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

public class MinionHelper {

    public static final String TAG_MINION = "modrpg_minion";
    public static final String TAG_OWNER_PREFIX = "modrpg_owner_";
    public static final String NBT_LIFESPAN = "modrpg_lifespan";

    /**
     * Genera una entidad aliada con tiempo de vida limitado y etiquetas de pertenencia.
     */
    public static <T extends Mob> T spawnMinion(ServerPlayer owner,
                                                EntityType<T> type,
                                                Vec3 spawnPos,
                                                int lifespanTicks,
                                                Consumer<T> setup) {
        ServerLevel level = (ServerLevel) owner.level();
        T minion = type.create(level);
        if (minion == null) return null;

        minion.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, owner.getYRot(), 0.0f);

        // Etiquetas de identificación
        minion.addTag(TAG_MINION);
        minion.addTag(TAG_OWNER_PREFIX + owner.getStringUUID());

        CompoundTag data = minion.getPersistentData();
        data.putInt(NBT_LIFESPAN, lifespanTicks);

        // Personalización de equipo, tamaño, etc.
        if (setup != null) {
            setup.accept(minion);
        }

        level.addFreshEntity(minion);

        // Efectos de invocación
        level.sendParticles(ParticleTypes.SOUL, spawnPos.x, spawnPos.y + 0.5, spawnPos.z, 15, 0.3, 0.4, 0.3, 0.05);
        level.sendParticles(ParticleTypes.POOF, spawnPos.x, spawnPos.y + 0.5, spawnPos.z, 8, 0.2, 0.2, 0.2, 0.02);

        return minion;
    }

    /**
     * Comprueba si una entidad es un esbirro perteneciente a un jugador específico.
     */
    public static boolean isMinionOf(Entity entity, LivingEntity potentialOwner) {
        if (!entity.getTags().contains(TAG_MINION)) return false;
        return entity.getTags().contains(TAG_OWNER_PREFIX + potentialOwner.getStringUUID());
    }

    /**
     * Comprueba si dos entidades son aliadas (mismo dueño o dueño y esbirro).
     */
    public static boolean areAllies(Entity a, Entity b) {
        if (a == null || b == null) return false;
        if (a == b) return true;

        // Jugador atacando a su propio esbirro
        if (a instanceof ServerPlayer player && isMinionOf(b, player)) return true;
        if (b instanceof ServerPlayer player && isMinionOf(a, player)) return true;

        // Dos esbirros del mismo dueño peleándose entre sí
        if (a.getTags().contains(TAG_MINION) && b.getTags().contains(TAG_MINION)) {
            for (String tag : a.getTags()) {
                if (tag.startsWith(TAG_OWNER_PREFIX) && b.getTags().contains(tag)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Decrementa el tiempo de vida en cada tick. Si llega a 0, la criatura desaparece limpiamente.
     */
    public static void tickMinion(LivingEntity minion) {
        CompoundTag data = minion.getPersistentData();
        if (!data.contains(NBT_LIFESPAN)) return;

        int remaining = data.getInt(NBT_LIFESPAN) - 1;
        if (remaining <= 0) {
            ServerLevel level = (ServerLevel) minion.level();
            level.sendParticles(ParticleTypes.POOF, minion.getX(), minion.getY() + 0.5, minion.getZ(), 12, 0.2, 0.3, 0.2, 0.05);
            level.playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.2f);
            minion.discard();
        } else {
            data.putInt(NBT_LIFESPAN, remaining);
        }
    }

    /**
     * Ordena a todos los esbirros cercanos del jugador que ataquen al objetivo indicado.
     */
    public static void redirectMinionsTarget(ServerPlayer owner, LivingEntity target, double radius) {
        if (target == null || !target.isAlive() || areAllies(owner, target)) return;

        ServerLevel level = (ServerLevel) owner.level();
        AABB area = owner.getBoundingBox().inflate(radius);
        List<Mob> minions = level.getEntitiesOfClass(
                Mob.class, area,
                mob -> isMinionOf(mob, owner) && mob.isAlive()
        );

        for (Mob minion : minions) {
            minion.setTarget(target);
        }
    }
}