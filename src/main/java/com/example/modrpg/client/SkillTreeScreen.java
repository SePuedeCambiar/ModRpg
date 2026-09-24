package com.example.modrpg.client;

import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketEquipSkill;
import com.example.modrpg.networking.PacketUnlockNode;
import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import com.example.modrpg.skills.data.SkillRequirement;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class SkillTreeScreen extends Screen {

    // Desplazamiento y Zoom interactivo
    private double scrollX = 0;
    private double scrollY = 0;
    private double zoom = 1.0;

    private static final int NODE_SIZE = 26;

    // Filtro por rama seleccionada (null = mostrar todas)
    private ResourceLocation selectedBranch = null;

    public SkillTreeScreen() {
        super(Component.literal("Árbol de Habilidades RPG"));
    }

    @Override
    protected void init() {
        super.init();

        // Botón Recentrar
        this.addRenderableWidget(Button.builder(Component.literal("⌖ Recentrar"), btn -> {
            this.scrollX = 0;
            this.scrollY = 0;
            this.zoom = 1.0;
        }).bounds(10, 10, 75, 20).build());

        // Botón Cerrar
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), btn -> this.onClose())
                .bounds(this.width - 65, 10, 55, 20).build());

        // Pestañas de filtrado de ramas
        int tabY = this.height - 28;
        int tabW = 68;
        int startX = (this.width / 2) - ((tabW * 6) / 2);

        this.addRenderableWidget(Button.builder(Component.literal("Todas"), btn -> selectedBranch = null)
                .bounds(startX, tabY, tabW, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("CaC"), btn -> selectedBranch = SkillRegistry.BRANCH_MELEE)
                .bounds(startX + tabW, tabY, tabW, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Arquería"), btn -> selectedBranch = SkillRegistry.BRANCH_RANGED)
                .bounds(startX + (tabW * 2), tabY, tabW, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Magia"), btn -> selectedBranch = SkillRegistry.BRANCH_MAGIC)
                .bounds(startX + (tabW * 3), tabY, tabW, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Movilidad"), btn -> selectedBranch = SkillRegistry.BRANCH_MOBILITY)
                .bounds(startX + (tabW * 4), tabY, tabW, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Defensa"), btn -> selectedBranch = SkillRegistry.BRANCH_DEFENSE)
                .bounds(startX + (tabW * 5), tabY, tabW, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) {
            this.zoom = Math.min(this.zoom * 1.15, 1.8);
        } else if (delta < 0) {
            this.zoom = Math.max(this.zoom / 1.15, 0.55);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 || button == 1 || button == 2) {
            this.scrollX += dragX / zoom;
            this.scrollY += dragY / zoom;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        Player player = Minecraft.getInstance().player;
        if (player == null) return false;

        PlayerSkills skills = player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).orElse(null);
        if (skills == null) return false;

        // Conversión de coordenadas de pantalla a coordenadas del mundo 2D con zoom
        double worldMouseX = (mouseX - (this.width / 2.0)) / zoom - scrollX;
        double worldMouseY = (mouseY - (this.height / 2.0)) / zoom - scrollY;

        for (SkillNode node : SkillRegistry.getAll()) {
            int nx = node.getPosX() - (NODE_SIZE / 2);
            int ny = node.getPosY() - (NODE_SIZE / 2);

            if (worldMouseX >= nx && worldMouseX <= nx + NODE_SIZE && worldMouseY >= ny && worldMouseY <= ny + NODE_SIZE) {
                // CLIC IZQUIERDO (0): Comprar / Desbloquear nodo
                if (button == 0) {
                    if (!skills.isNodeUnlocked(node.getId())) {
                        ModMessages.sendToServer(new PacketUnlockNode(node.getId()));
                        return true;
                    }
                }
                // CLIC DERECHO (1): Equipar / Desequipar en la Rueda Radial
                else if (button == 1) {
                    if (skills.isNodeUnlocked(node.getId())) {
                        ModMessages.sendToServer(new PacketEquipSkill(node.getId()));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.fill(0, 0, this.width, this.height, 0xEE0B0B10);

        Player player = Minecraft.getInstance().player;
        PlayerSkills skills = (player != null) ? player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).orElse(null) : null;

        var pose = guiGraphics.pose();
        pose.pushPose();

        // Aplicar transformación central de cámara con escala de zoom
        pose.translate(this.width / 2.0, this.height / 2.0, 0);
        pose.scale((float) zoom, (float) zoom, 1.0f);
        pose.translate(scrollX, scrollY, 0);

        // 1. Dibujar líneas de conexión entre nodos
        for (SkillNode node : SkillRegistry.getAll()) {
            int startX = node.getPosX();
            int startY = node.getPosY();

            if (node.getParentIds().isEmpty()) {
                boolean isUnlocked = (skills != null && skills.isNodeUnlocked(node.getId()));
                drawLine(guiGraphics, 0, 0, startX, startY, isUnlocked ? 0xFFDAA520 : 0xFF444455);
            } else {
                for (ResourceLocation parentId : node.getParentIds()) {
                    SkillNode parent = SkillRegistry.get(parentId);
                    if (parent != null) {
                        int targetX = parent.getPosX();
                        int targetY = parent.getPosY();
                        boolean isUnlocked = (skills != null && skills.isNodeUnlocked(node.getId()) && skills.isNodeUnlocked(parent.getId()));
                        drawLine(guiGraphics, targetX, targetY, startX, startY, isUnlocked ? 0xFFDAA520 : 0xFF444455);
                    }
                }
            }
        }

        // 2. Nodo central de inicio
        guiGraphics.fill(-16, -16, 16, 16, 0xFF222233);
        guiGraphics.fill(-14, -14, 14, 14, 0xFFDAA520);
        guiGraphics.renderItem(new ItemStack(Items.COMPASS), -8, -8);

        // 3. Dibujar cada nodo del árbol
        double worldMouseX = (mouseX - (this.width / 2.0)) / zoom - scrollX;
        double worldMouseY = (mouseY - (this.height / 2.0)) / zoom - scrollY;
        SkillNode hoveredNode = null;

        for (SkillNode node : SkillRegistry.getAll()) {
            int nx = node.getPosX() - (NODE_SIZE / 2);
            int ny = node.getPosY() - (NODE_SIZE / 2);

            boolean isUnlocked = (skills != null && skills.isNodeUnlocked(node.getId()));
            boolean isEquipped = (skills != null && skills.isSkillEquipped(node.getId()));
            boolean matchesFilter = (selectedBranch == null || node.getBranchId().equals(selectedBranch));

            int borderColor;
            if (isEquipped) {
                borderColor = 0xFFFFFF55; // Borde dorado brillante si está equipada en la rueda
            } else {
                switch (node.getType()) {
                    case ULTIMATE -> borderColor = isUnlocked ? 0xFF55FF55 : 0xFF00AA00;
                    case HYBRID_SYNERGY -> borderColor = isUnlocked ? 0xFFFF55FF : 0xFFAA00AA;
                    case ACTIVE_ABILITY -> borderColor = isUnlocked ? 0xFFFFAA00 : 0xFFCC6600;
                    default -> borderColor = isUnlocked ? 0xFF55FFFF : 0xFF0088AA;
                }
            }

            int alpha = matchesFilter ? 0xFF : 0x44; // Atenuar si no coincide con la pestaña actual
            int finalBorder = (borderColor & 0x00FFFFFF) | (alpha << 24);
            int finalBg = isUnlocked ? ((0x1A1A24 & 0x00FFFFFF) | (alpha << 24)) : ((0x0A0A0E & 0x00FFFFFF) | (alpha << 24));

            guiGraphics.fill(nx - 1, ny - 1, nx + NODE_SIZE + 1, ny + NODE_SIZE + 1, finalBorder);
            guiGraphics.fill(nx, ny, nx + NODE_SIZE, ny + NODE_SIZE, finalBg);
            guiGraphics.renderItem(node.getIcon(), nx + (NODE_SIZE - 16) / 2, ny + (NODE_SIZE - 16) / 2);

            if (worldMouseX >= nx && worldMouseX <= nx + NODE_SIZE && worldMouseY >= ny && worldMouseY <= ny + NODE_SIZE) {
                hoveredNode = node;
            }
        }

        pose.popPose();

        // Renderizado de widgets y botones
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Título e instrucciones superiores
        String branchText = (selectedBranch == null) ? "TODAS LAS RAMAS" : selectedBranch.getPath().toUpperCase();
        guiGraphics.drawString(this.font, "§6§lÁRBOL RPG §7[" + branchText + "]", 95, 12, 0xFFFFFF);
        guiGraphics.drawString(this.font, "§7Rueda: Zoom (" + (int)(zoom * 100) + "%) | Arrastrar: Mover cámara", 95, 23, 0x888888);

        // 4. Tooltip flotante detallado
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

            // Información de maná y recarga
            if (hoveredNode.getManaCost() > 0) {
                tooltip.add(Component.literal("§b⚡ Coste: §f" + (int) hoveredNode.getManaCost() + " Maná"));
            }
            if (hoveredNode.getDefaultCooldownTicks() > 0) {
                tooltip.add(Component.literal("§c⏳ Enfriamiento: §f" + (hoveredNode.getDefaultCooldownTicks() / 20) + "s"));
            }
            tooltip.add(Component.literal(""));

            boolean isUnlocked = skills.isNodeUnlocked(hoveredNode.getId());
            boolean isEquipped = skills.isSkillEquipped(hoveredNode.getId());

            if (isUnlocked) {
                tooltip.add(Component.literal("§a§l✔ [DESBLOQUEADA]"));
                if (hoveredNode.getType() == SkillNode.NodeType.ACTIVE_ABILITY || hoveredNode.getType() == SkillNode.NodeType.ULTIMATE) {
                    if (isEquipped) {
                        tooltip.add(Component.literal("§e⭐ [EQUIPADA EN RUEDA RADIAL]"));
                        tooltip.add(Component.literal("§6[Clic Derecho para desequipar]"));
                    } else {
                        tooltip.add(Component.literal("§7[Clic Derecho para equipar en rueda]"));
                    }
                }
            } else {
                tooltip.add(Component.literal("§eRequisitos para aprender:"));
                for (SkillRequirement req : hoveredNode.getRequirements()) {
                    tooltip.add(req.getTooltip(null, skills));
                }
                tooltip.add(Component.literal(""));
                tooltip.add(Component.literal("§a[Clic Izquierdo para comprar]"));
            }

            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    private void drawLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        float distance = (float) Math.hypot(dx, dy);
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x1, y1, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        guiGraphics.fill(0, -1, (int) distance, 1, color);
        pose.popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}