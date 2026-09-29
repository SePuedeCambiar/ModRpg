package com.example.modrpg.ai.nemesis;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rastrea la telemetría de combate del jugador para detectar hábitos predecibles
 * (abuso de saltos, dependencia de escudo, elemento mágico favorito, kiting).
 */
public class PlayerCombatProfiler {

    public enum DominantStyle {
        AIR_JUMPER("§bAcróbata Aéreo", "Dependencia extrema de Salto de Viento / Ground Slam."),
        SHIELD_TURTLE("§6Portador de Escudo", "Bloquea pasivamente la mayoría de los golpes."),
        ELEMENTAL_MAGE("§dHechicero Elemental", "Dependencia de ráfagas mágicas."),
        SNIPER_KITER("§aTirador a Distancia", "Combate mediante kiting continuo con flechas."),
        MELEE_BERSERKER("§cBruto Cuerpo a Cuerpo", "Presión frontal agresiva con espada/hacha."),
        BALANCED("§7Equilibrado", "Combina estilos sin depender de uno en particular.");

        private final String displayName;
        private final String description;

        DominantStyle(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    private static final String PROFILE_TAG = "modrpg_combat_profile";

    public static void recordMeleeDamage(ServerPlayer player, float damage) {
        addWeight(player, "melee", damage * 0.1f);
    }

    public static void recordRangedDamage(ServerPlayer player, float damage) {
        addWeight(player, "ranged", damage * 0.15f);
    }

    public static void recordShieldBlock(ServerPlayer player) {
        addWeight(player, "shield", 1.5f);
    }

    public static void recordAirAction(ServerPlayer player) {
        addWeight(player, "vertical_air", 2.0f);
    }

    public static void recordMagicCast(ServerPlayer player, String element) {
        addWeight(player, "magic", 1.2f);
        addWeight(player, "elem_" + element.toLowerCase(), 1.5f);
    }

    private static void addWeight(ServerPlayer player, String key, float amount) {
        CompoundTag profile = getProfileTag(player);
        float current = profile.getFloat(key);
        profile.putFloat(key, current + amount);
        player.getPersistentData().put(PROFILE_TAG, profile);
    }

    public static CompoundTag getProfileTag(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(PROFILE_TAG) ? data.getCompound(PROFILE_TAG) : new CompoundTag();
    }

    /**
     * Determina el estilo dominante calculando la proporción de acciones.
     */
    public static DominantStyle getDominantStyle(ServerPlayer player) {
        CompoundTag p = getProfileTag(player);

        float melee = p.getFloat("melee");
        float ranged = p.getFloat("ranged");
        float shield = p.getFloat("shield");
        float air = p.getFloat("vertical_air");
        float magic = p.getFloat("magic");

        float total = melee + ranged + shield + air + magic;
        if (total < 10.0f) return DominantStyle.BALANCED; // No hay suficientes datos aún

        // Umbrales de especialización
        if ((air / total) >= 0.25f) return DominantStyle.AIR_JUMPER;
        if ((shield / total) >= 0.25f) return DominantStyle.SHIELD_TURTLE;
        if ((magic / total) >= 0.30f) return DominantStyle.ELEMENTAL_MAGE;
        if ((ranged / total) >= 0.35f) return DominantStyle.SNIPER_KITER;
        if ((melee / total) >= 0.40f) return DominantStyle.MELEE_BERSERKER;

        return DominantStyle.BALANCED;
    }

    /**
     * Devuelve el elemento mágico más utilizado por el jugador.
     */
    public static String getFavoriteElement(ServerPlayer player) {
        CompoundTag p = getProfileTag(player);
        String[] elements = {"fire", "frost", "lightning", "void", "holy"};

        String topElement = "fire";
        float maxWeight = -1.0f;

        for (String el : elements) {
            float w = p.getFloat("elem_" + el);
            if (w > maxWeight) {
                maxWeight = w;
                topElement = el;
            }
        }
        return topElement.toUpperCase();
    }
}