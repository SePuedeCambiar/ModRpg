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
        skills.setCooldown(skillTornado, 3); // 3 ticks
        assertTrue(skills.hasCooldown(skillTornado));

        skills.tickCooldowns(); // Pasa a 2
        assertEquals(2, skills.getCooldown(skillTornado));

        skills.tickCooldowns(); // Pasa a 1
        assertEquals(1, skills.getCooldown(skillTornado));

        skills.tickCooldowns(); // Llega a 0 -> Debe borrarse del mapa
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
}