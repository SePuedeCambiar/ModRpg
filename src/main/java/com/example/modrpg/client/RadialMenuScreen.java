package com.example.modrpg.client;

import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketCastSkill;
import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class RadialMenuScreen extends Screen {

    private static final int RADIUS = 75;
    private static final int BADGE_SIZE = 28;

    public RadialMenuScreen() {
        super(Component.literal("Rueda de Habilidades RPG"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Fondo semitransparente con efecto de enfoque
        guiGraphics.fill(0, 0, this.width, this.height, 0x990A0A10);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        PlayerSkills skills = player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).orElse(null);
        if (skills == null) return;

        // Filtrar habilidades activas que el jugador tiene desbloqueadas
        List<SkillNode> activeSkills = new ArrayList<>();
        for (ResourceLocation id : skills.getUnlockedNodes()) {
            SkillNode node = SkillRegistry.get(id);
            if (node != null && (node.getType() == SkillNode.NodeType.ACTIVE_ABILITY || node.getType() == SkillNode.NodeType.ULTIMATE)) {
                activeSkills.add(node);
            }
        }

        if (activeSkills.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "§cNo tienes habilidades activas desbloqueadas.", centerX, centerY, 0xFFFFFF);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        SkillNode hoveredSkill = null;
        int total = activeSkills.size();

        // Dibujar cada habilidad en la rueda circular
        for (int i = 0; i < total; i++) {
            SkillNode node = activeSkills.get(i);

            // Ángulo en radianes (repartido uniformemente en 360 grados)
            double angle = (2 * Math.PI / total) * i - (Math.PI / 2);
            int nodeX = (int) (centerX + Math.cos(angle) * RADIUS) - (BADGE_SIZE / 2);
            int nodeY = (int) (centerY + Math.sin(angle) * RADIUS) - (BADGE_SIZE / 2);

            boolean isHovered = (mouseX >= nodeX && mouseX <= nodeX + BADGE_SIZE && mouseY >= nodeY && mouseY <= nodeY + BADGE_SIZE);
            if (isHovered) {
                hoveredSkill = node;
            }

            boolean onCooldown = skills.hasCooldown(node.getId());
            int borderColor = onCooldown ? 0xFFAA2222 : (isHovered ? 0xFFFFFFFF : 0xFFDAA520);

            // Borde y fondo del slot
            guiGraphics.fill(nodeX - 1, nodeY - 1, nodeX + BADGE_SIZE + 1, nodeY + BADGE_SIZE + 1, borderColor);
            guiGraphics.fill(nodeX, nodeY, nodeX + BADGE_SIZE, nodeY + BADGE_SIZE, isHovered ? 0xFF2A2A38 : 0xFF14141E);

            // Icono
            guiGraphics.renderItem(node.getIcon(), nodeX + (BADGE_SIZE - 16) / 2, nodeY + (BADGE_SIZE - 16) / 2);

            // Si está en cooldown, mostrar máscara roja
            if (onCooldown) {
                guiGraphics.fill(nodeX, nodeY, nodeX + BADGE_SIZE, nodeY + BADGE_SIZE, 0x88AA0000);
            }
        }

        // Centro: información de la habilidad apuntada
        if (hoveredSkill != null) {
            guiGraphics.drawCenteredString(this.font, "§6§l" + hoveredSkill.getDisplayName().getString(), centerX, centerY - 14, 0xFFFFFF);
            if (skills.hasCooldown(hoveredSkill.getId())) {
                int seg = (skills.getCooldown(hoveredSkill.getId()) / 20) + 1;
                guiGraphics.drawCenteredString(this.font, "§c⏳ Enfriamiento: " + seg + "s", centerX, centerY + 2, 0xFF8888);
            } else {
                guiGraphics.drawCenteredString(this.font, "§a✔ Listo para usar (Clic izquierdo)", centerX, centerY + 2, 0x88FF88);
            }
        } else {
            guiGraphics.drawCenteredString(this.font, "§7Selecciona una habilidad", centerX, centerY - 6, 0xAAAAAA);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Clic izquierdo
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            Player player = Minecraft.getInstance().player;
            if (player != null) {
                PlayerSkills skills = player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).orElse(null);
                if (skills != null) {
                    List<SkillNode> activeSkills = new ArrayList<>();
                    for (ResourceLocation id : skills.getUnlockedNodes()) {
                        SkillNode node = SkillRegistry.get(id);
                        if (node != null && (node.getType() == SkillNode.NodeType.ACTIVE_ABILITY || node.getType() == SkillNode.NodeType.ULTIMATE)) {
                            activeSkills.add(node);
                        }
                    }

                    int total = activeSkills.size();
                    for (int i = 0; i < total; i++) {
                        SkillNode node = activeSkills.get(i);
                        double angle = (2 * Math.PI / total) * i - (Math.PI / 2);
                        int nodeX = (int) (centerX + Math.cos(angle) * RADIUS) - (BADGE_SIZE / 2);
                        int nodeY = (int) (centerY + Math.sin(angle) * RADIUS) - (BADGE_SIZE / 2);

                        if (mouseX >= nodeX && mouseX <= nodeX + BADGE_SIZE && mouseY >= nodeY && mouseY <= nodeY + BADGE_SIZE) {
                            // Ejecutar la habilidad seleccionada
                            ModMessages.sendToServer(new PacketCastSkill(node.getId()));
                            this.onClose();
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
