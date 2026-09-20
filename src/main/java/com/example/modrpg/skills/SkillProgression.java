package com.example.modrpg.skills;

public class SkillProgression {

    public static final int MAX_LEVEL = 100;

    /**
     * Calcula el costo en niveles de XP del jugador.
     * Escala desde 1 nivel hasta 100 niveles exactos al nivel 99/100.
     */
    public static int getXpCost(int currentLevel) {
        if (currentLevel >= MAX_LEVEL - 1) return 100;
        if (currentLevel <= 0) return 1;

        double progress = (double) currentLevel / (MAX_LEVEL - 1);
        return Math.max(1, (int) Math.round(1.0 + Math.pow(progress, 1.6) * 99.0));
    }

    /**
     * Calcula el bono plano de daño cuerpo a cuerpo.
     * Curva de poder: casi nula al inicio, pero escala hasta +200% a nivel 100.
     */
    public static double getMeleeBonusDamage(double baseAttack, int level) {
        if (level <= 0) return 0.0;
        int clampedLevel = Math.min(level, MAX_LEVEL);

        // Curva progresiva (L / 100)^1.6 * 2.0
        double multiplier = Math.pow((double) clampedLevel / MAX_LEVEL, 1.6) * 2.0;
        return baseAttack * multiplier;
    }

    /**
     * Calcula el bono de velocidad de movimiento para el atributo de Minecraft.
     */
    public static double getMobilityBonusSpeed(int level) {
        if (level <= 0) return 0.0;
        int clampedLevel = Math.min(level, MAX_LEVEL);
        return Math.pow((double) clampedLevel / MAX_LEVEL, 1.5) * 0.08;
    }

    /**
     * Multiplicador de mitigación de daño defensivo.
     * Nivel 0 -> 1.0 (recibe 100% de daño).
     * Nivel 100 -> 0.60 (recibe solo 60% de daño = 40% de reducción pasiva).
     */
    public static float getDefenseDamageFactor(int level) {
        if (level <= 0) return 1.0f;
        int clampedLevel = Math.min(level, MAX_LEVEL);
        float reduction = (float) Math.pow((double) clampedLevel / MAX_LEVEL, 1.4) * 0.40f;
        return Math.max(0.60f, 1.0f - reduction);
    }

    /**
     * Requisito de práctica acumulada para subir al nivel siguiente.
     */
    public static int getRequiredPractice(int nextLevel) {
        return nextLevel * 3;
    }
}