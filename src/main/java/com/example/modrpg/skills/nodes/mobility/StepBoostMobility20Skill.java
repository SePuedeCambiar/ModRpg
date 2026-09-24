package com.example.modrpg.skills.nodes.mobility;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.SkillAttributes;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class StepBoostMobility20Skill extends SkillNode {

    public StepBoostMobility20Skill() {
        super(
                SkillRegistry.NODE_STEP_BOOST_MOBILITY_20,
                SkillRegistry.BRANCH_MOBILITY,
                Component.literal("Mejora de Paso I (+0.5 Altura)"),
                Component.literal("Entrenamiento de agilidad que otorga +0.5 bloques adicionales a tu altura de paso automática."),
                NodeType.PASSIVE_STAT,
                0
        );
    }

    @Override
    public void onUnlocked(ServerPlayer player, PlayerSkills skills) {
        SkillAttributes.applyModifiers(player);
    }
}