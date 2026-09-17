package com.example.modrpg.networking;

import com.example.modrpg.skills.PlayerSkillsProvider;
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

                // 1. Validar que la habilidad exista en el registro
                if (node == null) {
                    player.displayClientMessage(
                            Component.literal("§c[RPG] Habilidad no registrada: §e" + msg.skillId),
                            true
                    );
                    return;
                }

                // 2. Validar que el jugador la tenga desbloqueada
                if (!skills.isNodeUnlocked(node.getId())) {
                    player.displayClientMessage(
                            Component.literal("§c🔒 Aún no has desbloqueado: §f" + node.getDisplayName().getString()),
                            true
                    );
                    return;
                }

                // 3. Validar enfriamiento (Cooldown)
                if (skills.hasCooldown(node.getId())) {
                    int remainingSeconds = (skills.getCooldown(node.getId()) / 20) + 1;
                    player.displayClientMessage(
                            Component.literal("§c⏳ Habilidad en enfriamiento: §e" + remainingSeconds + "s"),
                            true
                    );
                    return;
                }

                // 4. Aplicar cooldown base y ejecutar la habilidad
                if (node.getDefaultCooldownTicks() > 0) {
                    skills.setCooldown(node.getId(), node.getDefaultCooldownTicks());
                }

                node.onExecuteActive(player, skills);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}