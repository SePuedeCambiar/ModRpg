package com.example.modrpg.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBinding {
    public static final String KEY_CATEGORY_RPG = "key.category.modrpg";
    public static final String KEY_ACTIVATE_SKILL = "key.modrpg.activate_skill";
    public static final String KEY_OPEN_SKILLS = "key.modrpg.open_skills";

    // Tecla [R] para activar el Capstone
    public static final KeyMapping SKILL_ACTIVATE_KEY = new KeyMapping(
            KEY_ACTIVATE_SKILL,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            KEY_CATEGORY_RPG
    );

    // Tecla [K] para abrir el Árbol de Habilidades
    public static final KeyMapping OPEN_SKILLS_KEY = new KeyMapping(
            KEY_OPEN_SKILLS,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            KEY_CATEGORY_RPG
    );
}