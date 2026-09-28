package com.example.modrpg.skills;

import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import com.example.modrpg.skills.data.SkillRequirement;
import com.example.modrpg.skills.magic.modular.CraftedSpell;
import com.example.modrpg.skills.magic.modular.SpellElement;
import com.example.modrpg.skills.magic.modular.SpellShape;
import com.example.modrpg.skills.magic.modular.SpellTiming;
import com.example.modrpg.skills.nodes.melee.VitalCleaveSkill;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSkillsTest {

    private PlayerSkills skills;
    private final ResourceLocation branchMelee = new ResourceLocation("modrpg", "melee");
    private final ResourceLocation branchMagic = new ResourceLocation("modrpg", "magic");
    private final ResourceLocation skillTornado = new ResourceLocation("modrpg", "melee_heavy_tornado");
    private final ResourceLocation stanceBerserker = new ResourceLocation("modrpg", "melee_berserker_stance");
    private final ResourceLocation counterKills = new ResourceLocation("modrpg", "melee_kills");
    private final ResourceLocation counterDistance = new ResourceLocation("modrpg", "distance_run");

    @BeforeEach
    void setUp() {
        skills = new PlayerSkills();
    }

    @Test
    @DisplayName("El nivel de rama debe limitarse entre 0 y 100")
    void testBranchLevelClamping() {
        skills.setBranchLevel(branchMelee, 150);
        assertEquals(100, skills.getBranchLevel(branchMelee));

        skills.setBranchLevel(branchMelee, -10);
        assertEquals(0, skills.getBranchLevel(branchMelee));

        skills.addBranchLevel(branchMelee, 5);
        assertEquals(5, skills.getBranchLevel(branchMelee));
    }

    @Test
    @DisplayName("Las especializaciones deben asignar correctamente los límites 20, 50 y 100 (Sprint 4)")
    void testSpecializationCapsAndRespec() {
        // Por defecto, sin especializaciones selladas, el límite es 20
        assertEquals(PlayerSkills.CAP_BASE, skills.getMaxLevelForBranch(branchMelee));
        assertEquals(PlayerSkills.CAP_BASE, skills.getMaxLevelForBranch(branchMagic));

        // Sellamos Rama Principal (CaC) y Secundaria (Magia)
        skills.setPrimaryBranch(branchMelee);
        skills.setSecondaryBranch(branchMagic);

        assertEquals(PlayerSkills.CAP_PRIMARY, skills.getMaxLevelForBranch(branchMelee)); // 100
        assertEquals(PlayerSkills.CAP_SECONDARY, skills.getMaxLevelForBranch(branchMagic)); // 50

        // Asignamos niveles superiores a 20
        skills.setBranchLevel(branchMelee, 60);
        skills.setBranchLevel(branchMagic, 35);
        assertEquals(60, skills.getBranchLevel(branchMelee));
        assertEquals(35, skills.getBranchLevel(branchMagic));

        // Ejecutar Respec: libera los votos y recorta los niveles al tope base (20)
        skills.respecSpecializations();
        assertNull(skills.getPrimaryBranch());
        assertNull(skills.getSecondaryBranch());
        assertEquals(PlayerSkills.CAP_BASE, skills.getBranchLevel(branchMelee)); // Bajó a 20
        assertEquals(PlayerSkills.CAP_BASE, skills.getBranchLevel(branchMagic)); // Bajó a 20
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
    @DisplayName("Los contadores de práctica de todas las ramas deben acumular correctamente")
    void testPracticeCounters() {
        assertEquals(0, skills.getPractice(counterKills));
        assertEquals(0, skills.getPractice(counterDistance));

        skills.addPractice(counterKills, 10);
        skills.addPractice(counterDistance, 50);

        assertEquals(10, skills.getPractice(counterKills));
        assertEquals(50, skills.getPractice(counterDistance));
    }

    @Test
    @DisplayName("Drenaje de Maná en servidor por posturas activas y auto-apagado al llegar a 0")
    void testActiveTogglesManaDrainAndAutoDeactivation() {
        SkillNode testStance = new SkillNode(
                stanceBerserker,
                branchMelee,
                Component.literal("Postura Test"),
                Component.literal("Test"),
                SkillNode.NodeType.PASSIVE_TOGGLE,
                0
        ) {}.setSustainManaCost(4.5f);
        SkillRegistry.register(testStance);

        skills.unlockNode(stanceBerserker);
        skills.setToggleActive(stanceBerserker, true);
        assertTrue(skills.isToggleActive(stanceBerserker));

        skills.setMana(2.0f);

        skills.tickServerSide();
        assertTrue(skills.getCurrentMana() < 2.0f, "El maná debe drenar mientras la postura esté activa");

        while (skills.isToggleActive(stanceBerserker)) {
            skills.tickServerSide();
        }

        assertFalse(skills.isToggleActive(stanceBerserker), "La postura debió auto-desactivarse al quedarse sin maná");
        assertTrue(skills.consumeTogglesForceDeactivated(), "La bandera de desactivación forzada debe ser true");
        assertFalse(skills.consumeTogglesForceDeactivated(), "La bandera debe limpiarse tras consumirse");
    }

    @Test
    @DisplayName("SkillRequirement debe ser 100% seguro contra NullPointerException cuando player es null")
    void testSkillRequirementAntiNpe() {
        SkillRequirement xpReq = SkillRequirement.minPlayerXpLevel(10);
        SkillRequirement costReq = SkillRequirement.consumePlayerXpLevels(5);
        SkillRequirement branchReq = SkillRequirement.branchLevel(branchMelee, 3);
        SkillRequirement practiceReq = SkillRequirement.practice(counterKills, 20, "bajas");

        assertDoesNotThrow(() -> {
            Component tooltip1 = xpReq.getTooltip(null, skills);
            assertNotNull(tooltip1);
            assertTrue(tooltip1.getString().contains("10"));

            Component tooltip2 = costReq.getTooltip(null, skills);
            assertNotNull(tooltip2);

            Component tooltip3 = branchReq.getTooltip(null, skills);
            assertNotNull(tooltip3);

            Component tooltip4 = practiceReq.getTooltip(null, skills);
            assertNotNull(tooltip4);
        });
    }

    @Test
    @DisplayName("El Tajo Vital debe escalar su cooldown según el daño y topar en 20 segundos (400 ticks)")
    void testVitalCleaveCooldownScaling() {
        assertEquals(208, VitalCleaveSkill.calculateCooldown(2.0f));
        assertEquals(240, VitalCleaveSkill.calculateCooldown(10.0f));
        assertEquals(400, VitalCleaveSkill.calculateCooldown(50.0f));
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

        assertEquals(2.45f, spell.calculateDamage(null), 0.05f);
        assertEquals(7, spell.calculateCooldownTicks());
        assertEquals(7.7f, spell.calculateManaCost(), 0.05f);

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
        assertNull(skills.getSpell(1));

        CompoundTag nbt = new CompoundTag();
        skills.saveNBTData(nbt);

        PlayerSkills loaded = new PlayerSkills();
        loaded.loadNBTData(nbt);
        assertNotNull(loaded.getSpell(0));
        assertEquals("Orbe de Fuego Pesado", loaded.getSpell(0).getName());
        assertNull(loaded.getSpell(1));
    }
}