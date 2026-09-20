package com.example.modrpg.skills;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class SkillProgressionTest {

    @Test
    @DisplayName("El costo de XP debe escalar suave al inicio y exigir 100 niveles de XP a nivel 99/100")
    void testXpCostProgression() {
        // Niveles bajos son muy baratos
        assertEquals(1, SkillProgression.getXpCost(0), "Nivel 0 debe costar 1 nivel");
        assertTrue(SkillProgression.getXpCost(10) <= 5, "Nivel 10 debe costar entre 3 y 5 niveles de XP");

        // Nivel medio requiere esfuerzo
        assertTrue(SkillProgression.getXpCost(50) >= 30, "Nivel 50 debe costar al menos 30 niveles");

        // Nivel 99 exige exactamente los 100 niveles de XP pedidos por el reto
        assertEquals(100, SkillProgression.getXpCost(99), "Para pasar a nivel 100 debe costar 100 niveles de XP");
        assertEquals(100, SkillProgression.getXpCost(100), "A nivel 100 se congela en 100");
    }

    @Test
    @DisplayName("La curva de daño CaC no debe notarse a nivel bajo pero debe triplicar (+200%) a nivel 100")
    void testMeleeDamageCurve() {
        double baseAttack = 9.0; // Espada de diamante base

        // Nivel 0: sin bono
        assertEquals(0.0, SkillProgression.getMeleeBonusDamage(baseAttack, 0), 0.01);

        // Nivel 5: el bono es insignificante (+0.1 de daño)
        double lowBonus = SkillProgression.getMeleeBonusDamage(baseAttack, 5);
        assertTrue(lowBonus < 0.25, "A nivel 5 casi no debe notarse el aumento");

        // Nivel 100: bono exacto del +200% (+18 de daño -> total 27)
        double maxBonus = SkillProgression.getMeleeBonusDamage(baseAttack, 100);
        assertEquals(18.0, maxBonus, 0.1, "A nivel 100 el bono debe ser exactamente +200% del daño base");
    }

    @Test
    @DisplayName("La curva de velocidad de movilidad debe ser segura para el FOV de Minecraft")
    void testMobilitySpeedCurve() {
        assertEquals(0.0, SkillProgression.getMobilityBonusSpeed(0));

        // A nivel 100 debe dar un bono máximo controlado (+0.08 de velocidad)
        assertEquals(0.08, SkillProgression.getMobilityBonusSpeed(100), 0.005);
    }

    @Test
    @DisplayName("La curva de defensa pasiva debe otorgar mitigación con rendimientos decrecientes (máximo 40%)")
    void testDefenseMitigationCurve() {
        assertEquals(1.0f, SkillProgression.getDefenseDamageFactor(0), 0.01f, "Nivel 0 recibe 100% de daño");

        // A nivel 100 mitiga el 40% (recibe solo el 60% del impacto)
        assertEquals(0.60f, SkillProgression.getDefenseDamageFactor(100), 0.02f, "Nivel 100 debe recibir solo el 60% de daño");
    }

    @ParameterizedTest(name = "Para subir al nivel {0}, se requieren {1} puntos de práctica")
    @CsvSource({
            "1, 3",
            "10, 30",
            "50, 150",
            "100, 300"
    })
    @DisplayName("Los requisitos de práctica deben ser predecibles para todas las ramas")
    void testUniversalPracticeRequirements(int nextLevel, int expectedPractice) {
        assertEquals(expectedPractice, SkillProgression.getRequiredPractice(nextLevel));
    }
}