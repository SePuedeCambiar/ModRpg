package com.example.modrpg.ai.director;

import com.example.modrpg.ai.TacticalCasterGoal;
import com.example.modrpg.ai.goals.TacticalBoundingGoal;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Queue;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AlienDirectorAndStressTest {

    @Test
    @DisplayName("C1: AudioFootprintTracker debe retornar el ping más reciente y no el más viejo de la cola")
    void testAudioTrackerMustReturnLatestPing() throws Exception {
        AudioFootprintTracker.clearAll();

        Field activePingsField = AudioFootprintTracker.class.getDeclaredField("ACTIVE_PINGS");
        activePingsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Queue<Object> activePings = (Queue<Object>) activePingsField.get(null);

        UUID playerUUID = UUID.randomUUID();
        Vec3 pos = new Vec3(10, 64, 10);

        // Ping antiguo (tick 10)
        AudioFootprintTracker.AcousticPing olderPing = new AudioFootprintTracker.AcousticPing(
                playerUUID, pos, AudioFootprintTracker.NoiseCategory.WALK, 10
        );
        // Ping nuevo reciente (tick 50)
        AudioFootprintTracker.AcousticPing newerPing = new AudioFootprintTracker.AcousticPing(
                playerUUID, pos, AudioFootprintTracker.NoiseCategory.MINING, 50
        );

        activePings.add(olderPing);
        activePings.add(newerPing);

        // En el código actual, findHeardPing itera la cola y retorna el primer elemento que cumpla la distancia,
        // devolviendo el ping viejo de tick 10 en lugar del eco más reciente de tick 50.
        AudioFootprintTracker.AcousticPing heard = AudioFootprintTracker.findHeardPing(pos, 24.0);

        assertNotNull(heard);
        assertEquals(
                50, heard.tickCreated(),
                "FALLO CRÍTICO (C1): AudioFootprintTracker.findHeardPing() retorna el ping más viejo en vez del más reciente. " +
                        "Esto provoca que los monstruos persigan ruidos obsoletos ignorando las detonaciones o hechizos recientes."
        );
    }

    @Test
    @DisplayName("C3: MacroDirectorManager no debe romper la fase REPRIEVE por estrés residual elevado sin daño hostil reciente")
    void testReprieveMustNotBreakOnResidualStressAlone() {
        // En el código actual, en cuanto el Director entra en REPRIEVE evalúa 'stress >= 0.70f'
        // y regresa de inmediato a CLIMAX a los 20 ticks.
        // Debe existir una regla formal (shouldBreakReprieve) que exija agresión reciente.
        boolean hasReprieveRuleMethod = false;
        boolean breaksOnResidualStress = true;

        try {
            Method m = MacroDirectorManager.class.getMethod("shouldBreakReprieve", boolean.class, int.class, float.class);
            hasReprieveRuleMethod = true;
            // Parámetros: hasCloseCombat = false, ticksSinceLastDamage = 120 (sin daño hace 6s), stress = 0.75f (alto residual)
            breaksOnResidualStress = (boolean) m.invoke(null, false, 120, 0.75f);
        } catch (ReflectiveOperationException ignored) {}

        assertTrue(
                hasReprieveRuleMethod && !breaksOnResidualStress,
                "FALLO (C3): MacroDirectorManager rompe la fase REPRIEVE inmediatamente por estrés residual (>= 0.70). " +
                        "Como los combates terminan con estrés alto, el jugador sufre un bucle de ping-pong REPRIEVE <-> CLIMAX " +
                        "y la tregua anunciada nunca se cumple."
        );
    }

    @Test
    @DisplayName("C4: MacroDirectorManager debe definir un timeout duro para CLIMAX para evitar softlocks")
    void testClimaxMustHaveHardTimeout() {
        // En el código actual, si hay piglins neutrales o granjas de mobs en 20 bloques, CLIMAX no termina nunca
        // a menos que stress >= 0.95f por 45s. Si el estrés está en 0.85, queda en bucle infinito.
        boolean hasClimaxTimeoutConstant = false;
        for (Field f : MacroDirectorManager.class.getDeclaredFields()) {
            String name = f.getName();
            if (name.contains("CLIMAX_TIMEOUT") || name.contains("MAX_CLIMAX")) {
                hasClimaxTimeoutConstant = true;
                break;
            }
        }

        assertTrue(
                hasClimaxTimeoutConstant,
                "FALLO (C4): MacroDirectorManager no define una constante de timeout duro para CLIMAX (ej. CLIMAX_TIMEOUT_TICKS). " +
                        "En el Nether o cerca de granjas de mobs, el Director queda bloqueado en CLIMAX para siempre."
        );
    }

    @Test
    @DisplayName("C8: TacticalBoundingGoal y TacticalCasterGoal deben implementar la regla de piedad (pausa a <= 6 HP)")
    void testMercyRuleMustBeImplementedInRangedAndCasterGoals() {
        boolean boundingHasMercy = false;
        for (Field f : TacticalBoundingGoal.class.getDeclaredFields()) {
            if (f.getName().toLowerCase().contains("mercy")) {
                boundingHasMercy = true;
                break;
            }
        }

        boolean casterHasMercy = false;
        for (Field f : TacticalCasterGoal.class.getDeclaredFields()) {
            if (f.getName().toLowerCase().contains("mercy")) {
                casterHasMercy = true;
                break;
            }
        }

        assertTrue(
                boundingHasMercy && casterHasMercy,
                "FALLO (C8): TacticalBoundingGoal y/o TacticalCasterGoal no tienen campos para la Regla de Piedad (mercyCooldown / mercyPauseTicks). " +
                        "Cuando el jugador cae a <= 3 corazones (6.0 HP), los tiradores y magos siguen acribillándolo sin la pausa de 2s."
        );
    }

    @Test
    @DisplayName("C7: PlayerStressTracker debe calcular la luz efectiva restando skyDarken a cielo abierto nocturno")
    void testTrueLightCalculationUnderNightSky() {
        // En el código actual, usa getRawBrightness(pos, 0), que de noche devuelve 15 a cielo abierto.
        // Debe existir calculateTrueLight(blockLight, skyLight, skyDarken) que compute la oscuridad real.
        boolean hasLightHelper = false;
        int effectiveLight = 15;

        try {
            Method m = PlayerStressTracker.class.getMethod("calculateTrueLight", int.class, int.class, int.class);
            hasLightHelper = true;
            // De noche a medianoche sin antorchas: block=0, sky=15, skyDarken=11 -> Luz real = 4
            effectiveLight = (int) m.invoke(null, 0, 15, 11);
        } catch (ReflectiveOperationException ignored) {}

        assertTrue(
                hasLightHelper && effectiveLight <= 4,
                "FALLO (C7): PlayerStressTracker no tiene un método calculateTrueLight(block, sky, skyDarken). " +
                        "Actualmente evalúa la noche oscura a cielo abierto como brillo 15, interpretándola como base segura " +
                        "y reduciendo el estrés en plena noche en lugar de aumentarlo."
        );
    }

    @Test
    @DisplayName("C6: PlayerVulnerabilityDetector debe permitir registrar y consultar progreso de minería por UUID")
    void testMiningProgressAccumulator() {
        UUID testPlayer = UUID.randomUUID();
        boolean hasUuidHelper = false;

        try {
            Method recordMethod = PlayerVulnerabilityDetector.class.getMethod("recordMining", UUID.class, int.class);
            Method getMethod = PlayerVulnerabilityDetector.class.getMethod("getMiningTicks", UUID.class);
            hasUuidHelper = true;

            recordMethod.invoke(null, testPlayer, 20);
            recordMethod.invoke(null, testPlayer, 25);
            int total = (int) getMethod.invoke(null, testPlayer);
            assertEquals(45, total);
        } catch (ReflectiveOperationException ignored) {}

        assertTrue(
                hasUuidHelper,
                "FALLO (C6): PlayerVulnerabilityDetector solo admite recordMiningProgress(ServerPlayer), " +
                        "lo que impide acumular ticks continuos de minería sin pérdida de eventos."
        );
    }
}