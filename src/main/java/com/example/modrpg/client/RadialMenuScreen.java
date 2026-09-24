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

    private static final int RADIUS = 80;
    private static final int BADGE_SIZE = 30;

    private final List<SkillNode> activeSkills = new ArrayList<>();

    public RadialMenuScreen() {
        super(Component.literal("Rueda de Habilidades RPG"));
    }

    @Override
    protected void init() {
        super.init();
        activeSkills.clear();

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        PlayerSkills skills = player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).orElse(null);
        if (skills == null) return;

        // 1. Cargar las habilidades equipadas en el Loadout
        List<ResourceLocation> equipped = skills.getEquippedSkills();
        for (ResourceLocation id : equipped) {
            SkillNode node = SkillRegistry.get(id);
            if (node != null && (node.getType() == SkillNode.NodeType.ACTIVE_ABILITY || node.getType() == SkillNode.NodeType.ULTIMATE)) {
                activeSkills.add(node);
            }
        }

        // 2. Fallback: Si no ha configurado el loadout, toma hasta 8 activas desbloqueadas
        if (activeSkills.isEmpty()) {
            for (ResourceLocation id : skills.getUnlockedNodes()) {
                if (activeSkills.size() >= PlayerSkills.MAX_LOADOUT_SLOTS) break;
                SkillNode node = SkillRegistry.get(id);
                if (node != null && (node.getType() == SkillNode.NodeType.ACTIVE_ABILITY || node.getType() == SkillNode.NodeType.ULTIMATE)) {
                    activeSkills.add(node);
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x990A0A10);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        PlayerSkills skills = player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).orElse(null);
        if (skills == null) return;

        if (activeSkills.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "§cNo tienes habilidades activas equipadas.", centerX, centerY - 6, 0xFFFFFF);
            guiGraphics.drawCenteredString(this.font, "§7(Abre el árbol con [K] y equípalas con Clic Derecho)", centerX, centerY + 8, 0xAAAAAA);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        SkillNode hoveredSkill = null;
        int total = activeSkills.size();

        for (int i = 0; i < total; i++) {
            SkillNode node = activeSkills.get(i);

            double angle = (2 * Math.PI / total) * i - (Math.PI / 2);
            int nodeX = (int) (centerX + Math.cos(angle) * RADIUS) - (BADGE_SIZE / 2);
            int nodeY = (int) (centerY + Math.sin(angle) * RADIUS) - (BADGE_SIZE / 2);

            boolean isHovered = (mouseX >= nodeX && mouseX <= nodeX + BADGE_SIZE && mouseY >= nodeY && mouseY <= nodeY + BADGE_SIZE);
            if (isHovered) {
                hoveredSkill = node;
            }

            boolean onCooldown = skills.hasCooldown(node.getId());
            boolean enoughMana = skills.getCurrentMana() >= node.getManaCost();

            int borderColor;
            if (onCooldown) {
                borderColor = 0xFFAA2222; // Rojo si está en enfriamiento
            } else if (!enoughMana) {
                borderColor = 0xFF3366BB; // Azul oscuro si falta maná
            } else {
                borderColor = isHovered ? 0xFFFFFFFF : 0xFFDAA520; // Blanco si tiene cursor encima, dorado por defecto
            }

            // Fondo y marco del badge
            guiGraphics.fill(nodeX - 1, nodeY - 1, nodeX + BADGE_SIZE + 1, nodeY + BADGE_SIZE + 1, borderColor);
            guiGraphics.fill(nodeX, nodeY, nodeX + BADGE_SIZE, nodeY + BADGE_SIZE, isHovered ? 0xFF2A2A38 : 0xFF14141E);
            guiGraphics.renderItem(node.getIcon(), nodeX + (BADGE_SIZE - 16) / 2, nodeY + (BADGE_SIZE - 16) / 2);

            // Capa sombreada si está en cooldown
            if (onCooldown) {
                guiGraphics.fill(nodeX, nodeY, nodeX + BADGE_SIZE, nodeY + BADGE_SIZE, 0x88AA0000);
            }
        }

        // Información en el centro de la rueda
        if (hoveredSkill != null) {
            guiGraphics.drawCenteredString(this.font, "§6§l" + hoveredSkill.getDisplayName().getString(), centerX, centerY - 18, 0xFFFFFF);

            boolean onCooldown = skills.hasCooldown(hoveredSkill.getId());
            boolean enoughMana = skills.getCurrentMana() >= hoveredSkill.getManaCost();

            if (onCooldown) {
                int seg = (skills.getCooldown(hoveredSkill.getId()) / 20) + 1;
                guiGraphics.drawCenteredString(this.font, "§c⏳ Enfriamiento: " + seg + "s", centerX, centerY - 4, 0xFF8888);
            } else if (!enoughMana) {
                guiGraphics.drawCenteredString(this.font, "§9⚡ Falta Maná (Requiere: " + (int)hoveredSkill.getManaCost() + ")", centerX, centerY - 4, 0x88AAFF);
            } else {
                guiGraphics.drawCenteredString(this.font, "§a✔ Listo para usar (Clic izquierdo)", centerX, centerY - 4, 0x88FF88);
            }

            if (hoveredSkill.getManaCost() > 0) {
                guiGraphics.drawCenteredString(this.font, "§bCoste: " + (int)hoveredSkill.getManaCost() + " Maná", centerX, centerY + 8, 0xAAAAAA);
            }
        } else {
            guiGraphics.drawCenteredString(this.font, "§7Selecciona una habilidad (" + total + "/" + PlayerSkills.MAX_LOADOUT_SLOTS + ")", centerX, centerY - 6, 0xAAAAAA);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !activeSkills.isEmpty()) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int total = activeSkills.size();

            for (int i = 0; i < total; i++) {
                SkillNode node = activeSkills.get(i);
                double angle = (2 * Math.PI / total) * i - (Math.PI / 2);
                int nodeX = (int) (centerX + Math.cos(angle) * RADIUS) - (BADGE_SIZE / 2);
                int nodeY = (int) (centerY + Math.sin(angle) * RADIUS) - (BADGE_SIZE / 2);

                if (mouseX >= nodeX && mouseX <= nodeX + BADGE_SIZE && mouseY >= nodeY && mouseY <= nodeY + BADGE_SIZE) {
                    ModMessages.sendToServer(new PacketCastSkill(node.getId()));
                    this.onClose();
                    return true;
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