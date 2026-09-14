package com.example.modrpg.client;

import com.example.modrpg.ModRpg;
import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketSkillActivate;
import com.example.modrpg.networking.PacketSpinAttack;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ClientEvents {

    // Registra todas las teclas del mod en Opciones -> Controles
    @Mod.EventBusSubscriber(modid = ModRpg.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModBusEvents {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(KeyBinding.SKILL_ACTIVATE_KEY);
            event.register(KeyBinding.OPEN_SKILLS_KEY);
            event.register(KeyBinding.SPIN_ATTACK_KEY);
        }
    }

    // Escucha las pulsaciones de teclas durante la partida
    @Mod.EventBusSubscriber(modid = ModRpg.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            // Tecla [R]: Activar Golpe Definitivo
            if (KeyBinding.SKILL_ACTIVATE_KEY.consumeClick()) {
                ModMessages.sendToServer(new PacketSkillActivate());
            }

            // Tecla [K]: Abrir GUI del Árbol de Habilidades
            if (KeyBinding.OPEN_SKILLS_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(new SkillTreeScreen());
            }

            // Tecla [V]: Ejecutar Ataque Giratorio
            if (KeyBinding.SPIN_ATTACK_KEY.consumeClick()) {
                ModMessages.sendToServer(new PacketSpinAttack());
            }
        }
    }
}