package com.example.modrpg.skills;

import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

public class SkillAttributes {

    private static final UUID MELEE_DAMAGE_UUID = UUID.fromString("d8f5f0b8-7c8a-4d32-b8d2-123456789abc");
    private static final UUID MOBILITY_SPEED_UUID = UUID.fromString("e9a6f1c9-8d9b-5e43-c9e3-987654321fed");

    public static void applyModifiers(ServerPlayer player) {
        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            applyMeleeDamage(player, skills.getBranchLevel(SkillRegistry.BRANCH_MELEE));
            applyMobilitySpeed(player, skills.getBranchLevel(SkillRegistry.BRANCH_MOBILITY));
        });
    }

    private static void applyMeleeDamage(ServerPlayer player, int level) {
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute == null) return;

        attribute.removeModifier(MELEE_DAMAGE_UUID);
        if (level <= 0) return;

        // A nivel 100 da +35.0 de daño adicional
        double bonusDamage = Math.pow((double) level / 100.0, 1.8) * 35.0;
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

        // A nivel 100 da +80% de velocidad
        double bonusSpeed = Math.pow((double) level / 100.0, 1.5) * 0.08;
        attribute.addTransientModifier(new AttributeModifier(
                MOBILITY_SPEED_UUID,
                "modrpg_mobility_speed",
                bonusSpeed,
                AttributeModifier.Operation.ADDITION
        ));
    }
}