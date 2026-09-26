package com.example.modrpg.client;

import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
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

            // Borde y fondo oscuro de la barra
            guiGraphics.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0xFF000000);
            guiGraphics.fill(x, y, x + barWidth, y + barHeight, 0xFF1A1A2E);

            // Barra azul de maná
            guiGraphics.fill(x, y, x + filledWidth, y + barHeight, 0xFF00AAFF);

            // Texto numérico de maná
            String text = (int) current + " / " + (int) max;
            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    "§b⚡ " + text,
                    x + (barWidth / 2) - (Minecraft.getInstance().font.width("⚡ " + text) / 2),
                    y - 8,
                    0xFFFFFF
            );

            // =========================================================================
            // CASILLERO DE HABILIDAD SELECCIONADA [R] (A la izquierda de la barra)
            // =========================================================================
            ResourceLocation selectedId = skills.getSelectedSkill();
            if (selectedId != null) {
                SkillNode node = SkillRegistry.get(selectedId);
                if (node != null) {
                    int slotSize = 20;
                    int slotX = x - slotSize - 6;
                    int slotY = y - 9;

                    boolean onCooldown = skills.hasCooldown(selectedId);
                    int borderColor = onCooldown ? 0xFFAA2222 : 0xFF00AAFF;

                    // Fondo y marco
                    guiGraphics.fill(slotX - 1, slotY - 1, slotX + slotSize + 1, slotY + slotSize + 1, 0xFF000000);
                    guiGraphics.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, 0xCC14141E);

                    // Borde de estado
                    guiGraphics.renderOutline(slotX, slotY, slotSize, slotSize, borderColor);

                    // Icono de la habilidad
                    guiGraphics.renderItem(node.getIcon(), slotX + 2, slotY + 2);

                    // Sombra y tiempo si está en cooldown
                    if (onCooldown) {
                        guiGraphics.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, 0xAA000000);
                        int cdSeg = (skills.getCooldown(selectedId) / 20) + 1;
                        guiGraphics.drawCenteredString(Minecraft.getInstance().font, "§c" + cdSeg, slotX + (slotSize / 2), slotY + 6, 0xFFFFFF);
                    }

                    // Tecla rápida [R] en la esquina
                    guiGraphics.drawString(Minecraft.getInstance().font, "§eR", slotX + slotSize - 5, slotY + slotSize - 7, 0xFFFFFF);
                }
            }
        });
    };
}