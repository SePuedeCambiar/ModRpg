package com.example.modrpg.networking;

import com.example.modrpg.skills.SkillEconomy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUpgradeSkill {
    private final String branch;

    public PacketUpgradeSkill(String branch) {
        this.branch = branch;
    }

    public static void encode(PacketUpgradeSkill msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.branch);
    }

    public static PacketUpgradeSkill decode(FriendlyByteBuf buf) {
        return new PacketUpgradeSkill(buf.readUtf());
    }

    public static void handle(PacketUpgradeSkill msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // Ejecuta la compra legítima desde el servidor
                SkillEconomy.upgradeBranch(player, msg.branch);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}