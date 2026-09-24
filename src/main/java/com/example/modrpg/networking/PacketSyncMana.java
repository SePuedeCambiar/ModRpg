package com.example.modrpg.networking;

import com.example.modrpg.skills.PlayerSkillsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncMana {

    private final float currentMana;
    private final float maxMana;

    public PacketSyncMana(float currentMana, float maxMana) {
        this.currentMana = currentMana;
        this.maxMana = maxMana;
    }

    public static void encode(PacketSyncMana msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.currentMana);
        buf.writeFloat(msg.maxMana);
    }

    public static PacketSyncMana decode(FriendlyByteBuf buf) {
        return new PacketSyncMana(buf.readFloat(), buf.readFloat());
    }

    public static void handle(PacketSyncMana msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Player player = Minecraft.getInstance().player;
                if (player != null) {
                    player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                        skills.setMana(msg.currentMana);
                    });
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}