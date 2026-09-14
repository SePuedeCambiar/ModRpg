package com.example.modrpg.networking;

import com.example.modrpg.ModRpg;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {
    private static SimpleChannel INSTANCE;

    private static final String PROTOCOL_VERSION = "1";
    public static final ResourceLocation NETWORK_ID = new ResourceLocation(ModRpg.MODID, "main");

    public static void register() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                NETWORK_ID,
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        // ID 0: Activar Capstone (Tecla R)
        INSTANCE.registerMessage(
                0,
                PacketSkillActivate.class,
                PacketSkillActivate::encode,
                PacketSkillActivate::decode,
                PacketSkillActivate::handle
        );

        // ID 1: Sincronizar datos al Cliente (Servidor -> Cliente)
        INSTANCE.registerMessage(
                1,
                PacketSyncSkillsToClient.class,
                PacketSyncSkillsToClient::encode,
                PacketSyncSkillsToClient::decode,
                PacketSyncSkillsToClient::handle
        );

        // ID 2: Solicitar mejora de rama desde botón GUI (Cliente -> Servidor)
        INSTANCE.registerMessage(
                2,
                PacketUpgradeSkill.class,
                PacketUpgradeSkill::encode,
                PacketUpgradeSkill::decode,
                PacketUpgradeSkill::handle
        );
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}