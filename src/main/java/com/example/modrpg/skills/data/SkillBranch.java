package com.example.modrpg.skills.data;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record SkillBranch(
        ResourceLocation id,
        Component displayName,
        int minPlayerXpToUnlock,
        ResourceLocation practiceCounterId
) {}