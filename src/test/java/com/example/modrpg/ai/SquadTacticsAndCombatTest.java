package com.example.modrpg.ai;

import com.example.modrpg.ai.goals.director.AmbushAssaultGoal;
import com.example.modrpg.ai.squad.CoverNode;
import com.example.modrpg.ai.squad.SquadCoverManager;
import com.example.modrpg.skills.magic.modular.CraftedSpell;
import com.example.modrpg.skills.magic.modular.SpellElement;
import com.example.modrpg.skills.magic.modular.SpellShape;
import com.example.modrpg.skills.magic.modular.SpellTiming;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SquadTacticsAndCombatTest {

    @Test
    @DisplayName("B5: TacticalFlankGoal solo debe asignarse al arquetipo VOID_WEAVER y no a casters/snipers")
    void testFlankGoalMustBeRestrictedToVoidWeaver() {
        // En el código actual de EnemyRpgManager, todo mob no-rush recibe TacticalFlankGoal (prioridad 5).
        // Debe existir un método selector que restrinja el flanqueo exclusivamente a VOID_WEAVER.
        boolean hasRestrictedFlankMethod = false;
        boolean sniperFlanks = true;
        boolean weaverFlanks = false;

        try {
            Method m = EnemyRpgManager.class.getMethod("shouldEquipFlankGoal", EnemyArchetype.class);
            hasRestrictedFlankMethod = true;
            sniperFlanks = (boolean) m.invoke(null, EnemyArchetype.CRYO_SNIPER);
            weaverFlanks = (boolean) m.invoke(null, EnemyArchetype.VOID_WEAVER);
        } catch (ReflectiveOperationException ignored) {}

        assertTrue(
                hasRestrictedFlankMethod && !sniperFlanks && weaverFlanks,
                "FALLO (B5): EnemyRpgManager debe restringir TacticalFlankGoal exclusivamente a VOID_WEAVER. " +
                        "Actualmente todos los arqueros y magos reciben la meta de flanqueo cuerpo a cuerpo (Prioridad 5), " +
                        "impidiendo que ataquen a distancia con TacticalCasterGoal (Prioridad 6)."
        );
    }

    @Test
    @DisplayName("B6: TacticalCasterGoal.start() debe preservar cooldowns de habilidades largas en lugar de resetearlos a 60")
    void testCasterGoalMustPreserveSkillCooldownsOnStart() throws Exception {
        CraftedSpell dummySpell = new CraftedSpell("Test", SpellElement.FIRE, SpellShape.PROJECTILE, SpellTiming.BALANCED, 1);
        TacticalCasterGoal goal = new TacticalCasterGoal(null, EnemyArchetype.FLAME_JUGGERNAUT, dummySpell);

        Field skillCdField = TacticalCasterGoal.class.getDeclaredField("specialSkillCooldownTicks");
        skillCdField.setAccessible(true);

        // Simulamos que el mob acaba de invocar o curarse (cooldown de 300 ticks = 15s)
        skillCdField.set(goal, 300);

        // Al moverse o reanudarse la meta:
        goal.start();

        // En el código actual, start() sobrescribe incondicionalmente: this.specialSkillCooldownTicks = 60;
        int cdAfterStart = skillCdField.getInt(goal);

        assertEquals(
                300, cdAfterStart,
                "FALLO (B6): TacticalCasterGoal.start() resetea specialSkillCooldownTicks a 60 ticks (3s). " +
                        "Cada vez que un Nigromante o Evoker se mueve o reanuda combate, se le borra el cooldown largo de invocación/curación."
        );
    }

    @Test
    @DisplayName("B2: CraftedSpell debe incluir filtro de fuego amigo para que mobs hostiles no se dañen entre sí")
    void testHostileFriendlyFireProtection() {
        // En el código actual, CraftedSpell y proyectiles usan !e.isAlliedTo(caster).
        // En Minecraft vanilla los monstruos hostiles no tienen equipo y se queman y matan entre sí.
        boolean hasFriendlyFireFilter = false;
        try {
            Method m = CraftedSpell.class.getMethod("canHarmTarget", LivingEntity.class, LivingEntity.class);
            hasFriendlyFireFilter = true;
        } catch (ReflectiveOperationException ignored) {}

        assertTrue(
                hasFriendlyFireFilter,
                "FALLO (B2): CraftedSpell no cuenta con un método unificado canHarmTarget(caster, target). " +
                        "Actualmente usa !e.isAlliedTo(caster), provocando que el aura de fuego del Juggernaut incinere " +
                        "a sus propios aliados y el rayo del Weaver perfore al tanque frontal de su escuadrón."
        );
    }

    @Test
    @DisplayName("B8: SquadCoverManager no debe purgar TODOS los nodos disponibles al sobrepasar 12, sino solo el exceso")
    void testCoverNodePurgeMustNotWipeAllNodes() throws Exception {
        SquadCoverManager coverManager = new SquadCoverManager();
        Field nodesField = SquadCoverManager.class.getDeclaredField("knownCoverNodes");
        nodesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<BlockPos, CoverNode> knownCoverNodes = (Map<BlockPos, CoverNode>) nodesField.get(coverManager);

        // Poblamos con 14 nodos de cobertura disponibles
        for (int i = 0; i < 14; i++) {
            BlockPos pos = new BlockPos(i, 64, 0);
            knownCoverNodes.put(pos, new CoverNode(pos));
        }

        // Debe existir un método controlado para podar únicamente los nodos sobrantes (pruneExcessNodes)
        boolean hasPruneMethod = false;
        try {
            Method m = SquadCoverManager.class.getMethod("pruneExcessNodes");
            hasPruneMethod = true;
            m.invoke(coverManager);
            // Debe dejar la lista acotada a un máximo de 12, pero nunca vaciarla a 0
            assertTrue(knownCoverNodes.size() > 0 && knownCoverNodes.size() <= 12);
        } catch (ReflectiveOperationException ignored) {}

        assertTrue(
                hasPruneMethod,
                "FALLO (B8): SquadCoverManager no implementa una purga controlada de exceso de nodos (pruneExcessNodes). " +
                        "Actualmente en la línea 174 ejecuta removeIf(isAvailable), lo que borra todos los nodos de golpe a 0."
        );
    }

    @Test
    @DisplayName("B4: AmbushAssaultGoal debe tener un campo de cooldown de ataque para no golpear a 20 Hz")
    void testAmbushGoalMustHaveAttackCooldown() {
        boolean hasAttackCooldownField = false;
        for (Field field : AmbushAssaultGoal.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            if (name.contains("attackcooldown") || name.contains("attackdelay") || name.contains("hurtdelay")) {
                hasAttackCooldownField = true;
                break;
            }
        }

        assertTrue(
                hasAttackCooldownField,
                "FALLO CRÍTICO (B4): AmbushAssaultGoal no tiene un campo de cooldown de ataque (ej. attackCooldownTicks). " +
                        "Actualmente en AMBUSH_READY/CLIMAX llama a doHurtTarget() en cada tick (20 veces/s) limitado únicamente " +
                        "por los i-frames de la víctima, sin mover los brazos ni verificar línea de visión."
        );
    }

    @Test
    @DisplayName("B7: CraftedSpell debe calcular el centro de GROUND_AOE sobre el objetivo cuando el lanzador tiene target")
    void testGroundAoeTargetCentering() {
        boolean hasTargetAoeHelper = false;
        try {
            Method m = CraftedSpell.class.getMethod("calculateGroundAoeCenter", LivingEntity.class, Vec3.class);
            hasTargetAoeHelper = true;
        } catch (ReflectiveOperationException ignored) {}

        assertTrue(
                hasTargetAoeHelper,
                "FALLO (B7): CraftedSpell no calcula el centro de GROUND_AOE dinámicamente según el objetivo. " +
                        "Actualmente hardcodea caster.position() + look * 5, provocando que los Nigromantes a 12m " +
                        "detonen la runa abisal en el suelo a 5m de ellos sin tocar al jugador."
        );
    }
}