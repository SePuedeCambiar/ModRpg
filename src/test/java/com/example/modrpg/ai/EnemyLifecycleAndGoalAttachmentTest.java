package com.example.modrpg.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyLifecycleAndGoalAttachmentTest {

    @Test
    @DisplayName("A2: EnemyRpgManager debe persistir 'modrpg_archetype' en NBT para poder reconstruir el mob tras reinicios")
    void testArchetypeMustBeSavedInPersistentData() {
        // En el código actual, EnemyRpgManager nunca escribe "modrpg_archetype" en mob.getPersistentData()
        boolean hasArchetypeTagConstant = false;
        for (Field field : EnemyRpgManager.class.getDeclaredFields()) {
            if (field.getName().contains("ARCHETYPE") || field.getName().contains("TAG_ARCHETYPE")) {
                hasArchetypeTagConstant = true;
                break;
            }
        }

        assertTrue(
                hasArchetypeTagConstant,
                "FALLO CRÍTICO (A2): EnemyRpgManager no define ni guarda una clave 'modrpg_archetype' en persistentData. " +
                        "Al descargar el chunk, el mob pierde su identidad de clase (Cryo Sniper, Juggernaut, etc.) para siempre."
        );
    }

    @Test
    @DisplayName("B1: EnemyRpgManager debe desacoplar el equipamiento del acople de metas para purgar DESPUÉS de setItemSlot")
    void testMustDecoupleEquipmentFromGoalAttachment() {
        // En AbstractSkeleton.setItemSlot (Minecraft 1.20.1), vanilla reinserta metas de arco/hueso
        // vía reassessWeaponGoal(). Por tanto, el acople de metas tácticas y la purga deben
        // estar desacoplados y ejecutarse obligatoriamente DESPUÉS de equipar al mob.
        boolean hasAttachGoalsMethod = false;
        for (Method m : EnemyRpgManager.class.getDeclaredMethods()) {
            if (m.getName().equals("attachTacticalGoals") || m.getName().equals("attachBehaviorAndSquad")) {
                hasAttachGoalsMethod = true;
                break;
            }
        }

        assertTrue(
                hasAttachGoalsMethod,
                "FALLO CRÍTICO (B1): EnemyRpgManager no desacopla attachTacticalGoals() / attachBehaviorAndSquad(). " +
                        "Actualmente purgeVanillaAttackGoals() corre en la línea 82 ANTES de setItemSlot (línea 87), " +
                        "lo que provoca que el esqueleto reinyecte sus metas vanilla de arco/hueso inmediatamente."
        );
    }

    @Test
    @DisplayName("A2: EnemyRpgManager debe proveer un método de re-inicialización para mobs cargados de chunks")
    void testMustSupportChunkReloadReinitialization() {
        // Al recargar un chunk, un mob conserva su etiqueta TAG_INITIALIZED.
        // EnemyRpgManager debe tener un método o lógica para restaurar el escuadrón y las metas
        // sin abortar con un simple 'return' en la primera línea.
        boolean hasReloadHandler = false;
        for (Method m : EnemyRpgManager.class.getDeclaredMethods()) {
            if (m.getName().contains("Reload") || m.getName().contains("Reattach") || m.getName().equals("attachBehaviorAndSquad")) {
                hasReloadHandler = true;
                break;
            }
        }

        assertTrue(
                hasReloadHandler,
                "FALLO CRÍTICO (A2): EnemyRpgManager no provee un mecanismo para re-acoplar el escuadrón y las metas " +
                        "a un mob recargado de disco. Al recargar el chunk, el mob vuelve a comportarse como un mob vanilla."
        );
    }
}