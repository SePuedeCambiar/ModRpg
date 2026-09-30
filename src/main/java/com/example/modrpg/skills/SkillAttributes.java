package com.example.modrpg.skills;

import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

public class SkillAttributes {

    private static final UUID MELEE_DAMAGE_UUID   = UUID.fromString("d8f5f0b8-7c8a-4d32-b8d2-123456789abc");
    private static final UUID MOBILITY_SPEED_UUID = UUID.fromString("e9a6f1c9-8d9b-5e43-c9e3-987654321fed");
    private static final UUID STEP_HEIGHT_UUID    = UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0123456789ab");

    /**
     * Aplica los modificadores nativos de atributos al jugador en el servidor.
     * SPRINT 2 FIX: El escalado de daño CaC ahora se procesa dinámicamente en LivingHurtEvent
     * para afectar al arma sostenida; aquí purgamos cualquier modificador plano residual.
     */
    public static void applyModifiers(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) return;

        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            cleanupLegacyMeleeDamage(player);
            applyMobilitySpeed(player, skills.getBranchLevel(SkillRegistry.BRANCH_MOBILITY));
            applyStepHeight(player, skills);
        });
    }

    /**
     * SPRINT 2 FIX: Limpieza del modificador plano antiguo.
     * removeModifier(UUID) en 1.20.1 es seguro: si no existe el UUID, no hace nada.
     */
    private static void cleanupLegacyMeleeDamage(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute != null) {
            attribute.removeModifier(MELEE_DAMAGE_UUID);
        }
    }

    /**
     * Aplica la bonificación progresiva de velocidad de movimiento (hasta +0.08 a nivel 100).
     */
    private static void applyMobilitySpeed(ServerPlayer player, int level) {
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;

        attribute.removeModifier(MOBILITY_SPEED_UUID);
        if (level <= 0) return;

        double bonusSpeed = SkillProgression.getMobilityBonusSpeed(level);

        attribute.addTransientModifier(new AttributeModifier(
                MOBILITY_SPEED_UUID,
                "modrpg_mobility_speed",
                bonusSpeed,
                AttributeModifier.Operation.ADDITION
        ));
    }

    /**
     * Acumulación algebraica de altura de paso automático (Step Height):
     * - LightStep: +0.5
     * - Mejora Movilidad 20: +0.5
     * - Zancada Marcial CaC 3: +0.5
     * Total posible: +1.5 bloques sobre la base de 0.6 = 2.1 bloques (sube 2 bloques directos sin saltar).
     */
    private static void applyStepHeight(ServerPlayer player, PlayerSkills skills) {
        if (ForgeMod.STEP_HEIGHT_ADDITION == null || !ForgeMod.STEP_HEIGHT_ADDITION.isPresent()) return;

        AttributeInstance attribute = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (attribute == null) return;

        attribute.removeModifier(STEP_HEIGHT_UUID);

        double totalStepAddition = 0.0;

        if (skills.isNodeUnlocked(SkillRegistry.NODE_LIGHT_STEP)) {
            totalStepAddition += 0.5;
        }
        if (skills.isNodeUnlocked(SkillRegistry.NODE_STEP_BOOST_MOBILITY_20)) {
            totalStepAddition += 0.5;
        }
        if (skills.isNodeUnlocked(SkillRegistry.NODE_STEP_BOOST_MELEE_3)) {
            totalStepAddition += 0.5;
        }

        if (totalStepAddition > 0.0) {
            attribute.addTransientModifier(new AttributeModifier(
                    STEP_HEIGHT_UUID,
                    "modrpg_step_height",
                    totalStepAddition,
                    AttributeModifier.Operation.ADDITION
            ));
        }
    }
}