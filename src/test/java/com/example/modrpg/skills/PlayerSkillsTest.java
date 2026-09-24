package com.example.modrpg.skills;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSkillsTest {

    private PlayerSkills skills;
    private final ResourceLocation branchMelee = new ResourceLocation("modrpg", "melee");
    private final ResourceLocation skillTornado = new ResourceLocation("modrpg", "melee_heavy_tornado");
    private final ResourceLocation counterKills = new ResourceLocation("modrpg", "melee_kills");

    @BeforeEach
    void setUp() {
        skills = new PlayerSkills();
    }

    @Test
    @DisplayName("El nivel de rama debe limitarse entre 0 y 100")
    void testBranchLevelClamping() {
        skills.setBranchLevel(branchMelee, 150);
        assertEquals(100, skills.getBranchLevel(branchMelee), "El nivel no puede superar 100");

        skills.setBranchLevel(branchMelee, -10);
        assertEquals(0, skills.getBranchLevel(branchMelee), "El nivel no puede ser negativo");

        skills.addBranchLevel(branchMelee, 5);
        assertEquals(5, skills.getBranchLevel(branchMelee));
    }

    @Test
    @DisplayName("El sistema de Cooldowns debe decrementar por tick y eliminarse al llegar a 0")
    void testCooldownTickBehavior() {
        skills.setCooldown(skillTornado, 3);
        assertTrue(skills.hasCooldown(skillTornado));

        skills.tickCooldowns();
        assertEquals(2, skills.getCooldown(skillTornado));

        skills.tickCooldowns();
        assertEquals(1, skills.getCooldown(skillTornado));

        skills.tickCooldowns();
        assertFalse(skills.hasCooldown(skillTornado));
        assertEquals(0, skills.getCooldown(skillTornado));
    }

    @Test
    @DisplayName("El desbloqueo de nodos debe ser persistente en memoria")
    void testNodeUnlockState() {
        assertFalse(skills.isNodeUnlocked(skillTornado));

        skills.unlockNode(skillTornado);
        assertTrue(skills.isNodeUnlocked(skillTornado));

        skills.lockNode(skillTornado);
        assertFalse(skills.isNodeUnlocked(skillTornado));
    }

    @Test
    @DisplayName("Los contadores de práctica deben acumular correctamente")
    void testPracticeCounters() {
        assertEquals(0, skills.getPractice(counterKills));

        skills.addPractice(counterKills, 10);
        skills.addPractice(counterKills, 15);
        assertEquals(25, skills.getPractice(counterKills));
    }

    @Test
    @DisplayName("El sistema de Loadout debe permitir equipar hasta 8 habilidades y evitar duplicados")
    void testLoadoutEquipAndSlots() {
        ResourceLocation skill1 = new ResourceLocation("modrpg", "skill_1");
        ResourceLocation skill2 = new ResourceLocation("modrpg", "skill_2");

        skills.unlockNode(skill1);
        skills.unlockNode(skill2);

        assertTrue(skills.isSkillEquipped(skill1));
        assertTrue(skills.isSkillEquipped(skill2));
        assertEquals(2, skills.getEquippedSkills().size());

        skills.unequipSkill(0);
        assertFalse(skills.isSkillEquipped(skill1));
        assertTrue(skills.isSkillEquipped(skill2));

        skills.equipSkill(0, skill1);
        assertTrue(skills.isSkillEquipped(skill1));
    }

    @Test
    @DisplayName("Simulación de sincronización y decremento de cooldown en el cliente")
    void testClientCooldownSyncAndTick() {
        // 1. Servidor aplica cooldown de 60 ticks
        PlayerSkills serverSkills = new PlayerSkills();
        serverSkills.setCooldown(skillTornado, 60);
        assertTrue(serverSkills.hasCooldown(skillTornado));

        // 2. Cliente recibe los datos y actualiza limpiamente (6 argumentos con getEquippedSkills())
        PlayerSkills clientSkills = new PlayerSkills();
        clientSkills.replaceAll(
                serverSkills.getAllBranchLevels(),
                serverSkills.getUnlockedNodes(),
                serverSkills.getAllPracticeCounters(),
                serverSkills.getAllCooldowns(),
                serverSkills.isUltimateCharged(),
                serverSkills.getEquippedSkills() // <-- Corregido el 6to argumento
        );

        assertTrue(clientSkills.hasCooldown(skillTornado));
        assertEquals(60, clientSkills.getCooldown(skillTornado));

        // 3. Simulamos 60 ticks transcurridos en el cliente
        for (int tick = 0; tick < 60; tick++) {
            clientSkills.tickCooldowns();
        }

        assertFalse(clientSkills.hasCooldown(skillTornado), "El cooldown debe expirar tras 60 ticks en el cliente");
        assertEquals(0, clientSkills.getCooldown(skillTornado));
    }
    @Test
    @DisplayName("El maná no debe superar el máximo ni caer por debajo de cero")
    void testManaBoundsAndConsumption() {
        assertEquals(100.0f, skills.getCurrentMana());

        // Consumo exitoso
        assertTrue(skills.consumeMana(40.0f));
        assertEquals(60.0f, skills.getCurrentMana(), 0.01f);

        // Consumo excesivo rechazado
        assertFalse(skills.consumeMana(150.0f));
        assertEquals(60.0f, skills.getCurrentMana(), 0.01f);

        // Recuperación limitada al máximo
        skills.restoreMana(200.0f);
        assertEquals(skills.getMaxMana(), skills.getCurrentMana(), 0.01f);
    }

    @Test
    @DisplayName("La regeneración de maná debe escalar con un +5% por cada nivel de Magia")
    void testManaRegenScaling() {
        ResourceLocation branchMagic = new ResourceLocation("modrpg", "magic");

        // Nivel 0 de magia: 2.0 maná/segundo base
        skills.setBranchLevel(branchMagic, 0);
        assertEquals(2.0f, skills.getManaRegenPerSecond(), 0.01f);

        // Nivel 10 de magia: +50% -> 3.0 maná/segundo
        skills.setBranchLevel(branchMagic, 10);
        assertEquals(3.0f, skills.getManaRegenPerSecond(), 0.01f);

        // Nivel 100 de magia: +500% (6x base) -> 12.0 maná/segundo
        skills.setBranchLevel(branchMagic, 100);
        assertEquals(12.0f, skills.getManaRegenPerSecond(), 0.01f);
    }
}