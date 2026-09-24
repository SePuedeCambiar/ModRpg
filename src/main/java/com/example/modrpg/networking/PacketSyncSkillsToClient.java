package com.example.modrpg.networking;

import com.example.modrpg.client.ClientPacketHandler;
import com.example.modrpg.skills.PlayerSkills;
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

    public PacketSyncSkillsToClient(PlayerSkills skills) {
        this.branchLevels = new HashMap<>(skills.getAllBranchLevels());
        this.unlockedNodes = new HashSet<>(skills.getUnlockedNodes());
        this.practiceCounters = new HashMap<>(skills.getAllPracticeCounters());
        this.cooldowns = new HashMap<>(skills.getAllCooldowns());
        this.ultimateCharged = skills.isUltimateCharged();
        this.equippedSkills = new ArrayList<>(skills.getEquippedSkills());
    }

    public PacketSyncSkillsToClient(Map<ResourceLocation, Integer> branchLevels,
                                    Set<ResourceLocation> unlockedNodes,
                                    Map<ResourceLocation, Integer> practiceCounters,
                                    Map<ResourceLocation, Integer> cooldowns,
                                    boolean ultimateCharged,
                                    List<ResourceLocation> equippedSkills) {
        this.branchLevels = branchLevels;
        this.unlockedNodes = unlockedNodes;
        this.practiceCounters = practiceCounters;
        this.cooldowns = cooldowns;
        this.ultimateCharged = ultimateCharged;
        this.equippedSkills = equippedSkills;
    }

    public static void encode(PacketSyncSkillsToClient msg, FriendlyByteBuf buf) {
        buf.writeMap(msg.branchLevels, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeVarInt);
        buf.writeCollection(msg.unlockedNodes, FriendlyByteBuf::writeResourceLocation);
        buf.writeMap(msg.practiceCounters, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeVarInt);
        buf.writeMap(msg.cooldowns, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeVarInt);
        buf.writeBoolean(msg.ultimateCharged);
        buf.writeCollection(msg.equippedSkills, FriendlyByteBuf::writeResourceLocation);
    }

    public static PacketSyncSkillsToClient decode(FriendlyByteBuf buf) {
        Map<ResourceLocation, Integer> branchLevels = buf.readMap(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readVarInt);
        Set<ResourceLocation> unlockedNodes = buf.readCollection(HashSet::new, FriendlyByteBuf::readResourceLocation);
        Map<ResourceLocation, Integer> practiceCounters = buf.readMap(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readVarInt);
        Map<ResourceLocation, Integer> cooldowns = buf.readMap(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readVarInt);
        boolean ultimateCharged = buf.readBoolean();
        List<ResourceLocation> equippedSkills = buf.readCollection(ArrayList::new, FriendlyByteBuf::readResourceLocation);

        return new PacketSyncSkillsToClient(branchLevels, unlockedNodes, practiceCounters, cooldowns, ultimateCharged, equippedSkills);
    }

    public static void handle(PacketSyncSkillsToClient msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSync(msg));
        });
        ctx.get().setPacketHandled(true);
    }
}