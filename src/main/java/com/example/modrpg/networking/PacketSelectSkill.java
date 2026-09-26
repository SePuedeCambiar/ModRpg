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

public class PacketSelectSkill {

    private final ResourceLocation skillId;

    public PacketSelectSkill(ResourceLocation skillId) {
        this.skillId = skillId;
    }

    public static void encode(PacketSelectSkill msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.skillId);
    }

    public static PacketSelectSkill decode(FriendlyByteBuf buf) {
        return new PacketSelectSkill(buf.readResourceLocation());
    }

    public static void handle(PacketSelectSkill msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                SkillNode node = SkillRegistry.get(msg.skillId);
                if (node == null || !skills.isNodeUnlocked(node.getId())) return;

                skills.setSelectedSkill(node.getId());
                player.displayClientMessage(Component.literal("§a✔ Habilidad activa: §f§l" + node.getDisplayName().getString() + " §7(Usa [R] para ejecutar)"), true);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.PLAYERS, 0.7f, 1.6f);

                SkillEconomy.syncSkills(player);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}