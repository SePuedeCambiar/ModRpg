package com.example.modrpg.client;

import com.example.modrpg.networking.PacketSyncSkillsToClient;
import com.example.modrpg.skills.PlayerSkillsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class ClientPacketHandler {
    public static void handleSync(PacketSyncSkillsToClient msg) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                skills.setMeleeLevel(msg.meleeLevel);
                skills.setRangedLevel(msg.rangedLevel);
                skills.setMobilityLevel(msg.mobilityLevel);
                skills.setMeleeKills(msg.meleeKills);
                skills.setRangedKills(msg.rangedKills);
                skills.setDoubleAttack(msg.hasDoubleAttack);
                skills.setSpinAttack(msg.hasSpinAttack);
                skills.setCapstoneMelee(msg.hasCapstoneMelee);
                skills.setTailwind(msg.hasTailwind);
                skills.setHypersonicArrow(msg.hasHypersonicArrow);
                skills.setHybridRangedMelee(msg.hasHybridRangedMelee);
            });
        }
    }
}