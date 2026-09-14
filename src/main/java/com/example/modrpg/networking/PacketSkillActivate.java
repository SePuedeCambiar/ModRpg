package com.example.modrpg.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSkillActivate {
    public PacketSkillActivate() {}

    public static void encode(PacketSkillActivate msg, FriendlyByteBuf buffer) {
        // Paquete vacío (trigger de acción)
    }

    public static PacketSkillActivate decode(FriendlyByteBuf buffer) {
        return new PacketSkillActivate();
    }

    public static void handle(PacketSkillActivate msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Se ejecuta en el Servidor
            System.out.println(">>> [RPG] Servidor: ¡Paquete de habilidad recibido!");
        });
        ctx.get().setPacketHandled(true);
    }
}