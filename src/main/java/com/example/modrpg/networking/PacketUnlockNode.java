package com.example.modrpg.networking;

import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillEconomy;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUnlockNode {

    private final ResourceLocation nodeId;

    public PacketUnlockNode(ResourceLocation nodeId) {
        this.nodeId = nodeId;
    }

    public static void encode(PacketUnlockNode msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.nodeId);
    }

    public static PacketUnlockNode decode(FriendlyByteBuf buf) {
        return new PacketUnlockNode(buf.readResourceLocation());
    }

    public static void handle(PacketUnlockNode msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                SkillNode node = SkillRegistry.get(msg.nodeId);
                if (node == null) return;

                if (skills.isNodeUnlocked(node.getId())) {
                    player.sendSystemMessage(Component.literal("§e[RPG] Ya tienes desbloqueada esta habilidad."));
                    return;
                }

                if (node.tryUnlock(player, skills)) {
                    player.sendSystemMessage(Component.literal("§a§l✔ [RPG] ¡Has desbloqueado: §e" + node.getDisplayName().getString() + "§a!"));
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
                    SkillEconomy.syncSkills(player);
                } else {
                    player.sendSystemMessage(Component.literal("§c🔒 [RPG] No cumples con los requisitos para desbloquear: §f" + node.getDisplayName().getString()));
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
