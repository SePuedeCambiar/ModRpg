package com.example.modrpg.networking;

import com.example.modrpg.skills.PlayerSkills;
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

public class PacketEquipSkill {

    private final ResourceLocation skillId;

    public PacketEquipSkill(ResourceLocation skillId) {
        this.skillId = skillId;
    }

    public static void encode(PacketEquipSkill msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.skillId);
    }

    public static PacketEquipSkill decode(FriendlyByteBuf buf) {
        return new PacketEquipSkill(buf.readResourceLocation());
    }

    public static void handle(PacketEquipSkill msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                SkillNode node = SkillRegistry.get(msg.skillId);
                if (node == null || !skills.isNodeUnlocked(node.getId())) return;

                // Solo se equipan activas o definitivas
                if (node.getType() != SkillNode.NodeType.ACTIVE_ABILITY && node.getType() != SkillNode.NodeType.ULTIMATE) {
                    player.displayClientMessage(Component.literal("§c[RPG] Las habilidades pasivas siempre están activas y no ocupan ranura."), true);
                    return;
                }

                if (skills.isSkillEquipped(node.getId())) {
                    // Desequipar
                    int index = skills.getEquippedSkills().indexOf(node.getId());
                    skills.unequipSkill(index);
                    player.displayClientMessage(Component.literal("§e[RPG] Desequipada de la rueda: §f" + node.getDisplayName().getString()), true);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 0.8f, 0.8f);
                } else {
                    // Equipar
                    if (skills.getEquippedSkills().size() >= PlayerSkills.MAX_LOADOUT_SLOTS) {
                        player.displayClientMessage(Component.literal("§c[RPG] Rueda llena (Máx. " + PlayerSkills.MAX_LOADOUT_SLOTS + " ranuras). Desequipa una primero con clic derecho."), true);
                        return;
                    }
                    skills.equipSkill(skills.getEquippedSkills().size(), node.getId());
                    player.displayClientMessage(Component.literal("§a[RPG] ¡Equipada en la rueda: §f" + node.getDisplayName().getString() + "§a!"), true);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0f, 1.2f);
                }

                SkillEconomy.syncSkills(player);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}