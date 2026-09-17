package com.example.modrpg.client;

import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketUnlockNode;
import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import com.example.modrpg.skills.data.SkillRequirement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class SkillTreeScreen extends Screen {

    // Desplazamiento de cámara (Pan / Drag)
    private double scrollX = 0;
    private double scrollY = 0;

    private static final int NODE_SIZE = 26;

    public SkillTreeScreen() {
        super(Component.literal("Árbol de Habilidades RPG"));
    }

    @Override
    protected void init() {
        super.init();
        // Botón para recentrar cámara
        this.addRenderableWidget(Button.builder(Component.literal("⌖ Recentrar"), btn -> {
            this.scrollX = 0;
            this.scrollY = 0;
        }).bounds(10, 10, 80, 20).build());

        // Botón cerrar
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), btn -> this.onClose())
                .bounds(this.width - 70, 10, 60, 20).build());
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // Clic izquierdo o derecho para arrastrar el lienzo
        if (button == 0 || button == 1) {
            this.scrollX += dragX;
            this.scrollY += dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0) { // Clic izquierdo sobre un nodo para comprarlo
            int centerX = (int) (this.width / 2 + scrollX);
            int centerY = (int) (this.height / 2 + scrollY);

            Player player = Minecraft.getInstance().player;
            if (player != null) {
                PlayerSkills skills = player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).orElse(null);
                if (skills != null) {
                    for (SkillNode node : SkillRegistry.getAll()) {
                        int nx = centerX + node.getPosX() - (NODE_SIZE / 2);
                        int ny = centerY + node.getPosY() - (NODE_SIZE / 2);

                        if (mouseX >= nx && mouseX <= nx + NODE_SIZE && mouseY >= ny && mouseY <= ny + NODE_SIZE) {
                            if (!skills.isNodeUnlocked(node.getId())) {
                                ModMessages.sendToServer(new PacketUnlockNode(node.getId()));
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1. Fondo oscuro estilo RPG
        this.renderBackground(guiGraphics);
        guiGraphics.fill(0, 0, this.width, this.height, 0xDD0D0D12);

        int centerX = (int) (this.width / 2 + scrollX);
        int centerY = (int) (this.height / 2 + scrollY);

        Player player = Minecraft.getInstance().player;
        PlayerSkills skills = (player != null) ? player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).orElse(null) : null;

        // 2. Dibujar líneas de conexión entre nodos
        for (SkillNode node : SkillRegistry.getAll()) {
            int startX = centerX + node.getPosX();
            int startY = centerY + node.getPosY();

            int targetX = centerX;
            int targetY = centerY;

            if (node.getParentId() != null) {
                SkillNode parent = SkillRegistry.get(node.getParentId());
                if (parent != null) {
                    targetX = centerX + parent.getPosX();
                    targetY = centerY + parent.getPosY();
                }
            }

            boolean isUnlocked = (skills != null && skills.isNodeUnlocked(node.getId()));
            int lineColor = isUnlocked ? 0xFFDAA520 : 0xFF444455;

            drawLine(guiGraphics, targetX, targetY, startX, startY, lineColor);
        }

        // 3. Dibujar nodo central de inicio ("Inicia tu aventura")
        guiGraphics.fill(centerX - 18, centerY - 18, centerX + 18, centerY + 18, 0xFF222233);
        guiGraphics.fill(centerX - 16, centerY - 16, centerX + 16, centerY + 16, 0xFFDAA520);
        guiGraphics.renderItem(new ItemStack(Items.COMPASS), centerX - 8, centerY - 8);

        // 4. Dibujar cada nodo del árbol
        SkillNode hoveredNode = null;
        for (SkillNode node : SkillRegistry.getAll()) {
            int nx = centerX + node.getPosX() - (NODE_SIZE / 2);
            int ny = centerY + node.getPosY() - (NODE_SIZE / 2);

            boolean isUnlocked = (skills != null && skills.isNodeUnlocked(node.getId()));

            int borderColor;
            switch (node.getType()) {
                case ULTIMATE -> borderColor = isUnlocked ? 0xFF55FF55 : 0xFF00AA00;
                case HYBRID_SYNERGY -> borderColor = isUnlocked ? 0xFFFF55FF : 0xFFAA00AA;
                case ACTIVE_ABILITY -> borderColor = isUnlocked ? 0xFFFFAA00 : 0xFFCC6600;
                default -> borderColor = isUnlocked ? 0xFF55FFFF : 0xFF0088AA;
            }

            // Marco y fondo del nodo
            guiGraphics.fill(nx - 1, ny - 1, nx + NODE_SIZE + 1, ny + NODE_SIZE + 1, borderColor);
            guiGraphics.fill(nx, ny, nx + NODE_SIZE, ny + NODE_SIZE, isUnlocked ? 0xFF1A1A24 : 0xFF0A0A0E);

            // Icono
            guiGraphics.renderItem(node.getIcon(), nx + (NODE_SIZE - 16) / 2, ny + (NODE_SIZE - 16) / 2);

            // Detección de cursor encima
            if (mouseX >= nx && mouseX <= nx + NODE_SIZE && mouseY >= ny && mouseY <= ny + NODE_SIZE) {
                hoveredNode = node;
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Título de navegación
        guiGraphics.drawString(this.font, "§6§lÁRBOL DE HABILIDADES §7(Arrastra con el ratón para navegar)", 100, 16, 0xFFFFFF);

        // 5. Tooltip flotante
        if (hoveredNode != null && skills != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§l" + hoveredNode.getDisplayName().getString()));

            String typeBadge = switch (hoveredNode.getType()) {
                case ULTIMATE -> "§6★ DEFINITIVA";
                case HYBRID_SYNERGY -> "§d☯ SINERGIA HÍBRIDA";
                case ACTIVE_ABILITY -> "§e⚡ HABILIDAD ACTIVA";
                default -> "§b◆ PASIVA";
            };
            tooltip.add(Component.literal(typeBadge));
            tooltip.add(Component.literal("§7" + hoveredNode.getDescription().getString()));
            tooltip.add(Component.literal(""));

            boolean isUnlocked = skills.isNodeUnlocked(hoveredNode.getId());
            if (isUnlocked) {
                tooltip.add(Component.literal("§a§l✔ [DESBLOQUEADA]"));
            } else {
                tooltip.add(Component.literal("§eRequisitos:"));
                for (SkillRequirement req : hoveredNode.getRequirements()) {
                    tooltip.add(req.getTooltip(null, skills));
                }
                tooltip.add(Component.literal(""));
                tooltip.add(Component.literal("§a[Clic izquierdo para comprar]"));
            }

            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    private void drawLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        int currX = x1;
        int currY = y1;

        while (true) {
            guiGraphics.fill(currX - 1, currY - 1, currX + 1, currY + 1, color);
            if (currX == x2 && currY == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                currX += sx;
            }
            if (e2 < dx) {
                err += dx;
                currY += sy;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}