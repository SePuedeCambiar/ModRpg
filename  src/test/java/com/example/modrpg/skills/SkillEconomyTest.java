package com.example.modrpg.skills;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class SkillEconomyTest {

    @Test
    @DisplayName("El costo de experiencia a nivel 0 no debe ser cero ni negativo")
    void testInitialXpCost() {
        int cost = SkillEconomy.getXpCost(0);
        assertTrue(cost >= 1, "El costo a nivel 0 debe ser al menos 1 nivel de XP");
    }

    @Test
    @DisplayName("El costo de experiencia a nivel 99/100 debe exigir 100 niveles de XP")
    void testMaxLevelXpCost() {
        int costLevel99 = SkillEconomy.getXpCost(99);
        int costLevel100 = SkillEconomy.getXpCost(100);

        assertEquals(100, costLevel99, "El nivel final antes de maestría debe requerir 100 niveles de XP");
        assertEquals(100, costLevel100, "Al nivel máximo el costo se congela en 100");
    }

    @ParameterizedTest(name = "Para subir al nivel {0}, se requieren {1} bajas de práctica")
    @CsvSource({
            "1, 3",
            "5, 15",
            "10, 30",
            "50, 150",
            "100, 300"
    })
    @DisplayName("El escalado de práctica requerida debe ser lineal y coherente")
    void testRequiredKillsProgression(int nextLevel, int expectedKills) {
        assertEquals(expectedKills, SkillEconomy.getRequiredKills(nextLevel));
    }
}