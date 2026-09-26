package com.example.modrpg.skills.magic.modular;

public enum SpellTiming {
    RAPID_FIRE("Ráfaga Rápida (Metralleta)", 0.35f, 0.35f, 0.15f), // ~6 ticks de CD (0.3s)
    BALANCED("Equilibrado", 1.0f, 1.0f, 1.0f),                    // ~2.5s de CD
    HEAVY_BURST("Detonación Pesada", 2.6f, 2.2f, 3.2f);           // ~10s a 14s de CD

    private final String displayName;
    private final float damageMultiplier;
    private final float manaMultiplier;
    private final float cooldownMultiplier;

    SpellTiming(String displayName, float damageMultiplier, float manaMultiplier, float cooldownMultiplier) {
        this.displayName = displayName;
        this.damageMultiplier = damageMultiplier;
        this.manaMultiplier = manaMultiplier;
        this.cooldownMultiplier = cooldownMultiplier;
    }

    public String getDisplayName() { return displayName; }
    public float getDamageMultiplier() { return damageMultiplier; }
    public float getManaMultiplier() { return manaMultiplier; }
    public float getCooldownMultiplier() { return cooldownMultiplier; }
}