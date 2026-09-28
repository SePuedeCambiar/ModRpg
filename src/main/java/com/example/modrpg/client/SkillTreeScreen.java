package com.example.modrpg.client;

import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketEquipSkill;
import com.example.modrpg.networking.PacketUnlockNode;
import com.example.modrpg.networking.PacketUpgradeSkill;
import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillEconomy;
import com.example.modrpg.skills.data.SkillBranch;
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
import java.util.Objects;

public class SkillTreeScreen extends Screen {

    private double scrollX = 0;
    private double scrollY = 0;
    private double zoom = 1.0;

    private static final int NODE_SIZE = 26;

    private ResourceLocation selectedBranch = null;
    private Button upgradeBranchButton;

    public SkillTreeScreen() {
        super(Component.literal("Árbol de Habilidades RPG"));
    }

    @Override
    protected void init() {
        super.init();

        // 1. Botón Recentrar
        this.addRenderableWidget(Button.builder(Component.literal("⌖ Recentrar"), btn -> {
            this.scrollX = 0;
            this.scrollY = 0;
            this.zoom = 1.0;
        }).bounds(10, 10, 75, 20).build());

        // 2. Botón Cerrar
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), btn -> this.onClose())
                .bounds(this.width - 65, 10, 55, 20).build());

        // 3. Pestañas de filtrado de ramas
        int tabY = this.height - 26;
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

        // 4. Botón Dinámico de Mejora
        int upgradeBtnW = 260;
        int upgradeBtnX = (this.width / 2) - (upgradeBtnW / 2);
        int upgradeBtnY = this.height - 52;

        this.upgradeBranchButton = this.addRenderableWidget(Button.builder(
                Component.literal("🔼 Mejorar Rama"),
                btn -> {
                    if (selectedBranch != null) {
                        ModMessages.sendToServer(new PacketUpgradeSkill(selectedBranch.getPath()));
                    }
                }
        ).bounds(upgradeBtnX, upgradeBtnY, upgradeBtnW, 20).build());
    }

    private void updateUpgradeButton(Player player, PlayerSkills skills) {
        if (this.upgradeBranchButton == null) return;

        if (selectedBranch == null) {
            this.upgradeBranchButton.visible = false;
            return;
        }

        this.upgradeBranchButton.visible = true;
        SkillBranch branch = SkillRegistry.getBranch(selectedBranch);
        if (branch == null || skills == null || player == null) {
            this.upgradeBranchButton.active = false;
            return;
        }

        int currentLvl = skills.getBranchLevel(selectedBranch);
        int maxAllowed = skills.getMaxLevelForBranch(selectedBranch);

        // A. Validar Nivel Máximo Absoluto (100)
        if (currentLvl >= SkillEconomy.MAX_LEVEL) {
            this.upgradeBranchButton.setMessage(Component.literal("§6★ Rama al Nivel Máximo (100)"));
            this.upgradeBranchButton.active = false;
            return;
        }

        // B. Validar Límites de Especialización (Sprint 4)
        if (currentLvl >= maxAllowed) {
            ResourceLocation primary = skills.getPrimaryBranch();
            ResourceLocation secondary = skills.getSecondaryBranch();

            if (maxAllowed == PlayerSkills.CAP_BASE) { // 20
                if (primary == null) {
                    this.upgradeBranchButton.setMessage(Component.literal("§6★ Sellar como Rama Principal (Nvl 21)"));
                } else if (secondary == null && !primary.equals(selectedBranch)) {
                    this.upgradeBranchButton.setMessage(Component.literal("§b☯ Sellar como Rama Secundaria (Nvl 21)"));
                } else {
                    this.upgradeBranchButton.setMessage(Component.literal("§c🔒 Tope Rama Básica (20/20) - Ver /rpg respec"));
                    this.upgradeBranchButton.active = false;
                    return;
                }
            } else if (maxAllowed == PlayerSkills.CAP_SECONDARY) { // 50
                this.upgradeBranchButton.setMessage(Component.literal("§c🔒 Tope Rama Secundaria (50/50)"));
                this.upgradeBranchButton.active = false;
                return;
            }
        }

        int nextLvl = currentLvl + 1;

        // C. Validar Pruebas de Ascensión
        int eliteKills = skills.getPractice(SkillRegistry.COUNTER_ELITE_KILLS);
        if (nextLvl == 26 && eliteKills < 3) {
            this.upgradeBranchButton.setMessage(Component.literal("§c🔒 Prueba Iniciado Req. (" + eliteKills + "/3 Élites)"));
            this.upgradeBranchButton.active = false;
            return;
        }
        if (nextLvl == 51 && eliteKills < 10) {
            this.upgradeBranchButton.setMessage(Component.literal("§c🔒 Prueba Maestro Req. (" + eliteKills + "/10 Élites)"));
            this.upgradeBranchButton.active = false;
            return;
        }
        if (nextLvl == 76 && eliteKills < 25) {
            this.upgradeBranchButton.setMessage(Component.literal("§c🔒 Prueba Gran Maestro Req. (" + eliteKills + "/25 Élites)"));
            this.upgradeBranchButton.active = false;
            return;
        }

        // D. Validar Economía (Práctica y XP)
        int xpCost = SkillEconomy.getXpCost(currentLvl);
        int practiceNeeded = SkillEconomy.getRequiredPractice(nextLvl);
        int playerPractice = skills.getPractice(branch.practiceCounterId());

        boolean meetsMinUnlock = currentLvl > 0 || player.experienceLevel >= branch.minPlayerXpToUnlock();
        boolean hasPractice = playerPractice >= practiceNeeded;
        boolean hasXp = player.experienceLevel >= xpCost;

        boolean canUpgrade = meetsMinUnlock && hasPractice && hasXp;
        this.upgradeBranchButton.active = canUpgrade;

        if (!meetsMinUnlock) {
            this.upgradeBranchButton.setMessage(Component.literal("§c🔒 Requiere Nivel " + branch.minPlayerXpToUnlock() + " XP"));
        } else if (!hasPractice) {
            this.upgradeBranchButton.setMessage(Component.literal("§cFalta Práctica (" + playerPractice + "/" + practiceNeeded + ")"));
        } else if (!hasXp) {
            this.upgradeBranchButton.setMessage(Component.literal("§cFalta XP (" + xpCost + " Niveles Req.)"));
        } else {
            this.upgradeBranchButton.setMessage(Component.literal("§a🔼 Subir a Nvl " + nextLvl + " §e(-" + xpCost + " Niveles XP)"));
        }
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

        double worldMouseX = (mouseX - (this.width / 2.0)) / zoom - scrollX;
        double worldMouseY = (mouseY - (this.height / 2.0)) / zoom - scrollY;

        for (SkillNode node : SkillRegistry.getAll()) {
            int nx = node.getPosX() - (NODE_SIZE / 2);
            int ny = node.getPosY() - (NODE_SIZE / 2);

            if (worldMouseX >= nx && worldMouseX <= nx + NODE_SIZE && worldMouseY >= ny && worldMouseY <= ny + NODE_SIZE) {
                if (button == 0) {
                    if (!skills.isNodeUnlocked(node.getId())) {
                        ModMessages.sendToServer(new PacketUnlockNode(node.getId()));
                        return true;
                    }
                } else if (button == 1) {
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

        updateUpgradeButton(player, skills);

        var pose = guiGraphics.pose();
        pose.pushPose();

        pose.translate(this.width / 2.0, this.height / 2.0, 0);
        pose.scale((float) zoom, (float) zoom, 1.0f);
        pose.translate(scrollX, scrollY, 0);

        // Líneas de conexión
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

        // Nodo central
        guiGraphics.fill(-16, -16, 16, 16, 0xFF222233);
        guiGraphics.fill(-14, -14, 14, 14, 0xFFDAA520);
        guiGraphics.renderItem(new ItemStack(Items.COMPASS), -8, -8);

        // Nodos del árbol
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
                borderColor = 0xFFFFFF55;
            } else {
                switch (node.getType()) {
                    case ULTIMATE -> borderColor = isUnlocked ? 0xFF55FF55 : 0xFF00AA00;
                    case HYBRID_SYNERGY -> borderColor = isUnlocked ? 0xFFFF55FF : 0xFFAA00AA;
                    case ACTIVE_ABILITY -> borderColor = isUnlocked ? 0xFFFFAA00 : 0xFFCC6600;
                    default -> borderColor = isUnlocked ? 0xFF55FFFF : 0xFF0088AA;
                }
            }

            int alpha = matchesFilter ? 0xFF : 0x44;
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

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Panel de Estado Superior con Insignia de Especialización
        String branchText = "TODAS LAS RAMAS";
        if (selectedBranch != null && skills != null) {
            String specTag;
            if (Objects.equals(selectedBranch, skills.getPrimaryBranch())) specTag = " §6★ [MAESTRÍA / MÁX 100]";
            else if (Objects.equals(selectedBranch, skills.getSecondaryBranch())) specTag = " §b☯ [SECUNDARIA / MÁX 50]";
            else specTag = " §7◆ [BÁSICA / MÁX 20]";
            branchText = selectedBranch.getPath().toUpperCase() + specTag;
        }

        guiGraphics.drawString(this.font, "§6§lÁRBOL RPG §7[" + branchText + "]", 95, 12, 0xFFFFFF);
        guiGraphics.drawString(this.font, "§7Rueda: Zoom (" + (int)(zoom * 100) + "%) | Arrastrar: Mover cámara", 95, 23, 0x888888);

        // Información de la rama seleccionada
        if (selectedBranch != null && skills != null) {
            SkillBranch branch = SkillRegistry.getBranch(selectedBranch);
            if (branch != null) {
                int lvl = skills.getBranchLevel(selectedBranch);
                int maxAllowed = skills.getMaxLevelForBranch(selectedBranch);
                int practice = skills.getPractice(branch.practiceCounterId());
                int nextLvl = lvl + 1;
                int reqPractice = SkillEconomy.getRequiredPractice(nextLvl);

                String branchInfo = "§e" + branch.displayName().getString() + ": §fNivel " + lvl + "/" + maxAllowed + "  §7|  Práctica: §b" + practice + (lvl < maxAllowed ? "/" + reqPractice : " §6[LÍMITE]");
                guiGraphics.drawCenteredString(this.font, branchInfo, this.width / 2, this.height - 64, 0xFFFFFF);
            }
        }

        // Tooltip del nodo
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
                    tooltip.add(req.getTooltip(player, skills));
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