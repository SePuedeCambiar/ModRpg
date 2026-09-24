package com.example.modrpg.networking;

import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillEconomy;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketCastSkill {

    private final ResourceLocation skillId;

    public PacketCastSkill(ResourceLocation skillId) {
        this.skillId = skillId;
    }

    public static void encode(PacketCastSkill msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.skillId);
    }

    public static PacketCastSkill decode(FriendlyByteBuf buf) {
        return new PacketCastSkill(buf.readResourceLocation());
    }

    public static void handle(PacketCastSkill msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                SkillNode node = SkillRegistry.get(msg.skillId);

                if (node == null) {
                    player.displayClientMessage(Component.literal("§c[RPG] Habilidad no registrada: §e" + msg.skillId), true);
                    return;
                }

                if (!skills.isNodeUnlocked(node.getId())) {
                    player.displayClientMessage(Component.literal("§c🔒 Aún no has desbloqueado: §f" + node.getDisplayName().getString()), true);
                    return;
                }

                if (skills.hasCooldown(node.getId())) {
                    int remainingSeconds = (skills.getCooldown(node.getId()) / 20) + 1;
                    player.displayClientMessage(Component.literal("§c⏳ En enfriamiento: §e" + remainingSeconds + "s"), true);
                    return;
                }

                // Validación y consumo de maná
                if (node.getManaCost() > 0.0f) {
                    if (!skills.consumeMana(node.getManaCost())) {
                        player.displayClientMessage(Component.literal("§9§l⚡ ¡Maná insuficiente! §7(Necesitas: §b" + (int) node.getManaCost() + "§7)"), true);
                        return;
                    }
                    // Sincronización instantánea de maná
                    ModMessages.sendToPlayer(new PacketSyncMana(skills.getCurrentMana(), skills.getMaxMana()), player);
                }

                if (node.getDefaultCooldownTicks() > 0) {
                    skills.setCooldown(node.getId(), node.getDefaultCooldownTicks());
                }

                node.onExecuteActive(player, skills);
                SkillEconomy.syncSkills(player);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}