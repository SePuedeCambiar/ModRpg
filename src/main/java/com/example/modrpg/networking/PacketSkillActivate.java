package com.example.modrpg.networking;

import com.example.modrpg.client.ClientPacketHandler;
import com.example.modrpg.skills.PlayerSkills;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncSkillsToClient {
    public final int meleeLevel;
    public final int rangedLevel;
    public final int mobilityLevel;
    public final int meleeKills;
    public final int rangedKills;
    public final boolean hasCapstoneMelee;
    public final boolean hasHybridRangedMelee;

    public PacketSyncSkillsToClient(PlayerSkills skills) {
        this.meleeLevel = skills.getMeleeLevel();
        this.rangedLevel = skills.getRangedLevel();
        this.mobilityLevel = skills.getMobilityLevel();
        this.meleeKills = skills.getMeleeKills();
        this.rangedKills = skills.getRangedKills();
        this.hasCapstoneMelee = skills.hasCapstoneMelee();
        this.hasHybridRangedMelee = skills.hasHybridRangedMelee();
    }

    public PacketSyncSkillsToClient(int meleeLevel, int rangedLevel, int mobilityLevel,
                                    int meleeKills, int rangedKills,
                                    boolean hasCapstoneMelee, boolean hasHybridRangedMelee) {
        this.meleeLevel = meleeLevel;
        this.rangedLevel = rangedLevel;
        this.mobilityLevel = mobilityLevel;
        this.meleeKills = meleeKills;
        this.rangedKills = rangedKills;
        this.hasCapstoneMelee = hasCapstoneMelee;
        this.hasHybridRangedMelee = hasHybridRangedMelee;
    }

    public static void encode(PacketSyncSkillsToClient msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.meleeLevel);
        buf.writeInt(msg.rangedLevel);
        buf.writeInt(msg.mobilityLevel);
        buf.writeInt(msg.meleeKills);
        buf.writeInt(msg.rangedKills);
        buf.writeBoolean(msg.hasCapstoneMelee);
        buf.writeBoolean(msg.hasHybridRangedMelee);
    }

    public static PacketSyncSkillsToClient decode(FriendlyByteBuf buf) {
        return new PacketSyncSkillsToClient(
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(),
                buf.readBoolean(), buf.readBoolean()
        );
    }

    public static void handle(PacketSyncSkillsToClient msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Ejecutamos únicamente en el lado del cliente de forma segura
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSync(msg));
        });
        ctx.get().setPacketHandled(true);
    }
}