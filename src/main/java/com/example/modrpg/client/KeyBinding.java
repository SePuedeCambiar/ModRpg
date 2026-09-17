package com.example.modrpg.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBinding {
    public static final String KEY_CATEGORY_RPG = "key.category.modrpg";
    public static final String KEY_ACTIVATE_SKILL = "key.modrpg.activate_skill";
    public static final String KEY_OPEN_SKILLS = "key.modrpg.open_skills";
    public static final String KEY_SPIN_ATTACK = "key.modrpg.spin_attack";
    public static final String KEY_MEGACUT = "key.modrpg.megacut";

    // Tecla [R]: Definitiva CaC: Ultracorte Final
    public static final KeyMapping SKILL_ACTIVATE_KEY = new KeyMapping(
            KEY_ACTIVATE_SKILL,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            KEY_CATEGORY_RPG
    );

    // Tecla [K]: Abrir Menú RPG
    public static final KeyMapping OPEN_SKILLS_KEY = new KeyMapping(
            KEY_OPEN_SKILLS,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            KEY_CATEGORY_RPG
    );

    // Tecla [V]: Torbellino Ultrapesado
    public static final KeyMapping SPIN_ATTACK_KEY = new KeyMapping(
            KEY_SPIN_ATTACK,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            KEY_CATEGORY_RPG
    );

    // Tecla [B]: Semidefinitiva: Megacorte
    public static final KeyMapping MEGACUT_KEY = new KeyMapping(
            KEY_MEGACUT,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            KEY_CATEGORY_RPG
    );
}