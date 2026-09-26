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

public class PacketTogglePassive {

    private final ResourceLocation skillId;

    public PacketTogglePassive(ResourceLocation skillId) {
        this.skillId = skillId;
    }

    public static void encode(PacketTogglePassive msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.skillId);
    }

    public static PacketTogglePassive decode(FriendlyByteBuf buf) {
        return new PacketTogglePassive(buf.readResourceLocation());
    }

    public static void handle(PacketTogglePassive msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                SkillNode node = SkillRegistry.get(msg.skillId);
                if (node == null || !skills.isNodeUnlocked(node.getId())) return;
                if (node.getType() != SkillNode.NodeType.PASSIVE_TOGGLE) return;

                boolean newState = skills.toggleState(node.getId());
                node.onToggleChanged(player, skills, newState);

                if (newState) {
                    player.displayClientMessage(Component.literal("§a✔ Postura activada: §f§l" + node.getDisplayName().getString() + " §2[ON]"), true);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.8f);
                } else {
                    player.displayClientMessage(Component.literal("§c✖ Postura desactivada: §f§l" + node.getDisplayName().getString() + " §4[OFF]"), true);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7f, 1.4f);
                }

                SkillEconomy.syncSkills(player);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}