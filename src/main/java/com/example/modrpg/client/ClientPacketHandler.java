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
                // Asignamos las bajas acumuladas
                while (skills.getMeleeKills() < msg.meleeKills) skills.addMeleeKill();
                while (skills.getRangedKills() < msg.rangedKills) skills.addRangedKill();
                skills.setCapstoneMelee(msg.hasCapstoneMelee);
                skills.setHybridRangedMelee(msg.hasHybridRangedMelee);
            });
        }
    }
}