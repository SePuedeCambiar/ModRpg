package com.example.modrpg.client;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Map;

public class SkillCooldownOverlay {

    public static final IGuiOverlay HUD_SKILLS = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Player player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) return;

        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            Map<ResourceLocation, Integer> cooldowns = skills.getAllCooldowns();
            if (cooldowns.isEmpty()) return;

            int startX = 12;
            int startY = screenHeight - 40;
            int slotSize = 22;
            int index = 0;

            for (Map.Entry<ResourceLocation, Integer> entry : cooldowns.entrySet()) {
                int cdTicks = entry.getValue();
                if (cdTicks <= 0) continue;

                SkillNode node = SkillRegistry.get(entry.getKey());
                if (node == null) continue;

                int x = startX + (index * (slotSize + 4));
                int y = startY;

                // Marco del cooldown
                guiGraphics.fill(x - 1, y - 1, x + slotSize + 1, y + slotSize + 1, 0x88000000);
                guiGraphics.fill(x, y, x + slotSize, y + slotSize, 0xCC1A1A24);

                // Icono
                guiGraphics.renderItem(node.getIcon(), x + 3, y + 3);

                // Capa oscura de enfriamiento
                guiGraphics.fill(x, y, x + slotSize, y + slotSize, 0xAA000000);

                // Texto del tiempo restante
                int seconds = (cdTicks / 20) + 1;
                String timeText = (seconds >= 60) ? (seconds / 60) + "m" : seconds + "s";

                guiGraphics.drawCenteredString(
                        Minecraft.getInstance().font,
                        "§e" + timeText,
                        x + (slotSize / 2),
                        y + 6,
                        0xFFFFFF
                );

                index++;
            }
        });
    };
}
