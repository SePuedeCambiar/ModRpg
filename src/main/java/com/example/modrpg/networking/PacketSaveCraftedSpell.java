package com.example.modrpg.networking;

import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillEconomy;
import com.example.modrpg.skills.magic.modular.CraftedSpell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSaveCraftedSpell {

    private final int slotIndex;
    private final CraftedSpell spell;

    public PacketSaveCraftedSpell(int slotIndex, CraftedSpell spell) {
        this.slotIndex = slotIndex;
        this.spell = spell;
    }

    public static void encode(PacketSaveCraftedSpell msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.slotIndex);
        buf.writeNbt(msg.spell.toNBT());
    }

    public static PacketSaveCraftedSpell decode(FriendlyByteBuf buf) {
        int slot = buf.readInt();
        CompoundTag tag = buf.readNbt();
        CraftedSpell spell = (tag != null) ? CraftedSpell.fromNBT(tag) : null;
        return new PacketSaveCraftedSpell(slot, spell);
    }

    public static void handle(PacketSaveCraftedSpell msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || msg.spell == null) return;

            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                skills.setSpell(msg.slotIndex, msg.spell);

                player.displayClientMessage(
                        Component.literal("§a§l✔ [RPG] ¡Hechizo §f§l" + msg.spell.getName() + "§a guardado en la Ranura " + (msg.slotIndex + 1) + "!"),
                        true
                );
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.4f);

                SkillEconomy.syncSkills(player);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}