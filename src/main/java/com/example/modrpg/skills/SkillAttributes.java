package com.example.modrpg.skills;

import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

public class SkillAttributes {

    // UUIDs fijos e inmutables para que Minecraft identifique nuestros modificadores RPG
    private static final UUID MELEE_DAMAGE_UUID   = UUID.fromString("d8f5f0b8-7c8a-4d32-b8d2-123456789abc");
    private static final UUID MOBILITY_SPEED_UUID = UUID.fromString("e9a6f1c9-8d9b-5e43-c9e3-987654321fed");

    /**
     * Aplica o actualiza todos los modificadores de atributos del jugador
     * según los niveles actuales de sus ramas RPG.
     */
    public static void applyModifiers(ServerPlayer player) {
        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            applyMeleeDamage(player, skills.getBranchLevel(SkillRegistry.BRANCH_MELEE));
            applyMobilitySpeed(player, skills.getBranchLevel(SkillRegistry.BRANCH_MOBILITY));
        });
    }

    /**
     * Aplica la curva de daño CaC:
     * - A nivel bajo (<10) el aumento es sutil y casi no se nota.
     * - A nivel 100 otorga un +200% del daño base (triplica el daño físico).
     */
    private static void applyMeleeDamage(ServerPlayer player, int level) {
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute == null) return;

        // Limpiamos siempre el modificador anterior para evitar duplicaciones
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

    /**
     * Aplica el aumento de velocidad de movimiento de forma segura para no romper
     * el campo visual (FOV) ni la cámara del jugador.
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
}