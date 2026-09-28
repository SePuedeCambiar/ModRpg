package com.example.modrpg.client;

import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketSelectSkill;
import com.example.modrpg.networking.PacketTogglePassive;
import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RadialMenuScreen extends Screen {

    private static final int RADIUS = 80;
    private static final int BADGE_SIZE = 30;

    private final List<SkillNode> activeSkills = new ArrayList<>();
    private ResourceLocation lastHoveredId = null;

    public RadialMenuScreen() {
        super(Component.literal("Rueda de Habilidades RPG"));
    }

    private boolean isWheelCompatible(SkillNode node) {
        if (node == null) return false;
        return node.getType() == SkillNode.NodeType.ACTIVE_ABILITY ||
                node.getType() == SkillNode.NodeType.ULTIMATE ||
                node.getType() == SkillNode.NodeType.PASSIVE_TOGGLE;
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
            if (isWheelCompatible(node)) {
                activeSkills.add(node);
            }
        }

        // 2. Fallback automático
        if (activeSkills.isEmpty()) {
            for (ResourceLocation id : skills.getUnlockedNodes()) {
                if (activeSkills.size() >= PlayerSkills.MAX_LOADOUT_SLOTS) break;
                SkillNode node = SkillRegistry.get(id);
                if (isWheelCompatible(node)) {
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
            guiGraphics.drawCenteredString(this.font, "§cNo tienes habilidades equipadas en la rueda.", centerX, centerY - 6, 0xFFFFFF);
            guiGraphics.drawCenteredString(this.font, "§7(Abre el árbol con [K] y equípalas con Clic Derecho)", centerX, centerY + 8, 0xAAAAAA);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        SkillNode hoveredSkill = null;
        int total = activeSkills.size();
        ResourceLocation currentlySelected = skills.getSelectedSkill();

        for (int i = 0; i < total; i++) {
            SkillNode node = activeSkills.get(i);

            double angle = (2 * Math.PI / total) * i - (Math.PI / 2);
            int nodeX = (int) (centerX + Math.cos(angle) * RADIUS) - (BADGE_SIZE / 2);
            int nodeY = (int) (centerY + Math.sin(angle) * RADIUS) - (BADGE_SIZE / 2);

            boolean isHovered = (mouseX >= nodeX && mouseX <= nodeX + BADGE_SIZE && mouseY >= nodeY && mouseY <= nodeY + BADGE_SIZE);
            if (isHovered) {
                hoveredSkill = node;
                if (!Objects.equals(lastHoveredId, node.getId())) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.6f, 0.25f));
                    lastHoveredId = node.getId();
                }
            }

            boolean isToggle = node.getType() == SkillNode.NodeType.PASSIVE_TOGGLE;
            boolean isCurrentSelection = Objects.equals(node.getId(), currentlySelected);
            boolean onCooldown = skills.hasCooldown(node.getId());
            boolean enoughMana = skills.getCurrentMana() >= node.getManaCost();

            int borderColor;
            int borderThickness = 1;

            if (isToggle) {
                boolean active = skills.isToggleActive(node.getId());
                if (active) {
                    borderColor = 0xFF55FF55;
                    borderThickness = 2;
                } else {
                    borderColor = 0xFF555566;
                }
            } else if (isCurrentSelection) {
                borderColor = 0xFF00FFFF;
                borderThickness = 2;
            } else if (onCooldown) {
                borderColor = 0xFFAA2222;
            } else if (!enoughMana) {
                borderColor = 0xFF3366BB;
            } else {
                borderColor = isHovered ? 0xFFFFFFFF : 0xFFDAA520;
            }

            guiGraphics.fill(nodeX - borderThickness, nodeY - borderThickness, nodeX + BADGE_SIZE + borderThickness, nodeY + BADGE_SIZE + borderThickness, borderColor);
            guiGraphics.fill(nodeX, nodeY, nodeX + BADGE_SIZE, nodeY + BADGE_SIZE, isHovered ? 0xFF2A2A38 : 0xFF14141E);
            guiGraphics.renderItem(node.getIcon(), nodeX + (BADGE_SIZE - 16) / 2, nodeY + (BADGE_SIZE - 16) / 2);

            if (onCooldown) {
                guiGraphics.fill(nodeX, nodeY, nodeX + BADGE_SIZE, nodeY + BADGE_SIZE, 0x88AA0000);
            }
        }

        if (hoveredSkill == null) {
            lastHoveredId = null;
        }

        // Información central
        if (hoveredSkill != null) {
            boolean isToggle = hoveredSkill.getType() == SkillNode.NodeType.PASSIVE_TOGGLE;
            boolean isCurrentSelection = Objects.equals(hoveredSkill.getId(), currentlySelected);
            String title = hoveredSkill.getDisplayName().getString();

            if (isToggle) {
                boolean active = skills.isToggleActive(hoveredSkill.getId());
                guiGraphics.drawCenteredString(this.font, (active ? "§a§l✔ " : "§7§l✖ ") + title + (active ? " §2[ACTIVA]" : " §8[DESACTIVADA]"), centerX, centerY - 20, 0xFFFFFF);
                guiGraphics.drawCenteredString(this.font, "§eClic Izquierdo: Alternar Estado (ON / OFF)", centerX, centerY - 6, 0xFFFF88);
                if (hoveredSkill.getSustainManaCost() > 0) {
                    guiGraphics.drawCenteredString(this.font, "§9Mantenimiento: " + String.format("%.1f", hoveredSkill.getSustainManaCost()) + " Maná/s", centerX, centerY + 8, 0x88AAFF);
                }
            } else {
                guiGraphics.drawCenteredString(this.font, (isCurrentSelection ? "§b⭐ " : "§6") + "§l" + title, centerX, centerY - 20, 0xFFFFFF);

                boolean onCooldown = skills.hasCooldown(hoveredSkill.getId());
                boolean enoughMana = skills.getCurrentMana() >= hoveredSkill.getManaCost();

                if (onCooldown) {
                    int seg = (skills.getCooldown(hoveredSkill.getId()) / 20) + 1;
                    guiGraphics.drawCenteredString(this.font, "§c⏳ Enfriamiento: " + seg + "s", centerX, centerY - 6, 0xFF8888);
                } else if (!enoughMana) {
                    guiGraphics.drawCenteredString(this.font, "§9⚡ Falta Maná (Requiere: " + (int) hoveredSkill.getManaCost() + ")", centerX, centerY - 6, 0x88AAFF);
                } else {
                    if (isCurrentSelection) {
                        guiGraphics.drawCenteredString(this.font, "§b[EQUIPADA EN MANO - DISPARA CON R]", centerX, centerY - 6, 0x88FFFF);
                    } else {
                        guiGraphics.drawCenteredString(this.font, "§aClic Izquierdo: Seleccionar para usar", centerX, centerY - 6, 0x88FF88);
                    }
                }

                if (hoveredSkill.getManaCost() > 0) {
                    guiGraphics.drawCenteredString(this.font, "§bCoste: " + (int) hoveredSkill.getManaCost() + " Maná", centerX, centerY + 8, 0xAAAAAA);
                }
            }
        } else {
            guiGraphics.drawCenteredString(this.font, "§7Selecciona habilidad o alterna posturas", centerX, centerY - 6, 0xAAAAAA);
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
                    Player player = Minecraft.getInstance().player;
                    if (player == null) return false;

                    // CASO A: Pasiva conmutable (Toggle)
                    if (node.getType() == SkillNode.NodeType.PASSIVE_TOGGLE) {
                        PlayerSkills skills = player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).orElse(null);
                        if (skills != null && !skills.isToggleActive(node.getId()) && node.getSustainManaCost() > 0 && skills.getCurrentMana() < 1.0f) {
                            player.displayClientMessage(Component.literal("§c⚡ ¡No tienes suficiente maná para activar esta postura!"), true);
                            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FIRE_EXTINGUISH, 1.2f, 0.8f));
                            return true;
                        }

                        ModMessages.sendToServer(new PacketTogglePassive(node.getId()));
                        if (skills != null) {
                            skills.toggleState(node.getId());
                        }
                        this.onClose();
                        return true;
                    }

                    // CASO B: Habilidad activa
                    ModMessages.sendToServer(new PacketSelectSkill(node.getId()));
                    player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(s -> s.setSelectedSkill(node.getId()));

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