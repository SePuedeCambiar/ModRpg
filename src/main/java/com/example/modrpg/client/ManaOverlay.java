package com.example.modrpg.client;

import com.example.modrpg.skills.PlayerSkillsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class ManaOverlay {

    public static final IGuiOverlay HUD_MANA = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Player player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator() || player.isCreative()) return;

        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            float current = skills.getCurrentMana();
            float max = skills.getMaxMana();

            int barWidth = 80;
            int barHeight = 6;
            int x = (screenWidth / 2) + 12;
            int y = screenHeight - 48; // Encima de los muslitos de comida

            float percentage = Math.min(1.0f, Math.max(0.0f, current / max));
            int filledWidth = (int) (barWidth * percentage);

            // Borde y fondo oscuro
            guiGraphics.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0xFF000000);
            guiGraphics.fill(x, y, x + barWidth, y + barHeight, 0xFF1A1A2E);

            // Barra azul de maná
            guiGraphics.fill(x, y, x + filledWidth, y + barHeight, 0xFF00AAFF);

            // Texto numérico
            String text = (int) current + " / " + (int) max;
            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    "§b⚡ " + text,
                    x + (barWidth / 2) - (Minecraft.getInstance().font.width("⚡ " + text) / 2),
                    y - 8,
                    0xFFFFFF
            );
        });
    };
}