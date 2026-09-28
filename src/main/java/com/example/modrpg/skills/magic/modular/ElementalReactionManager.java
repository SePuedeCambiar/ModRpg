package com.example.modrpg.skills.magic.modular;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ElementalReactionManager {

    private static final String TAG_PRIMER_ELEMENT = "modrpg_primer_elem";
    private static final String TAG_PRIMER_TIMER   = "modrpg_primer_timer";
    private static final int PRIMER_DURATION_TICKS = 100; // 5 segundos

    /**
     * Procesa el impacto de un elemento sobre una entidad y verifica si gatilla una reacción en cadena.
     */
    public static void handleElementalHit(LivingEntity caster, LivingEntity victim, SpellElement incoming, float damage) {
        if (victim.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) victim.level();
        CompoundTag data = victim.getPersistentData();

        if (data.contains(TAG_PRIMER_ELEMENT) && data.getInt(TAG_PRIMER_TIMER) > 0) {
            String primedName = data.getString(TAG_PRIMER_ELEMENT);
            SpellElement primed = SpellElement.valueOf(primedName);

            if (primed != incoming) {
                boolean triggered = triggerReaction(caster, victim, level, primed, incoming, damage);
                if (triggered) {
                    // Consume el primer tras reaccionar
                    data.remove(TAG_PRIMER_ELEMENT);
                    data.remove(TAG_PRIMER_TIMER);
                    return;
                }
            }
        }

        // Si no hubo reacción (o es el primer golpe), se ceba a la víctima con este elemento
        data.putString(TAG_PRIMER_ELEMENT, incoming.name());
        data.putInt(TAG_PRIMER_TIMER, PRIMER_DURATION_TICKS);

        // Partícula sutil indicando el elemento activo sobre el mob
        level.sendParticles(incoming.getParticle(), victim.getX(), victim.getY() + 0.8, victim.getZ(), 4, 0.2, 0.3, 0.2, 0.02);
    }

    private static boolean triggerReaction(LivingEntity caster, LivingEntity victim, ServerLevel level,
                                           SpellElement e1, SpellElement e2, float baseDamage) {
        boolean matchFrostLightning = (e1 == SpellElement.FROST && e2 == SpellElement.LIGHTNING) || (e1 == SpellElement.LIGHTNING && e2 == SpellElement.FROST);
        boolean matchFireVoid       = (e1 == SpellElement.FIRE && e2 == SpellElement.VOID) || (e1 == SpellElement.VOID && e2 == SpellElement.FIRE);
        boolean matchFireHoly       = (e1 == SpellElement.FIRE && e2 == SpellElement.HOLY) || (e1 == SpellElement.HOLY && e2 == SpellElement.FIRE);
        boolean matchFrostVoid      = (e1 == SpellElement.FROST && e2 == SpellElement.VOID) || (e1 == SpellElement.VOID && e2 == SpellElement.FROST);

        // 1. REACCIÓN: SUPERCONDUCTOR (Hielo + Rayo)
        if (matchFrostLightning) {
            float reactionDamage = 14.0f;
            AABB zone = victim.getBoundingBox().inflate(5.0);
            List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class, zone, e -> e != caster && e.isAlive());

            for (LivingEntity e : enemies) {
                e.hurt(caster.damageSources().indirectMagic(caster, caster), reactionDamage);
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1)); // -4 de ataque y defensa física debilitada
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 1.0, e.getZ(), 15, 0.3, 0.3, 0.3, 0.1);
            }

            level.sendParticles(ParticleTypes.SONIC_BOOM, victim.getX(), victim.getY() + 1.0, victim.getZ(), 1, 0, 0, 0, 0);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 1.8f);

            notifyPlayer(caster, "§b⚡ ¡SUPERCONDUCTOR! §f(Defensa enemiga destrozada)");
            return true;
        }

        // 2. REACCIÓN: COLAPSO GRAVITATORIO (Fuego + Vacío)
        if (matchFireVoid) {
            Vec3 center = victim.position();
            AABB zone = victim.getBoundingBox().inflate(6.0);
            List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class, zone, e -> e != caster && e.isAlive());

            for (LivingEntity e : enemies) {
                // Succión hacia el vórtice
                Vec3 toCenter = center.subtract(e.position()).normalize().scale(0.85);
                e.setDeltaMovement(new Vec3(toCenter.x, 0.35, toCenter.z));
                e.hurtMarked = true;
                e.setSecondsOnFire(5);
                e.hurt(caster.damageSources().indirectMagic(caster, caster), 12.0f);
            }

            level.sendParticles(ParticleTypes.PORTAL, center.x, center.y + 1.0, center.z, 60, 1.5, 1.0, 1.5, 0.2);
            level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 0.5, center.z, 15, 0.5, 0.5, 0.5, 0.1);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 1.2f);

            notifyPlayer(caster, "§5🌌 ¡COLAPSO GRAVITATORIO! §f(Enemigos absorbidos)");
            return true;
        }

        // 3. REACCIÓN: PIRA PURIFICADORA (Fuego + Sagrado)
        if (matchFireHoly) {
            float bonus = victim.isInvertedHealAndHarm() ? baseDamage * 2.0f : baseDamage * 0.5f;
            victim.hurt(caster.damageSources().indirectMagic(caster, caster), bonus);
            victim.setSecondsOnFire(8);

            // Pulso sanador para aliados
            if (caster != null && caster.isAlive()) {
                caster.heal(6.0f);
            }

            level.sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY() + 1.0, victim.getZ(), 30, 0.5, 0.5, 0.5, 0.15);
            level.sendParticles(ParticleTypes.FLAME, victim.getX(), victim.getY() + 1.0, victim.getZ(), 20, 0.4, 0.4, 0.4, 0.1);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.6f);

            notifyPlayer(caster, "§e✨ ¡PIRA PURIFICADORA! §f(Cura aliada y daño sagrado)");
            return true;
        }

        // 4. REACCIÓN: FRAGILIDAD ABISAL (Hielo + Vacío)
        if (matchFrostVoid) {
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 4)); // Casi inmóvil por 4s
            victim.addTag("modrpg_brittle_ice");

            level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY() + 1.0, victim.getZ(), 40, 0.5, 0.5, 0.5, 0.05);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.4f, 0.8f);

            notifyPlayer(caster, "§3❄ ¡FRAGILIDAD ABISAL! §f(El próximo golpe físico romperá el hielo)");
            return true;
        }

        return false;
    }

    public static void tickPrimers(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (data.contains(TAG_PRIMER_TIMER)) {
            int remaining = data.getInt(TAG_PRIMER_TIMER) - 1;
            if (remaining <= 0) {
                data.remove(TAG_PRIMER_TIMER);
                data.remove(TAG_PRIMER_ELEMENT);
            } else {
                data.putInt(TAG_PRIMER_TIMER, remaining);
            }
        }
    }

    private static void notifyPlayer(LivingEntity caster, String message) {
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }
}