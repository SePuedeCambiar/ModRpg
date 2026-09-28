package com.example.modrpg.networking;

import com.example.modrpg.client.ClientPacketHandler;
import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.magic.modular.CraftedSpell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class PacketSyncSkillsToClient {

    public final Map<ResourceLocation, Integer> branchLevels;
    public final Set<ResourceLocation> unlockedNodes;
    public final Map<ResourceLocation, Integer> practiceCounters;
    public final Map<ResourceLocation, Integer> cooldowns;
    public final boolean ultimateCharged;
    public final List<ResourceLocation> equippedSkills;
    public final float currentMana;
    public final float maxMana;
    public final ResourceLocation selectedSkill;
    public final Set<ResourceLocation> activeToggles;
    public final CraftedSpell[] spells;
    public final ResourceLocation primaryBranch;
    public final ResourceLocation secondaryBranch;

    public PacketSyncSkillsToClient(PlayerSkills skills) {
        this.branchLevels = new HashMap<>(skills.getAllBranchLevels());
        this.unlockedNodes = new HashSet<>(skills.getUnlockedNodes());
        this.practiceCounters = new HashMap<>(skills.getAllPracticeCounters());
        this.cooldowns = new HashMap<>(skills.getAllCooldowns());
        this.ultimateCharged = skills.isUltimateCharged();
        this.equippedSkills = new ArrayList<>(skills.getEquippedSkills());
        this.currentMana = skills.getCurrentMana();
        this.maxMana = skills.getMaxMana();
        this.selectedSkill = skills.getSelectedSkill();
        this.activeToggles = new HashSet<>(skills.getActiveToggles());
        this.spells = skills.getAllSpells();
        this.primaryBranch = skills.getPrimaryBranch();
        this.secondaryBranch = skills.getSecondaryBranch();
    }

    public PacketSyncSkillsToClient(Map<ResourceLocation, Integer> branchLevels,
                                    Set<ResourceLocation> unlockedNodes,
                                    Map<ResourceLocation, Integer> practiceCounters,
                                    Map<ResourceLocation, Integer> cooldowns,
                                    boolean ultimateCharged,
                                    List<ResourceLocation> equippedSkills,
                                    float currentMana,
                                    float maxMana,
                                    ResourceLocation selectedSkill,
                                    Set<ResourceLocation> activeToggles,
                                    CraftedSpell[] spells,
                                    ResourceLocation primaryBranch,
                                    ResourceLocation secondaryBranch) {
        this.branchLevels = branchLevels;
        this.unlockedNodes = unlockedNodes;
        this.practiceCounters = practiceCounters;
        this.cooldowns = cooldowns;
        this.ultimateCharged = ultimateCharged;
        this.equippedSkills = equippedSkills;
        this.currentMana = currentMana;
        this.maxMana = maxMana;
        this.selectedSkill = selectedSkill;
        this.activeToggles = activeToggles;
        this.spells = spells;
        this.primaryBranch = primaryBranch;
        this.secondaryBranch = secondaryBranch;
    }

    public static void encode(PacketSyncSkillsToClient msg, FriendlyByteBuf buf) {
        buf.writeMap(msg.branchLevels, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeVarInt);
        buf.writeCollection(msg.unlockedNodes, FriendlyByteBuf::writeResourceLocation);
        buf.writeMap(msg.practiceCounters, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeVarInt);
        buf.writeMap(msg.cooldowns, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeVarInt);
        buf.writeBoolean(msg.ultimateCharged);
        buf.writeCollection(msg.equippedSkills, FriendlyByteBuf::writeResourceLocation);
        buf.writeFloat(msg.currentMana);
        buf.writeFloat(msg.maxMana);

        buf.writeBoolean(msg.selectedSkill != null);
        if (msg.selectedSkill != null) buf.writeResourceLocation(msg.selectedSkill);

        buf.writeCollection(msg.activeToggles, FriendlyByteBuf::writeResourceLocation);

        for (int i = 0; i < 4; i++) {
            boolean hasSpell = (msg.spells != null && i < msg.spells.length && msg.spells[i] != null);
            buf.writeBoolean(hasSpell);
            if (hasSpell) buf.writeNbt(msg.spells[i].toNBT());
        }

        buf.writeBoolean(msg.primaryBranch != null);
        if (msg.primaryBranch != null) buf.writeResourceLocation(msg.primaryBranch);

        buf.writeBoolean(msg.secondaryBranch != null);
        if (msg.secondaryBranch != null) buf.writeResourceLocation(msg.secondaryBranch);
    }

    public static PacketSyncSkillsToClient decode(FriendlyByteBuf buf) {
        Map<ResourceLocation, Integer> branchLevels = buf.readMap(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readVarInt);
        Set<ResourceLocation> unlockedNodes = buf.readCollection(HashSet::new, FriendlyByteBuf::readResourceLocation);
        Map<ResourceLocation, Integer> practiceCounters = buf.readMap(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readVarInt);
        Map<ResourceLocation, Integer> cooldowns = buf.readMap(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readVarInt);
        boolean ultimateCharged = buf.readBoolean();
        List<ResourceLocation> equippedSkills = buf.readCollection(ArrayList::new, FriendlyByteBuf::readResourceLocation);
        float currentMana = buf.readFloat();
        float maxMana = buf.readFloat();
        ResourceLocation selectedSkill = buf.readBoolean() ? buf.readResourceLocation() : null;
        Set<ResourceLocation> activeToggles = buf.readCollection(HashSet::new, FriendlyByteBuf::readResourceLocation);

        CraftedSpell[] spells = new CraftedSpell[4];
        for (int i = 0; i < 4; i++) {
            if (buf.readBoolean()) {
                CompoundTag tag = buf.readNbt();
                spells[i] = (tag != null) ? CraftedSpell.fromNBT(tag) : null;
            }
        }

        ResourceLocation primary = buf.readBoolean() ? buf.readResourceLocation() : null;
        ResourceLocation secondary = buf.readBoolean() ? buf.readResourceLocation() : null;

        return new PacketSyncSkillsToClient(branchLevels, unlockedNodes, practiceCounters, cooldowns, ultimateCharged, equippedSkills, currentMana, maxMana, selectedSkill, activeToggles, spells, primary, secondary);
    }

    public static void handle(PacketSyncSkillsToClient msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSync(msg));
        });
        ctx.get().setPacketHandled(true);
    }
}