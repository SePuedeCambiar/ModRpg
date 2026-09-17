package com.example.modrpg.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBinding {
    public static final String KEY_CATEGORY_RPG = "key.category.modrpg";

    public static final KeyMapping RADIAL_MENU_KEY    = new KeyMapping("key.modrpg.radial_menu", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, KEY_CATEGORY_RPG);
    public static final KeyMapping OPEN_SKILLS_KEY    = new KeyMapping("key.modrpg.open_skills", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KEY_CATEGORY_RPG);
    public static final KeyMapping SKILL_ACTIVATE_KEY = new KeyMapping("key.modrpg.ultracut", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, KEY_CATEGORY_RPG);
    public static final KeyMapping SPIN_ATTACK_KEY    = new KeyMapping("key.modrpg.spin_attack", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, KEY_CATEGORY_RPG);
    public static final KeyMapping MEGACUT_KEY        = new KeyMapping("key.modrpg.megacut", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, KEY_CATEGORY_RPG);
    public static final KeyMapping DASH_KEY           = new KeyMapping("key.modrpg.dash", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, KEY_CATEGORY_RPG);
    public static final KeyMapping FIREBALL_KEY       = new KeyMapping("key.modrpg.fireball", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, KEY_CATEGORY_RPG);
    public static final KeyMapping HEAL_KEY           = new KeyMapping("key.modrpg.heal", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, KEY_CATEGORY_RPG);
}