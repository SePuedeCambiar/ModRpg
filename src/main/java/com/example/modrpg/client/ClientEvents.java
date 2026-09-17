package com.example.modrpg.client;

import com.example.modrpg.ModRpg;
import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketCastSkill;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ClientEvents {

    @Mod.EventBusSubscriber(modid = ModRpg.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModBusEvents {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(KeyBinding.SKILL_ACTIVATE_KEY);
            event.register(KeyBinding.OPEN_SKILLS_KEY);
            event.register(KeyBinding.SPIN_ATTACK_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = ModRpg.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            // Tecla [R]: Golpe Definitivo CaC
            if (KeyBinding.SKILL_ACTIVATE_KEY.consumeClick()) {
                ModMessages.sendToServer(new PacketCastSkill(SkillRegistry.NODE_CAPSTONE_MELEE));
            }

            // Tecla [K]: Abrir Menú del Árbol de Habilidades
            if (KeyBinding.OPEN_SKILLS_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(new SkillTreeScreen());
            }

            // Tecla [V]: Ataque Giratorio 360° (Torbellino)
            if (KeyBinding.SPIN_ATTACK_KEY.consumeClick()) {
                ModMessages.sendToServer(new PacketCastSkill(SkillRegistry.NODE_SPIN_ATTACK));
            }
        }
    }
}