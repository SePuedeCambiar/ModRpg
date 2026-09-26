package com.example.modrpg.skills.magic.modular;

public enum SpellShape {
    PROJECTILE("Proyectil", 1.0f, 1.0f, 1.0f),
    BEAM("Rayo Instantáneo", 0.9f, 1.15f, 0.8f),
    GROUND_AOE("Runa de Área", 1.3f, 1.35f, 1.4f),
    SELF_AURA("Aura de Estallido", 0.8f, 1.1f, 1.2f),
    TOUCH("Toque Cercano", 1.4f, 0.8f, 0.7f);

    private final String displayName;
    private final float damageMultiplier;
    private final float manaMultiplier;
    private final float cooldownMultiplier;

    SpellShape(String displayName, float damageMultiplier, float manaMultiplier, float cooldownMultiplier) {
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