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
        assertEquals(1, SkillProgression.getXpCost(0), "Nivel 0 debe costar 1 nivel");
        assertTrue(SkillProgression.getXpCost(10) <= 5, "Nivel 10 debe costar entre 3 y 5 niveles de XP");
        assertTrue(SkillProgression.getXpCost(50) >= 30, "Nivel 50 debe costar al menos 30 niveles");
        assertEquals(100, SkillProgression.getXpCost(99), "Para pasar a nivel 100 debe costar 100 niveles de XP");
        assertEquals(100, SkillProgression.getXpCost(100), "A nivel 100 se congela en 100");
    }

    @Test
    @DisplayName("La curva de daño CaC debe ser estrictamente lineal (+2% por nivel, triplicando a nivel 100)")
    void testMeleeDamageCurveLinear() {
        double baseAttack = 10.0;

        // Nivel 0: sin bono
        assertEquals(0.0, SkillProgression.getMeleeBonusDamage(baseAttack, 0), 0.001);

        // Nivel 1: +2% exacto (+0.2 de daño)
        assertEquals(0.2, SkillProgression.getMeleeBonusDamage(baseAttack, 1), 0.001);

        // Nivel 10: +20% (+2.0 de daño)
        assertEquals(2.0, SkillProgression.getMeleeBonusDamage(baseAttack, 10), 0.001);

        // Nivel 50: +100% (+10.0 de daño, duplica el daño base)
        assertEquals(10.0, SkillProgression.getMeleeBonusDamage(baseAttack, 50), 0.001);

        // Nivel 100: +200% exacto (+20.0 de daño, triplica el daño total)
        assertEquals(20.0, SkillProgression.getMeleeBonusDamage(baseAttack, 100), 0.001);
    }

    @Test
    @DisplayName("La curva de defensa pasiva debe otorgar mitigación hasta un 40% a nivel 100")
    void testDefenseMitigationCurve() {
        assertEquals(1.0f, SkillProgression.getDefenseDamageFactor(0), 0.01f);
        assertEquals(0.60f, SkillProgression.getDefenseDamageFactor(100), 0.02f);
    }

    @ParameterizedTest(name = "Para subir al nivel {0}, se requieren {1} puntos de práctica")
    @CsvSource({
            "1, 3",
            "10, 30",
            "50, 150",
            "100, 300"
    })
    void testUniversalPracticeRequirements(int nextLevel, int expectedPractice) {
        assertEquals(expectedPractice, SkillProgression.getRequiredPractice(nextLevel));
    }
}