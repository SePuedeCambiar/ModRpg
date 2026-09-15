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

    private final int panelWidth = 370;
    private final int panelHeight = 250;
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
        }).bounds(leftPos + 275, topPos + 38, 75, 20).build());

        // Botón Subir Distancia
        this.addRenderableWidget(Button.builder(Component.literal("+ Subir"), btn -> {
            ModMessages.sendToServer(new PacketUpgradeSkill("ranged"));
        }).bounds(leftPos + 275, topPos + 80, 75, 20).build());

        // Botón Subir Movilidad
        this.addRenderableWidget(Button.builder(Component.literal("+ Subir"), btn -> {
            ModMessages.sendToServer(new PacketUpgradeSkill("mobility"));
        }).bounds(leftPos + 275, topPos + 122, 75, 20).build());

        // Botón Cerrar
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), btn -> this.onClose())
                .bounds(leftPos + (panelWidth / 2) - 45, topPos + panelHeight - 22, 90, 18).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // Fondo del panel
        guiGraphics.fill(leftPos, topPos, leftPos + panelWidth, topPos + panelHeight, 0xF0141418);

        // Bordes dorados decorativos
        guiGraphics.fill(leftPos - 1, topPos - 1, leftPos + panelWidth + 1, topPos, 0xFFDAA520);
        guiGraphics.fill(leftPos - 1, topPos + panelHeight, leftPos + panelWidth + 1, topPos + panelHeight + 1, 0xFFDAA520);
        guiGraphics.fill(leftPos - 1, topPos, leftPos, topPos + panelHeight, 0xFFDAA520);
        guiGraphics.fill(leftPos + panelWidth, topPos, leftPos + panelWidth + 1, topPos + panelHeight, 0xFFDAA520);

        guiGraphics.drawCenteredString(this.font, "§6§l⚔ ÁRBOL DE HABILIDADES RPG ⚔",
                leftPos + (panelWidth / 2), topPos + 10, 0xFFFFFF);

        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                // ================= RAMA 1: MELEE =================
                int meleeLvl = skills.getMeleeLevel();
                int nextMeleeKills = SkillEconomy.getRequiredKills(meleeLvl + 1);
                int meleeCost = SkillEconomy.getXpCost(meleeLvl);

                guiGraphics.drawString(this.font, "§c§l⚔ Combate CaC: §fNivel " + meleeLvl + "/100", leftPos + 16, topPos + 34, 0xFFFFFF, false);
                guiGraphics.drawString(this.font, "§7Bajas: §e" + skills.getMeleeKills() + "/" + nextMeleeKills + " §7| Costo: §a" + meleeCost + " XP", leftPos + 16, topPos + 45, 0xAAAAAA, false);
                drawProgressBar(guiGraphics, leftPos + 16, topPos + 56, 245, 6, meleeLvl, 0xFFFF3333);

                // ================= RAMA 2: DISTANCIA =================
                int rangedLvl = skills.getRangedLevel();
                int nextRangedKills = SkillEconomy.getRequiredKills(rangedLvl + 1);
                int rangedCost = SkillEconomy.getXpCost(rangedLvl);

                guiGraphics.drawString(this.font, "§b§l🏹 Arquería: §fNivel " + rangedLvl + "/100", leftPos + 16, topPos + 76, 0xFFFFFF, false);
                guiGraphics.drawString(this.font, "§7Bajas: §e" + skills.getRangedKills() + "/" + nextRangedKills + " §7| Costo: §a" + rangedCost + " XP", leftPos + 16, topPos + 87, 0xAAAAAA, false);
                drawProgressBar(guiGraphics, leftPos + 16, topPos + 98, 245, 6, rangedLvl, 0xFF33CCFF);

                // ================= RAMA 3: MOVILIDAD =================
                int mobLvl = skills.getMobilityLevel();
                int totalKills = skills.getMeleeKills() + skills.getRangedKills();
                int nextMobKills = SkillEconomy.getRequiredKills(mobLvl + 1);
                int mobCost = SkillEconomy.getXpCost(mobLvl);

                guiGraphics.drawString(this.font, "§a§l🏃 Movilidad: §fNivel " + mobLvl + "/100", leftPos + 16, topPos + 118, 0xFFFFFF, false);
                guiGraphics.drawString(this.font, "§7Práctica: §e" + totalKills + "/" + nextMobKills + " §7| Costo: §a" + mobCost + " XP", leftPos + 16, topPos + 129, 0xAAAAAA, false);
                drawProgressBar(guiGraphics, leftPos + 16, topPos + 140, 245, 6, mobLvl, 0xFF33FF66);

                // ================= SECCIÓN DE TALENTOS =================
                guiGraphics.fill(leftPos + 12, topPos + 155, leftPos + panelWidth - 12, topPos + 220, 0x55000000);
                guiGraphics.drawString(this.font, "§eTalentos CaC y Arquería Desbloqueados:", leftPos + 16, topPos + 160, 0xFFFFFF, false);

                // Fila 1: Nivel Bajo
                String doubleStatus = skills.hasDoubleAttack() ? "§a✔ [Activo]" : "§c✖ [Req. CaC 4]";
                guiGraphics.drawString(this.font, "§c⚔ Doble Ataque: " + doubleStatus, leftPos + 16, topPos + 173, 0xFFFFFF, false);

                String tailwindStatus = skills.hasTailwind() ? "§a✔ [Activo]" : "§c✖ [Req. Arq 5]";
                guiGraphics.drawString(this.font, "§b💨 Viento a Favor: " + tailwindStatus, leftPos + 185, topPos + 173, 0xFFFFFF, false);

                // Fila 2: Nivel Medio
                String spinStatus = skills.hasSpinAttack() ? "§a✔ [Tecla V]" : "§c✖ [Req. CaC 20]";
                guiGraphics.drawString(this.font, "§b🌀 Torbellino: " + spinStatus, leftPos + 16, topPos + 188, 0xFFFFFF, false);

                String hybridStatus = skills.hasHybridRangedMelee() ? "§a✔ [Activo]" : "§c✖ [Req. 25/25]";
                guiGraphics.drawString(this.font, "§d☯ Cazador Híbrido: " + hybridStatus, leftPos + 185, topPos + 188, 0xFFFFFF, false);

                // Fila 3: Maestría
                String capstoneStatus = skills.hasCapstoneMelee() ? "§a✔ [Tecla R]" : "§c✖ [Req. CaC 50]";
                guiGraphics.drawString(this.font, "§6⚡ Golpe 500%: " + capstoneStatus, leftPos + 16, topPos + 203, 0xFFFFFF, false);

                String hypersonicStatus = skills.hasHypersonicArrow() ? "§a✔ [Sneak+Tiro]" : "§c✖ [Req. Arq 50]";
                guiGraphics.drawString(this.font, "§9⚡ Hipersónica: " + hypersonicStatus, leftPos + 185, topPos + 203, 0xFFFFFF, false);
            });
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int x, int y, int width, int height, int level, int color) {
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2A2A2E);
        int filledWidth = (int) ((level / 100.0f) * width);
        if (filledWidth > 0) {
            guiGraphics.fill(x, y, x + filledWidth, y + height, color);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}