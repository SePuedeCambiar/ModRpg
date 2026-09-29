package com.example.modrpg.ai.nemesis;

/**
 * Define las contramedidas mecánicas de los Capitanes Némesis.
 */
public enum NemesisTrait {
    ANTI_AIR_GRAVITY("ANTI_AIR_GRAVITY", "Red Gravitatoria", "Anula saltos e impulsos verticales por 6s."),
    SHIELD_BREAKER("SHIELD_BREAKER", "Hendidor de Escudos", "Ataques pesados que desactivan escudos por 5s."),
    MANA_DRAINER("MANA_DRAINER", "Silenciador Arcano", "Drena 25 de maná y disipa cebados elementales a 1s."),
    PROJECTILE_DEFLECTOR("PROJECTILE_DEFLECTOR", "Muro Cinético", "Desvía el 100% de los proyectiles frontales."),
    ADAPTIVE_WARRIOR("ADAPTIVE_WARRIOR", "Veterano de Choque", "Equilibrio táctico y contraataques rápidos.");

    private final String id;
    private final String displayName;
    private final String description;

    NemesisTrait(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    /**
     * Selecciona la contramedida perfecta según el hábito del jugador analizado en el Sprint 9.
     */
    public static NemesisTrait selectCounterForStyle(PlayerCombatProfiler.DominantStyle style) {
        return switch (style) {
            case AIR_JUMPER -> ANTI_AIR_GRAVITY;
            case SHIELD_TURTLE -> SHIELD_BREAKER;
            case ELEMENTAL_MAGE -> MANA_DRAINER;
            case SNIPER_KITER -> PROJECTILE_DEFLECTOR;
            case MELEE_BERSERKER -> SHIELD_BREAKER;
            case BALANCED -> ADAPTIVE_WARRIOR;
        };
    }
}