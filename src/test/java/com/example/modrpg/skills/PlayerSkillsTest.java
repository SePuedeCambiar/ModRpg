package com.example.modrpg.skills;

import com.example.modrpg.skills.magic.modular.CraftedSpell;
import com.example.modrpg.skills.magic.modular.SpellElement;
import com.example.modrpg.skills.magic.modular.SpellShape;
import com.example.modrpg.skills.magic.modular.SpellTiming;
import com.example.modrpg.skills.nodes.melee.VitalCleaveSkill;
import net.minecraft.nbt.CompoundTag;
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
    @DisplayName("Simulación de sincronización completa y decremento de cooldown en el cliente")
    void testClientCooldownSyncAndTick() {
        // 1. Servidor aplica cooldown de 60 ticks, selecciona habilidad y guarda un hechizo
        PlayerSkills serverSkills = new PlayerSkills();
        serverSkills.unlockNode(skillTornado);
        serverSkills.setCooldown(skillTornado, 60);
        serverSkills.setSelectedSkill(skillTornado);

        CraftedSpell spell = new CraftedSpell(
                "Rayo Veloz",
                SpellElement.LIGHTNING,
                SpellShape.PROJECTILE,
                SpellTiming.RAPID_FIRE,
                1
        );
        serverSkills.setSpell(0, spell);

        assertTrue(serverSkills.hasCooldown(skillTornado));

        // 2. Cliente recibe los datos completos sincronizados (11 argumentos)
        PlayerSkills clientSkills = new PlayerSkills();
        clientSkills.replaceAll(
                serverSkills.getAllBranchLevels(),
                serverSkills.getUnlockedNodes(),
                serverSkills.getAllPracticeCounters(),
                serverSkills.getAllCooldowns(),
                serverSkills.isUltimateCharged(),
                serverSkills.getEquippedSkills(),
                serverSkills.getCurrentMana(),
                serverSkills.getMaxMana(),
                serverSkills.getSelectedSkill(),
                serverSkills.getActiveToggles(),
                serverSkills.getAllSpells()
        );

        assertTrue(clientSkills.hasCooldown(skillTornado));
        assertEquals(60, clientSkills.getCooldown(skillTornado));
        assertEquals(skillTornado, clientSkills.getSelectedSkill(), "La habilidad seleccionada debe llegar al cliente");
        assertNotNull(clientSkills.getSpell(0), "El hechizo guardado en la memoria debe sincronizarse al cliente");
        assertEquals("Rayo Veloz", clientSkills.getSpell(0).getName());

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

    @Test
    @DisplayName("La selección de habilidad debe auto-asignar la primera equipada si está vacía")
    void testSelectedSkillAutoFallback() {
        ResourceLocation skillA = new ResourceLocation("modrpg", "skill_a");
        skills.unlockNode(skillA);

        // Al desbloquear, se equipa y se vuelve la seleccionada automáticamente
        assertEquals(skillA, skills.getSelectedSkill());

        // Si se desequipa, debe volver a null
        skills.unequipSkill(0);
        assertNull(skills.getSelectedSkill());
    }

    @Test
    @DisplayName("Las pasivas conmutables (Toggle) deben alternar estado correctamente")
    void testTogglePassiveBehavior() {
        ResourceLocation stanceId = new ResourceLocation("modrpg", "melee_berserker_stance");

        // No se puede activar si no está desbloqueada
        assertFalse(skills.toggleState(stanceId));
        assertFalse(skills.isToggleActive(stanceId));

        // Al desbloquearla, podemos alternar ON / OFF
        skills.unlockNode(stanceId);
        assertTrue(skills.toggleState(stanceId)); // Pasa a ON
        assertTrue(skills.isToggleActive(stanceId));

        assertFalse(skills.toggleState(stanceId)); // Pasa a OFF
        assertFalse(skills.isToggleActive(stanceId));
    }

    @Test
    @DisplayName("El Tajo Vital debe escalar su cooldown según el daño y topar en 20 segundos (400 ticks)")
    void testVitalCleaveCooldownScaling() {
        // Daño bajo (Zombie: 2.0 de daño) -> 200 + (2 * 4) = 208 ticks (10.4s)
        assertEquals(208, VitalCleaveSkill.calculateCooldown(2.0f));

        // Daño medio (Golem: 10.0 de daño) -> 200 + (10 * 4) = 240 ticks (12.0s)
        assertEquals(240, VitalCleaveSkill.calculateCooldown(10.0f));

        // Daño alto (Warden: 50.0 de daño) -> 200 + (50 * 4) = 400 ticks (20.0s exactos)
        assertEquals(400, VitalCleaveSkill.calculateCooldown(50.0f));

        // Daño masivo (Jefe modded de 1000 PV -> 100.0 de daño) -> Debe topar en 400 ticks (20.0s)
        assertEquals(400, VitalCleaveSkill.calculateCooldown(100.0f));
    }

    @Test
    @DisplayName("El Motor de Magia Modular debe calcular fórmulas coherentes y serializar NBT")
    void testModularSpellFormulasAndNBT() {
        CraftedSpell spell = new CraftedSpell(
                "Metralleta de Rayo",
                SpellElement.LIGHTNING,
                SpellShape.PROJECTILE,
                SpellTiming.RAPID_FIRE,
                1
        );

        // Fórmulas matemáticas:
        // Daño: 7.0 * 1.0 (Projectile) * 0.35 (Rapid) = 2.45
        assertEquals(2.45f, spell.calculateDamage(null), 0.05f);

        // Cooldown: 50 * 1.0 * 0.15 = 7.5 -> 7 ticks (~0.35 segundos)
        assertEquals(7, spell.calculateCooldownTicks());

        // Coste de maná: 22.0 * 1.0 * 0.35 = 7.7 maná por disparo
        assertEquals(7.7f, spell.calculateManaCost(), 0.05f);

        // Serialización y reconstrucción en NBT
        CompoundTag nbt = spell.toNBT();
        CraftedSpell reconstructed = CraftedSpell.fromNBT(nbt);

        assertEquals("Metralleta de Rayo", reconstructed.getName());
        assertEquals(SpellElement.LIGHTNING, reconstructed.getElement());
        assertEquals(SpellShape.PROJECTILE, reconstructed.getShape());
        assertEquals(SpellTiming.RAPID_FIRE, reconstructed.getTiming());
        assertEquals(1, reconstructed.getPowerLevel());
    }

    @Test
    @DisplayName("La memoria de hechizos debe persistir y sincronizar las 4 ranuras")
    void testSpellMemoryStorageAndSync() {
        CraftedSpell spell = new CraftedSpell(
                "Orbe de Fuego Pesado",
                SpellElement.FIRE,
                SpellShape.PROJECTILE,
                SpellTiming.HEAVY_BURST,
                1
        );

        skills.setSpell(0, spell);
        assertNotNull(skills.getSpell(0));
        assertEquals("Orbe de Fuego Pesado", skills.getSpell(0).getName());
        assertNull(skills.getSpell(1)); // Ranura 2 vacía

        // Test de persistencia NBT
        CompoundTag nbt = new CompoundTag();
        skills.saveNBTData(nbt);

        PlayerSkills loaded = new PlayerSkills();
        loaded.loadNBTData(nbt);
        assertNotNull(loaded.getSpell(0));
        assertEquals("Orbe de Fuego Pesado", loaded.getSpell(0).getName());
        assertNull(loaded.getSpell(1));
    }
}