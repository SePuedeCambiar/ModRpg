package com.example.modrpg.client;

import com.example.modrpg.ai.EnemyRpgManager;
import com.example.modrpg.ai.TacticalCasterGoal;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class ChampionOverlay {

    private static LivingEntity currentTarget = null;
    private static int displayTimer = 0;

    public static final IGuiOverlay HUD_CHAMPION = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) return;

        // 1. Detección del objetivo enfocado en la mira
        HitResult hit = mc.hitResult;
        LivingEntity candidate = (hit instanceof EntityHitResult eHit && eHit.getEntity() instanceof LivingEntity living) ? living : null;

        if (candidate instanceof Monster monster && monster.isAlive() &&
                (monster.getTags().contains("modrpg_champion") || monster.getTags().contains(EnemyRpgManager.TAG_CASTER))) {
            currentTarget = monster;
            displayTimer = 60; // Permanece en pantalla 3 segundos para no parpadear en combate
        } else if (displayTimer > 0) {
            displayTimer--;
            if (currentTarget != null && !currentTarget.isAlive()) {
                currentTarget = null;
                displayTimer = 0;
            }
        } else {
            currentTarget = null;
        }

        if (currentTarget == null) return;

        // 2. Coordenadas y Dimensiones del Marco
        int barWidth = 160;
        int barHeight = 8;
        int x = (screenWidth / 2) - (barWidth / 2);
        int y = 18;

        float hp = currentTarget.getHealth();
        float maxHp = currentTarget.getMaxHealth();
        float percentage = Math.min(1.0f, Math.max(0.0f, hp / maxHp));
        int filledWidth = (int) (barWidth * percentage);

        boolean isStaggered = currentTarget.getTags().contains(TacticalCasterGoal.TAG_STAGGERED);
        boolean isChampion = currentTarget.getTags().contains("modrpg_champion");

        // Color de marco: Dorado si es Campeón, Amarillo parpadeante si está Aturdido, Púrpura si es Caster
        int borderColor;
        if (isStaggered) {
            borderColor = (mc.player.tickCount % 6 < 3) ? 0xFFFFFFFF : 0xFFFFAA00;
        } else if (isChampion) {
            borderColor = 0xFFDAA520;
        } else {
            borderColor = 0xFF8844AA;
        }

        // Fondo y Relleno de la barra
        guiGraphics.fill(x - 2, y - 2, x + barWidth + 2, y + barHeight + 2, 0xFF000000);
        guiGraphics.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, borderColor);
        guiGraphics.fill(x, y, x + barWidth, y + barHeight, 0xFF14141E);

        // Barra de Vida: Rojo intenso para mobs normales, Dorado/Rojo para Campeones
        int healthColor = isStaggered ? 0xFFFFAA00 : (isChampion ? 0xFFE63900 : 0xFFCC2222);
        guiGraphics.fill(x, y, x + filledWidth, y + barHeight, healthColor);

        // 3. Título del Monstruo
        Component name = currentTarget.getCustomName() != null ? currentTarget.getCustomName() : currentTarget.getName();
        String titleStr = name.getString();
        guiGraphics.drawCenteredString(mc.font, titleStr, screenWidth / 2, y - 12, 0xFFFFFF);

        // 4. Texto Numérico de Vida
        String hpText = (int) hp + " / " + (int) maxHp;
        guiGraphics.drawCenteredString(mc.font, "§f" + hpText, screenWidth / 2, y, 0xFFFFFF);

        // 5. Alerta de Postura Rota / Estados Alterados
        if (isStaggered) {
            guiGraphics.drawCenteredString(mc.font, "§e§l⚡ ¡POSTURA ROTA! (+30% Daño) ⚡", screenWidth / 2, y + 11, 0xFFFFFF);
        } else if (currentTarget.getPersistentData().contains("modrpg_primer_elem")) {
            String primer = currentTarget.getPersistentData().getString("modrpg_primer_elem");
            String badge = switch (primer) {
                case "FROST" -> "§b[❄ Hielo]";
                case "FIRE" -> "§c[🔥 Fuego]";
                case "LIGHTNING" -> "§e[⚡ Rayo]";
                case "VOID" -> "§5[🌌 Vacío]";
                case "HOLY" -> "§e[✨ Sagrado]";
                default -> "";
            };
            if (!badge.isEmpty()) {
                guiGraphics.drawCenteredString(mc.font, "§7Elemento cebado: " + badge, screenWidth / 2, y + 11, 0xAAAAAA);
            }
        }
    };
}