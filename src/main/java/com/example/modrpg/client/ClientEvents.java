package com.example.modrpg.client;

import com.example.modrpg.ModRpg;
import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketCastSkill;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ClientEvents {

    @Mod.EventBusSubscriber(modid = ModRpg.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModBusEvents {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(KeyBinding.RADIAL_MENU_KEY);
            event.register(KeyBinding.OPEN_SKILLS_KEY);
            event.register(KeyBinding.SKILL_ACTIVATE_KEY);
            event.register(KeyBinding.SPIN_ATTACK_KEY);
            event.register(KeyBinding.MEGACUT_KEY);
            event.register(KeyBinding.DASH_KEY);
            event.register(KeyBinding.FIREBALL_KEY);
            event.register(KeyBinding.HEAL_KEY);
        }

        // Registro del HUD de Cooldowns encima del inventario
        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "skill_cooldowns", SkillCooldownOverlay.HUD_SKILLS);
        }
    }

    @Mod.EventBusSubscriber(modid = ModRpg.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            // [Z] Abrir Rueda Radial de Habilidades
            if (KeyBinding.RADIAL_MENU_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(new RadialMenuScreen());
            }

            // [K] Menú del Árbol de Habilidades
            if (KeyBinding.OPEN_SKILLS_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(new SkillTreeScreen());
            }

            // Atajos directos opcionales
            if (KeyBinding.SKILL_ACTIVATE_KEY.consumeClick()) ModMessages.sendToServer(new PacketCastSkill(SkillRegistry.NODE_ULTRACUT));
            if (KeyBinding.SPIN_ATTACK_KEY.consumeClick())    ModMessages.sendToServer(new PacketCastSkill(SkillRegistry.NODE_HEAVY_TORNADO));
            if (KeyBinding.MEGACUT_KEY.consumeClick())        ModMessages.sendToServer(new PacketCastSkill(SkillRegistry.NODE_MEGACUT));
            if (KeyBinding.DASH_KEY.consumeClick())           ModMessages.sendToServer(new PacketCastSkill(SkillRegistry.NODE_DASH));
            if (KeyBinding.FIREBALL_KEY.consumeClick())       ModMessages.sendToServer(new PacketCastSkill(SkillRegistry.NODE_FIREBALL));
            if (KeyBinding.HEAL_KEY.consumeClick())           ModMessages.sendToServer(new PacketCastSkill(SkillRegistry.NODE_HEALING_AURA));
        }
    }
    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "skill_cooldowns", SkillCooldownOverlay.HUD_SKILLS);
        event.registerAbove(VanillaGuiOverlay.PLAYER_HEALTH.id(), "mana_overlay", ManaOverlay.HUD_MANA);
    }
}