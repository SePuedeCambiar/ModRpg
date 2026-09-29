package com.example.modrpg.ai.nemesis;

import com.example.modrpg.ai.EnemyArchetype;

import java.util.ArrayList;
import java.util.List;

/**
 * Calcula el tamaño exacto y la composición de roles de la horda
 * según el día del mundo hardcore.
 */
public class HordeComposition {

    /**
     * Fórmula matemática: N = min(10, 3 + floor(Día / 15))
     */
    public static int calculateHordeSize(long dayTime) {
        long day = dayTime / 24000L;
        return Math.min(10, 3 + (int) (day / 15));
    }

    /**
     * Construye una lista quirúrgica de arquetipos según el tamaño de la horda.
     */
    public static List<EnemyArchetype> buildSquadArchetypes(int size) {
        List<EnemyArchetype> squad = new ArrayList<>();

        // 1. Siempre hay al menos 1 Tanque frontal y 1 Tirador
        squad.add(EnemyArchetype.FLAME_JUGGERNAUT);
        squad.add(EnemyArchetype.CRYO_SNIPER);

        if (size >= 3) squad.add(EnemyArchetype.VOID_WEAVER);     // Flanqueador
        if (size >= 4) squad.add(EnemyArchetype.STORM_EVOKER);    // Soporte elemental
        if (size >= 5) squad.add(EnemyArchetype.CRYO_SNIPER);     // Segundo tirador (supresión)
        if (size >= 6) squad.add(EnemyArchetype.FLAME_JUGGERNAUT);// Segundo tanque ancla
        if (size >= 7) squad.add(EnemyArchetype.CRYPT_NECROMANCER);// Invocador de cripta
        if (size >= 8) squad.add(EnemyArchetype.VOID_WEAVER);     // Segundo flanqueador
        if (size >= 9) squad.add(EnemyArchetype.STORM_EVOKER);    // Daño masivo
        if (size >= 10) squad.add(EnemyArchetype.CRYPT_NECROMANCER); // Soporte final

        return squad;
    }
}