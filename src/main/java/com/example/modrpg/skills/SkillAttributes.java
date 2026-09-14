package com.example.modrpg.skills;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

public class SkillAttributes {

    // UUIDs únicos para identificar nuestros modificadores en Minecraft
    private static final UUID MELEE_DAMAGE_UUID = UUID.fromString("d8f5f0b8-7c8a-4d32-b8d2-123456789abc");
    private static final UUID MOBILITY_SPEED_UUID = UUID.fromString("e9a6f1c9-8d9b-5e43-c9e3-987654321fed");

    /**
     * Aplica los modificadores según el nivel actual del jugador
     */
    public static void applyModifiers(ServerPlayer player) {
        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            applyMeleeDamage(player, skills.getMeleeLevel());
            applyMobilitySpeed(player, skills.getMobilityLevel());
        });
    }

    // --- ESCALADO DE DAÑO CUERPO A CUERPO ---
    private static void applyMeleeDamage(ServerPlayer player, int level) {
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute == null) return;

        // Quitamos el modificador anterior para actualizarlo con el nuevo valor
        attribute.removeModifier(MELEE_DAMAGE_UUID);

        if (level <= 0) return;

        // FÓRMULA MATEMÁTICA:
        // A nivel bajo sube decimales imperceptibles.
        // A nivel 100 da aproximadamente +35.0 de daño adicional (Modo Dios).
        double bonusDamage = Math.pow((double) level / 100.0, 1.8) * 35.0;

        AttributeModifier modifier = new AttributeModifier(
                MELEE_DAMAGE_UUID,
                "modrpg_melee_damage",
                bonusDamage,
                AttributeModifier.Operation.ADDITION
        );

        attribute.addTransientModifier(modifier);
    }

    // --- ESCALADO DE MOVILIDAD ---
    private static void applyMobilitySpeed(ServerPlayer player, int level) {
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;

        attribute.removeModifier(MOBILITY_SPEED_UUID);

        if (level <= 0) return;

        // A nivel 100 te da un +80% de velocidad de movimiento
        double bonusSpeed = Math.pow((double) level / 100.0, 1.5) * 0.08;

        AttributeModifier modifier = new AttributeModifier(
                MOBILITY_SPEED_UUID,
                "modrpg_mobility_speed",
                bonusSpeed,
                AttributeModifier.Operation.ADDITION
        );

        attribute.addTransientModifier(modifier);
    }
}