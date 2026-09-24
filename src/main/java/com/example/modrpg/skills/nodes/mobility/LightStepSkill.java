package com.example.modrpg.skills.nodes.mobility;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.SkillAttributes;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class LightStepSkill extends SkillNode {

    public static final double STEP_HEIGHT_BONUS = 0.5;

    public LightStepSkill() {
        super(
                SkillRegistry.NODE_LIGHT_STEP,
                SkillRegistry.BRANCH_MOBILITY,
                Component.literal("Paso Ligero (+0.5 Altura de Paso)"),
                Component.literal("Te permite subir bloques de 1 de altura suavemente sin saltar gracias al motor físico de Forge."),
                NodeType.PASSIVE_STAT,
                0
        );
    }

    @Override
    public void onUnlocked(ServerPlayer player, PlayerSkills skills) {
        // Actualiza inmediatamente los modificadores de atributos del jugador
        SkillAttributes.applyModifiers(player);
    }
}