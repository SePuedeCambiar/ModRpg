package com.example.modrpg.networking;

import com.example.modrpg.skills.PlayerSkillsProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSkillActivate {
    public PacketSkillActivate() {}

    public static void encode(PacketSkillActivate msg, FriendlyByteBuf buffer) {}

    public static PacketSkillActivate decode(FriendlyByteBuf buffer) {
        return new PacketSkillActivate();
    }

    public static void handle(PacketSkillActivate msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                if (skills.getMeleeLevel() < 10 && !skills.hasCapstoneMelee()) {
                    player.displayClientMessage(
                            Component.literal("§c🔒 Requiere Nivel 10 de Melee para usar el Golpe Definitivo."),
                            true
                    );
                    return;
                }

                if (skills.getUltimateCooldown() > 0) {
                    int segundos = (skills.getUltimateCooldown() / 20) + 1;
                    player.displayClientMessage(
                            Component.literal("§c⏳ Enfriamiento activo: §e" + segundos + "s"),
                            true
                    );
                    return;
                }

                if (skills.isUltimateCharged()) {
                    player.displayClientMessage(
                            Component.literal("§e⚡ ¡Tu arma ya está cargada! Ataca a un enemigo."),
                            true
                    );
                    return;
                }

                skills.setUltimateCharged(true);

                player.level().playSound(
                        null,
                        player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENDER_DRAGON_GROWL,
                        SoundSource.PLAYERS,
                        0.6f, 1.8f
                );

                player.displayClientMessage(
                        Component.literal("§6§l⚡ ¡GOLPE DEFINITIVO CARGADO! §e(Próximo impacto: +500% daño)"),
                        true
                );
            });
        });
        ctx.get().setPacketHandled(true);
    }
}