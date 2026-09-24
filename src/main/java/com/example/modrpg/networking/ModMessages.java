package com.example.modrpg.networking;

import com.example.modrpg.ModRpg;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {

    private static SimpleChannel INSTANCE;
    private static final String PROTOCOL_VERSION = "4";
    public static final ResourceLocation NETWORK_ID = new ResourceLocation(ModRpg.MODID, "main");

    public static void register() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                NETWORK_ID,
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        INSTANCE.registerMessage(0, PacketCastSkill.class, PacketCastSkill::encode, PacketCastSkill::decode, PacketCastSkill::handle);
        INSTANCE.registerMessage(1, PacketSyncSkillsToClient.class, PacketSyncSkillsToClient::encode, PacketSyncSkillsToClient::decode, PacketSyncSkillsToClient::handle);
        INSTANCE.registerMessage(2, PacketUpgradeSkill.class, PacketUpgradeSkill::encode, PacketUpgradeSkill::decode, PacketUpgradeSkill::handle);
        INSTANCE.registerMessage(3, PacketUnlockNode.class, PacketUnlockNode::encode, PacketUnlockNode::decode, PacketUnlockNode::handle);
        INSTANCE.registerMessage(4, PacketSyncMana.class, PacketSyncMana::encode, PacketSyncMana::decode, PacketSyncMana::handle);
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}