package com.example.modrpg.client;

import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketSaveCraftedSpell;
import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.magic.modular.CraftedSpell;
import com.example.modrpg.skills.magic.modular.SpellElement;
import com.example.modrpg.skills.magic.modular.SpellShape;
import com.example.modrpg.skills.magic.modular.SpellTiming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class SpellCraftingScreen extends Screen {

    private int elementIndex = 0;
    private int shapeIndex = 0;
    private int timingIndex = 1; // BALANCED por defecto
    private int selectedSlot = 0; // Ranura 1 (índice 0)

    private EditBox nameInput;
    private Button elementBtn;
    private Button shapeBtn;
    private Button timingBtn;
    private Button saveBtn;

    public SpellCraftingScreen() {
        super(Component.literal("Altar de Creación de Hechizos"));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 45;

        // Caja de texto para el nombre
        this.nameInput = new EditBox(this.font, centerX - 100, startY, 200, 20, Component.literal("Nombre del Hechizo"));
        this.nameInput.setValue(getCurrentElement().getDisplayName() + " " + getCurrentShape().getDisplayName());
        this.addRenderableWidget(this.nameInput);

        // Botón Elemento
        this.elementBtn = this.addRenderableWidget(Button.builder(
                Component.literal("Elemento: " + getCurrentElement().getColorCode() + getCurrentElement().getDisplayName()),
                btn -> {
                    elementIndex = (elementIndex + 1) % SpellElement.values().length;
                    updateButtons();
                }
        ).bounds(centerX - 100, startY + 30, 200, 20).build());

        // Botón Forma
        this.shapeBtn = this.addRenderableWidget(Button.builder(
                Component.literal("Forma: §f" + getCurrentShape().getDisplayName()),
                btn -> {
                    shapeIndex = (shapeIndex + 1) % SpellShape.values().length;
                    updateButtons();
                }
        ).bounds(centerX - 100, startY + 55, 200, 20).build());

        // Botón Cadencia / Modificador
        this.timingBtn = this.addRenderableWidget(Button.builder(
                Component.literal("Cadencia: §f" + getCurrentTiming().getDisplayName()),
                btn -> {
                    timingIndex = (timingIndex + 1) % SpellTiming.values().length;
                    updateButtons();
                }
        ).bounds(centerX - 100, startY + 80, 200, 20).build());

        // Selector de Ranuras [1] [2] [3] [4]
        int slotBtnWidth = 46;
        for (int i = 0; i < 4; i++) {
            final int slot = i;
            this.addRenderableWidget(Button.builder(
                    Component.literal("Ranura " + (slot + 1)),
                    btn -> this.selectedSlot = slot
            ).bounds(centerX - 100 + (slot * (slotBtnWidth + 5)), startY + 110, slotBtnWidth, 20).build());
        }

        // Botón Guardar Hechizo
        this.saveBtn = this.addRenderableWidget(Button.builder(
                Component.literal("§a§l✔ [ GUARDAR HECHIZO ]"),
                btn -> {
                    String spellName = nameInput.getValue().trim();
                    if (spellName.isEmpty()) spellName = "Hechizo Arcano";

                    CraftedSpell spell = new CraftedSpell(
                            spellName,
                            getCurrentElement(),
                            getCurrentShape(),
                            getCurrentTiming(),
                            1
                    );

                    ModMessages.sendToServer(new PacketSaveCraftedSpell(selectedSlot, spell));

                    Player player = Minecraft.getInstance().player;
                    if (player != null) {
                        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(s -> s.setSpell(selectedSlot, spell));
                    }
                    this.onClose();
                }
        ).bounds(centerX - 100, startY + 195, 200, 22).build());

        // Botón Cerrar
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), btn -> this.onClose())
                .bounds(this.width - 65, 10, 55, 20).build());
    }

    private SpellElement getCurrentElement() { return SpellElement.values()[elementIndex]; }
    private SpellShape getCurrentShape() { return SpellShape.values()[shapeIndex]; }
    private SpellTiming getCurrentTiming() { return SpellTiming.values()[timingIndex]; }

    private void updateButtons() {
        this.elementBtn.setMessage(Component.literal("Elemento: " + getCurrentElement().getColorCode() + getCurrentElement().getDisplayName()));
        this.shapeBtn.setMessage(Component.literal("Forma: §f" + getCurrentShape().getDisplayName()));
        this.timingBtn.setMessage(Component.literal("Cadencia: §f" + getCurrentTiming().getDisplayName()));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.fill(0, 0, this.width, this.height, 0xEE0A0A12);

        int centerX = this.width / 2;

        guiGraphics.drawCenteredString(this.font, "§6§lALTAR DE CREACIÓN DE HECHIZOS", centerX, 15, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, "§7Diseña tu magia modular y guárdala en tu memoria", centerX, 27, 0x888888);

        // Panel de Estadísticas en Vivo
        CraftedSpell preview = new CraftedSpell(
                nameInput.getValue(), getCurrentElement(), getCurrentShape(), getCurrentTiming(), 1
        );

        float dmg = preview.calculateDamage(Minecraft.getInstance().player);
        float mana = preview.calculateManaCost();
        float cd = preview.calculateCooldownTicks() / 20.0f;

        int boxY = 180;
        guiGraphics.fill(centerX - 100, boxY - 42, centerX + 100, boxY + 8, 0xCC141420);
        guiGraphics.renderOutline(centerX - 100, boxY - 42, 200, 50, 0xFF00AAFF);

        // Icono del elemento
        guiGraphics.renderItem(new ItemStack(getCurrentElement().getIconItem()), centerX - 92, boxY - 34);

        // Estadísticas estimadas
        guiGraphics.drawString(this.font, "§eDaño: §f" + String.format("%.1f", dmg) + " PV", centerX - 65, boxY - 36, 0xFFFFFF);
        guiGraphics.drawString(this.font, "§bManá: §f" + (int) mana + " puntos", centerX - 65, boxY - 24, 0xFFFFFF);
        guiGraphics.drawString(this.font, "§cCooldown: §f" + String.format("%.1f", cd) + "s", centerX - 65, boxY - 12, 0xFFFFFF);

        guiGraphics.drawCenteredString(this.font, "§dDestino: §fRanura " + (selectedSlot + 1) + " / 4", centerX + 45, boxY - 24, 0xFFFFFF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}