package com.example.modrpg.networking;

import com.example.modrpg.ModRpg;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {

    private static SimpleChannel INSTANCE;
    private static final String PROTOCOL_VERSION = "3";
    public static final ResourceLocation NETWORK_ID = new ResourceLocation(ModRpg.MODID, "main");

    public static void register() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                NETWORK_ID,
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        // ID 0: Casteo de habilidad activa con tecla
        INSTANCE.registerMessage(
                0,
                PacketCastSkill.class,
                PacketCastSkill::encode,
                PacketCastSkill::decode,
                PacketCastSkill::handle
        );

        // ID 1: Sincronización completa de datos
        INSTANCE.registerMessage(
                1,
                PacketSyncSkillsToClient.class,
                PacketSyncSkillsToClient::encode,
                PacketSyncSkillsToClient::decode,
                PacketSyncSkillsToClient::handle
        );

        // ID 2: Subir nivel de rama con botón
        INSTANCE.registerMessage(
                2,
                PacketUpgradeSkill.class,
                PacketUpgradeSkill::encode,
                PacketUpgradeSkill::decode,
                PacketUpgradeSkill::handle
        );

        // ID 3: Comprar / Desbloquear nodo desde el árbol
        INSTANCE.registerMessage(
                3,
                PacketUnlockNode.class,
                PacketUnlockNode::encode,
                PacketUnlockNode::decode,
                PacketUnlockNode::handle
        );
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}