package com.example.modrpg.skills.nodes.mobility;

import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.network.chat.Component;

public class LightStepSkill extends SkillNode {

    public LightStepSkill() {
        super(
                SkillRegistry.NODE_LIGHT_STEP,
                SkillRegistry.BRANCH_MOBILITY,
                Component.literal("Paso Ligero (+0.5 Altura de Paso)"),
                Component.literal("Te permite subir bloques completos de 1 de altura suavemente sin tener que saltar."),
                NodeType.PASSIVE_STAT,
                0
        );
    }
}
