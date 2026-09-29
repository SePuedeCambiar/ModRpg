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
                (monster.getTags().contains("modrpg_nemesis_captain") ||
                        monster.getTags().contains("modrpg_champion") ||
                        monster.getTags().contains(EnemyRpgManager.TAG_CASTER))) {
            currentTarget = monster;
            displayTimer = 60; // 3 segundos en pantalla
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
        int barWidth = 170;
        int barHeight = 8;
        int x = (screenWidth / 2) - (barWidth / 2);
        int y = 18;

        float hp = currentTarget.getHealth();
        float maxHp = currentTarget.getMaxHealth();
        float percentage = Math.min(1.0f, Math.max(0.0f, hp / maxHp));
        int filledWidth = (int) (barWidth * percentage);

        boolean isNemesis = currentTarget.getTags().contains("modrpg_nemesis_captain");
        boolean isStaggered = currentTarget.getTags().contains(TacticalCasterGoal.TAG_STAGGERED);
        boolean isChampion = currentTarget.getTags().contains("modrpg_champion");

        // Color de marco: Carmesí Sangriento si es Némesis, Dorado si es Campeón, Púrpura si es Caster
        int borderColor;
        if (isStaggered) {
            borderColor = (mc.player.tickCount % 6 < 3) ? 0xFFFFFFFF : 0xFFFFAA00;
        } else if (isNemesis) {
            borderColor = 0xFFFF2222; // Borde rojo carmesí brillante para Némesis
        } else if (isChampion) {
            borderColor = 0xFFDAA520; // Dorado élite
        } else {
            borderColor = 0xFF8844AA; // Púrpura caster
        }

        // Fondo y Relleno de la barra
        guiGraphics.fill(x - 2, y - 2, x + barWidth + 2, y + barHeight + 2, 0xFF000000);
        guiGraphics.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, borderColor);
        guiGraphics.fill(x, y, x + barWidth, y + barHeight, 0xFF14141E);

        int healthColor = isStaggered ? 0xFFFFAA00 : (isNemesis ? 0xFF990000 : (isChampion ? 0xFFE63900 : 0xFFCC2222));
        guiGraphics.fill(x, y, x + filledWidth, y + barHeight, healthColor);

        // 3. Título del Monstruo con Insignia Némesis
        Component name = currentTarget.getCustomName() != null ? currentTarget.getCustomName() : currentTarget.getName();
        String titlePrefix = isNemesis ? "§4§l[NÉMESIS] " : "";
        guiGraphics.drawCenteredString(mc.font, titlePrefix + name.getString(), screenWidth / 2, y - 12, 0xFFFFFF);

        // 4. Texto Numérico de Vida
        String hpText = (int) hp + " / " + (int) maxHp;
        guiGraphics.drawCenteredString(mc.font, "§f" + hpText, screenWidth / 2, y, 0xFFFFFF);

        // 5. Insignia de Contramedida Táctica o Estado
        if (isStaggered) {
            guiGraphics.drawCenteredString(mc.font, "§e§l⚡ ¡POSTURA ROTA! (+30% Daño) ⚡", screenWidth / 2, y + 11, 0xFFFFFF);
        } else if (isNemesis && currentTarget.getPersistentData().contains("modrpg_nemesis_trait")) {
            String trait = currentTarget.getPersistentData().getString("modrpg_nemesis_trait");
            String badge = switch (trait) {
                case "ANTI_AIR_GRAVITY" -> "§c[⚔ Contra: Acróbata Aéreo]";
                case "SHIELD_BREAKER" -> "§c[⚔ Contra: Portador de Escudo]";
                case "MANA_DRAINER" -> "§c[⚔ Contra: Hechicero Elemental]";
                case "PROJECTILE_DEFLECTOR" -> "§c[⚔ Contra: Tirador]";
                default -> "§e[★ Rival Adaptado]";
            };
            guiGraphics.drawCenteredString(mc.font, badge, screenWidth / 2, y + 11, 0xFFFFFF);
        }
    };
}