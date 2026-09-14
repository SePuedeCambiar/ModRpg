package com.example.modrpg.client;

import com.example.modrpg.ModRpg;
import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketSkillActivate;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ClientEvents {

    // 1. Registra la tecla en el bus del Mod para que aparezca en Opciones -> Controles
    @Mod.EventBusSubscriber(modid = ModRpg.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModBusEvents {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(KeyBinding.SKILL_ACTIVATE_KEY);
        }
    }

    // 2. Escucha las pulsaciones en el bus del Juego (Forge)
    @Mod.EventBusSubscriber(modid = ModRpg.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            // consumeClick() devuelve true una sola vez por pulsación
            if (KeyBinding.SKILL_ACTIVATE_KEY.consumeClick()) {
                // Enviamos la señal al servidor
                ModMessages.sendToServer(new PacketSkillActivate());
            }
        }
    }
}