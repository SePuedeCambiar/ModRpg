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
                // 1. Reemplazar ramas
                skills.getAllBranchLevels(); // vista
                msg.branchLevels.forEach(skills::setBranchLevel);

                // 2. Reemplazar nodos desbloqueados
                skills.getUnlockedNodes().clear();
                msg.unlockedNodes.forEach(skills::unlockNode);

                // 3. Reemplazar contadores de práctica
                msg.practiceCounters.forEach(skills::setPractice);

                // 4. Reemplazar cooldowns
                msg.cooldowns.forEach(skills::setCooldown);

                skills.setUltimateCharged(msg.ultimateCharged);
            });
        }
    }
}