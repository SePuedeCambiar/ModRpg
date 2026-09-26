package com.example.modrpg.skills.magic.modular;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

public enum SpellElement {
    FIRE(
            "Fuego", "§c",
            8.0f, 18.0f, 40,
            () -> ParticleTypes.FLAME,
            () -> SoundEvents.BLAZE_SHOOT,
            () -> Items.FIRE_CHARGE
    ),
    LIGHTNING(
            "Rayo", "§b",
            7.0f, 22.0f, 50,
            () -> ParticleTypes.ELECTRIC_SPARK,
            () -> SoundEvents.LIGHTNING_BOLT_THUNDER,
            () -> Items.AMETHYST_SHARD
    ),
    FROST(
            "Hielo", "§3",
            6.0f, 16.0f, 40,
            () -> ParticleTypes.SNOWFLAKE,
            () -> SoundEvents.SNOW_GOLEM_SHOOT,
            () -> Items.SNOWBALL
    ),
    VOID(
            "Vacío", "§5",
            9.0f, 26.0f, 60,
            () -> ParticleTypes.PORTAL,
            () -> SoundEvents.WARDEN_SONIC_BOOM,
            () -> Items.ENDER_EYE
    ),
    HOLY(
            "Sagrado", "§e",
            7.0f, 20.0f, 45,
            () -> ParticleTypes.END_ROD,
            () -> SoundEvents.BEACON_ACTIVATE,
            () -> Items.NETHER_STAR
    );

    private final String displayName;
    private final String colorCode;
    private final float baseDamage;
    private final float baseManaCost;
    private final int baseCooldownTicks;
    private final Supplier<ParticleOptions> particleSupplier;
    private final Supplier<SoundEvent> soundSupplier;
    private final Supplier<Item> iconSupplier;

    SpellElement(String displayName, String colorCode, float baseDamage, float baseManaCost,
                 int baseCooldownTicks,
                 Supplier<ParticleOptions> particleSupplier,
                 Supplier<SoundEvent> soundSupplier,
                 Supplier<Item> iconSupplier) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.baseDamage = baseDamage;
        this.baseManaCost = baseManaCost;
        this.baseCooldownTicks = baseCooldownTicks;
        this.particleSupplier = particleSupplier;
        this.soundSupplier = soundSupplier;
        this.iconSupplier = iconSupplier;
    }

    public String getDisplayName() { return displayName; }
    public String getColorCode() { return colorCode; }
    public float getBaseDamage() { return baseDamage; }
    public float getBaseManaCost() { return baseManaCost; }
    public int getBaseCooldownTicks() { return baseCooldownTicks; }

    public ParticleOptions getParticle() {
        return particleSupplier.get();
    }

    public SoundEvent getCastSound() {
        return soundSupplier.get();
    }

    public Item getIconItem() {
        return iconSupplier.get();
    }

    public Component getFormattedName() {
        return Component.literal(colorCode + displayName);
    }

    public void applyOnHitEffect(LivingEntity caster, LivingEntity victim, float damageDealt) {
        switch (this) {
            case FIRE -> victim.setSecondsOnFire(4);
            case FROST -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false));
            case VOID -> caster.heal(Math.max(1.0f, damageDealt * 0.20f));
            case HOLY -> {
                if (victim.isInvertedHealAndHarm()) {
                    victim.hurt(caster.damageSources().magic(), damageDealt * 0.75f);
                }
            }
            case LIGHTNING -> {
                var nearby = victim.level().getEntitiesOfClass(
                        LivingEntity.class, victim.getBoundingBox().inflate(4.0),
                        e -> e != caster && e != victim && e.isAlive() && !e.isAlliedTo(caster)
                );
                if (!nearby.isEmpty()) {
                    LivingEntity secondary = nearby.get(0);
                    secondary.hurt(caster.damageSources().magic(), damageDealt * 0.5f);
                }
            }
        }
    }
}