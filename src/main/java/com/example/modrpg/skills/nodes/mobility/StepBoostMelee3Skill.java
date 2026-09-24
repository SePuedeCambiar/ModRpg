package com.example.modrpg.skills.nodes.mobility;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.SkillAttributes;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class StepBoostMelee3Skill extends SkillNode {

    public StepBoostMelee3Skill() {
        super(
                SkillRegistry.NODE_STEP_BOOST_MELEE_3,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Zancada Marcial (+0.5 Altura)"),
                Component.literal("Fuerza en las piernas ganada del combate cuerpo a cuerpo que otorga +0.5 bloques a la altura de paso."),
                NodeType.PASSIVE_STAT,
                0
        );
    }

    @Override
    public void onUnlocked(ServerPlayer player, PlayerSkills skills) {
        SkillAttributes.applyModifiers(player);
    }
}