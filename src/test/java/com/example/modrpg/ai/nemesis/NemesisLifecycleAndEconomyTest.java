package com.example.modrpg.ai.nemesis;

import com.example.modrpg.ai.nemesis.goals.NemesisEscapeGoal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NemesisLifecycleAndEconomyTest {

    @Test
    @DisplayName("D1: NemesisHordeManager debe definir y persistir el tick de última incursión en NBT")
    void testRaidCooldownMustBePersistedInNbt() {
        boolean hasPersistentTagConstant = false;
        for (Field field : NemesisHordeManager.class.getDeclaredFields()) {
            if (field.getType().equals(String.class) && field.getName().equals("TAG_LAST_RAID_GAMETIME")) {
                hasPersistentTagConstant = true;
                break;
            }
        }

        assertTrue(
                hasPersistentTagConstant,
                "FALLO CRÍTICO (D1): NemesisHordeManager debe definir la constante TAG_LAST_RAID_GAMETIME " +
                        "para persistir el tick de raid en el NBT del jugador y evitar el exploit de reconexión."
        );
    }

    @Test
    @DisplayName("D4: NemesisEscapeGoal debe tener cooldown para evitar bucles infinitos de bombas de humo en cuevas")
    void testEscapeGoalMustHaveCooldownToPreventSmokeLoop() {
        boolean hasCooldownField = false;
        for (Field field : NemesisEscapeGoal.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            if (name.contains("cooldown")) {
                hasCooldownField = true;
                break;
            }
        }

        assertTrue(
                hasCooldownField,
                "FALLO (D4): NemesisEscapeGoal debe contar con un campo de cooldown de escape (escapeCooldownTicks)."
        );
    }

    @Test
    @DisplayName("D4: NemesisDialogueHelper no debe borrar prematuramente la memoria de intro durante la huida")
    void testTriggerEscapeMustNotPrematurelyWipeIntroMemory() throws Exception {
        UUID mobId = UUID.randomUUID();

        Field introMapField = NemesisDialogueHelper.class.getDeclaredField("LAST_INTRO_TICK");
        introMapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Long> introMap = (Map<UUID, Long>) introMapField.get(null);

        // Simulamos que el capitán registró su intro
        introMap.put(mobId, 1000L);

        // Disparamos triggerEscape con parámetros nulos controlados (no debe tocar LAST_INTRO_TICK)
        NemesisDialogueHelper.triggerEscape(null, null, null, null);

        assertTrue(
                introMap.containsKey(mobId),
                "FALLO (D4): NemesisDialogueHelper.triggerEscape no debe purgar la memoria de intro."
        );
    }

    @Test
    @DisplayName("D3: NemesisSavedData y NemesisCaptain deben poder registrar muertes ambientales sin jugador directo")
    void testCaptainDeathMustBeUpdatableWithoutPlayer() {
        UUID captainId = UUID.randomUUID();
        NemesisCaptain captain = new NemesisCaptain(
                captainId, "Azgar", "el Feroz", "SHIELD_BREAKER", 50, 15,
                NemesisCaptain.Status.STALKING, "NONE"
        );
        NemesisSavedData data = new NemesisSavedData();
        data.addOrUpdateCaptain(captain);

        // Invocamos el método handleCaptainDeath de NemesisHordeManager
        NemesisHordeManager.handleCaptainDeath(data, captain);

        assertEquals(
                NemesisCaptain.Status.DEAD,
                data.getCaptain(captainId).getStatus(),
                "FALLO CRÍTICO (D3): Si un capitán muere por causas ambientales, debe actualizarse a DEAD en NemesisSavedData."
        );
    }
}