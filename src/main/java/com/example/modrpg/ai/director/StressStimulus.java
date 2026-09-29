package com.example.modrpg.ai.director;

/**
 * Ponderación matemática de estímulos que incrementan o alivian
 * la tensión del jugador en el medidor de estrés S(t).
 */
public enum StressStimulus {
    LOW_HEALTH(0.04f),            // Salud crítica (< 40%)
    LOW_MANA(0.02f),              // Maná agotado (< 20%)
    HOSTILE_PROXIMITY_FAR(0.015f),// Enemigos entre 6 y 15 bloques
    HOSTILE_PROXIMITY_CLOSE(0.04f),// Enemigos a menos de 6 bloques
    DARKNESS(0.01f),              // Nivel de luz <= 4
    DEEP_CAVE(0.015f),            // Profundidad extrema (Y < 0)
    BURST_DAMAGE(0.15f),          // Impacto de daño reciente directo

    // Factores de alivio y recuperación
    DECAY_PEACEFUL(-0.03f),       // Sin enemigos cercanos, salud regenerando
    DECAY_SAFE_ZONE(-0.05f);      // Base segura iluminada (Luz >= 12)

    private final float deltaPerSecond;

    StressStimulus(float deltaPerSecond) {
        this.deltaPerSecond = deltaPerSecond;
    }

    public float getDeltaPerSecond() {
        return deltaPerSecond;
    }
}