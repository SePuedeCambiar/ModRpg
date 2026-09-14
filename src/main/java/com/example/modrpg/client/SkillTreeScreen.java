package com.example.modrpg.client;

import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketUpgradeSkill;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillEconomy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class SkillTreeScreen extends Screen {

    private final int panelWidth = 340;
    private final int panelHeight = 230;
    private int leftPos;
    private int topPos;

    public SkillTreeScreen() {
        super(Component.literal("Árbol de Habilidades RPG"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.panelWidth) / 2;
        this.topPos = (this.height - this.panelHeight) / 2;

        // Botón Subir Melee
        this.addRenderableWidget(Button.builder(Component.literal("+ Subir"), btn -> {
            ModMessages.sendToServer(new PacketUpgradeSkill("melee"));
        }).bounds(leftPos + 245, topPos + 40, 75, 20).build());

        // Botón Subir Distancia
        this.addRenderableWidget(Button.builder(Component.literal("+ Subir"), btn -> {
            ModMessages.sendToServer(new PacketUpgradeSkill("ranged"));
        }).bounds(leftPos + 245, topPos + 85, 75, 20).build());

        // Botón Subir Movilidad
        this.addRenderableWidget(Button.builder(Component.literal("+ Subir"), btn -> {
            ModMessages.sendToServer(new PacketUpgradeSkill("mobility"));
        }).bounds(leftPos + 245, topPos + 130, 75, 20).build());

        // Botón Cerrar
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), btn -> this.onClose())
                .bounds(leftPos + (panelWidth / 2) - 45, topPos + panelHeight - 24, 90, 18).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Fondo translúcido estándar de Minecraft
        this.renderBackground(guiGraphics);

        // Fondo del panel principal (Gris oscuro elegante)
        guiGraphics.fill(leftPos, topPos, leftPos + panelWidth, topPos + panelHeight, 0xF0151518);

        // Bordes dorados decorativos
        guiGraphics.fill(leftPos - 1, topPos - 1, leftPos + panelWidth + 1, topPos, 0xFFDAA520);
        guiGraphics.fill(leftPos - 1, topPos + panelHeight, leftPos + panelWidth + 1, topPos + panelHeight + 1, 0xFFDAA520);
        guiGraphics.fill(leftPos - 1, topPos, leftPos, topPos + panelHeight, 0xFFDAA520);
        guiGraphics.fill(leftPos + panelWidth, topPos, leftPos + panelWidth + 1, topPos + panelHeight, 0xFFDAA520);

        // Título del Menú
        guiGraphics.drawCenteredString(this.font, "§6§l⚔ ÁRBOL DE HABILIDADES RPG ⚔",
                leftPos + (panelWidth / 2), topPos + 12, 0xFFFFFF);

        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                // ================= RAMA 1: MELEE =================
                int meleeLvl = skills.getMeleeLevel();
                int nextMeleeKills = SkillEconomy.getRequiredKills(meleeLvl + 1);
                int meleeCost = SkillEconomy.getXpCost(meleeLvl);

                guiGraphics.drawString(this.font, "§c§l⚔ Combate Melee: §fNivel " + meleeLvl + "/100", leftPos + 16, topPos + 36, 0xFFFFFF, false);
                guiGraphics.drawString(this.font, "§7Bajas: §e" + skills.getMeleeKills() + "/" + nextMeleeKills + " §7| Costo: §a" + meleeCost + " XP", leftPos + 16, topPos + 47, 0xAAAAAA, false);
                drawProgressBar(guiGraphics, leftPos + 16, topPos + 58, 215, 6, meleeLvl, 0xFFFF3333);

                // ================= RAMA 2: DISTANCIA =================
                int rangedLvl = skills.getRangedLevel();
                int nextRangedKills = SkillEconomy.getRequiredKills(rangedLvl + 1);
                int rangedCost = SkillEconomy.getXpCost(rangedLvl);

                guiGraphics.drawString(this.font, "§b§l🏹 Arquería / Distancia: §fNivel " + rangedLvl + "/100", leftPos + 16, topPos + 81, 0xFFFFFF, false);
                guiGraphics.drawString(this.font, "§7Bajas: §e" + skills.getRangedKills() + "/" + nextRangedKills + " §7| Costo: §a" + rangedCost + " XP", leftPos + 16, topPos + 92, 0xAAAAAA, false);
                drawProgressBar(guiGraphics, leftPos + 16, topPos + 103, 215, 6, rangedLvl, 0xFF33CCFF);

                // ================= RAMA 3: MOVILIDAD =================
                int mobLvl = skills.getMobilityLevel();
                int totalKills = skills.getMeleeKills() + skills.getRangedKills();
                int nextMobKills = SkillEconomy.getRequiredKills(mobLvl + 1);
                int mobCost = SkillEconomy.getXpCost(mobLvl);

                guiGraphics.drawString(this.font, "§a§l🏃 Movilidad: §fNivel " + mobLvl + "/100", leftPos + 16, topPos + 126, 0xFFFFFF, false);
                guiGraphics.drawString(this.font, "§7Práctica: §e" + totalKills + "/" + nextMobKills + " §7| Costo: §a" + mobCost + " XP", leftPos + 16, topPos + 137, 0xAAAAAA, false);
                drawProgressBar(guiGraphics, leftPos + 16, topPos + 148, 215, 6, mobLvl, 0xFF33FF66);

                // ================= SECCIÓN DE HABILIDADES MAESTRAS =================
                guiGraphics.fill(leftPos + 14, topPos + 165, leftPos + panelWidth - 14, topPos + 198, 0x55000000);
                guiGraphics.drawString(this.font, "§eHabilidades Maestras:", leftPos + 18, topPos + 170, 0xFFFFFF, false);

                String capstoneStatus = skills.hasCapstoneMelee() ? "§a§l[DESBLOQUEADO]" : "§c§l[BLOQUEADO - Req. Nvl 50]";
                guiGraphics.drawString(this.font, "§6⚡ Golpe Definitivo [R]: " + capstoneStatus, leftPos + 18, topPos + 182, 0xFFFFFF, false);

                String hybridStatus = skills.hasHybridRangedMelee() ? "§a§l[DESBLOQUEADO]" : "§c§l[BLOQUEADO - Req. Nvl 25/25]";
                guiGraphics.drawString(this.font, "§d☯ Rama Híbrida: " + hybridStatus, leftPos + 175, topPos + 182, 0xFFFFFF, false);
            });
        }

        // Renderiza los botones interactivos
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int x, int y, int width, int height, int level, int color) {
        // Fondo de la barra
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2A2A2E);
        // Barra rellena según el nivel (0 a 100)
        int filledWidth = (int) ((level / 100.0f) * width);
        if (filledWidth > 0) {
            guiGraphics.fill(x, y, x + filledWidth, y + height, color);
        }
    }

    @Override
    public boolean isPauseScreen() {
        // Devuelve false para que el juego continúe corriendo de fondo (ideal para multijugador)
        return false;
    }
}