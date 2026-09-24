package com.example.modrpg.skills;

import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

public class SkillAttributes {

    // UUIDs fijas e inmutables para identificadores de atributos RPG
    private static final UUID MELEE_DAMAGE_UUID   = UUID.fromString("d8f5f0b8-7c8a-4d32-b8d2-123456789abc");
    private static final UUID MOBILITY_SPEED_UUID = UUID.fromString("e9a6f1c9-8d9b-5e43-c9e3-987654321fed");
    private static final UUID STEP_HEIGHT_UUID    = UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0123456789ab");

    /**
     * Aplica o actualiza todos los modificadores de atributos del jugador.
     */
    public static void applyModifiers(ServerPlayer player) {
        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            applyMeleeDamage(player, skills.getBranchLevel(SkillRegistry.BRANCH_MELEE));
            applyMobilitySpeed(player, skills.getBranchLevel(SkillRegistry.BRANCH_MOBILITY));
            applyStepHeight(player, skills);
        });
    }

    private static void applyMeleeDamage(ServerPlayer player, int level) {
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute == null) return;

        attribute.removeModifier(MELEE_DAMAGE_UUID);
        if (level <= 0) return;

        double baseAttack = attribute.getBaseValue();
        double bonusDamage = SkillProgression.getMeleeBonusDamage(baseAttack, level);

        attribute.addTransientModifier(new AttributeModifier(
                MELEE_DAMAGE_UUID,
                "modrpg_melee_damage",
                bonusDamage,
                AttributeModifier.Operation.ADDITION
        ));
    }

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
     * Aplica el aumento de altura de paso mediante el atributo de Forge 1.20.1.
     * Acumula el nodo Paso Ligero (+0.5 bloques) y futuras mejoras de paso.
     */
    private static void applyStepHeight(ServerPlayer player, PlayerSkills skills) {
        AttributeInstance attribute = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (attribute == null) return;

        attribute.removeModifier(STEP_HEIGHT_UUID);

        double totalStepAddition = 0.0;

        // Si tiene desbloqueado Paso Ligero (+0.5)
        if (skills.isNodeUnlocked(SkillRegistry.NODE_LIGHT_STEP)) {
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