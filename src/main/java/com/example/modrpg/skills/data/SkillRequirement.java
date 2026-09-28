package com.example.modrpg.skills.data;

import com.example.modrpg.skills.PlayerSkills;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public interface SkillRequirement {

    /**
     * Comprueba si el jugador cumple esta condición (seguro tanto en cliente como en servidor).
     */
    boolean isMet(Player player, PlayerSkills skills);

    /**
     * Consume el recurso si la condición lo requiere al desbloquear en el servidor.
     */
    default void consume(Player player, PlayerSkills skills) {}

    /**
     * Devuelve el texto descriptivo con indicador visual de estado (verde ✔ / rojo ✖).
     */
    Component getTooltip(Player player, PlayerSkills skills);

    // =========================================================================
    // FÁBRICAS DE REQUISITOS (CONDICIONES DEL ÁRBOL)
    // =========================================================================

    /**
     * Requiere que el jugador tenga al menos cierto nivel de experiencia general de Minecraft.
     */
    static SkillRequirement minPlayerXpLevel(int minXp) {
        return new SkillRequirement() {
            @Override
            public boolean isMet(Player player, PlayerSkills skills) {
                return player != null && player.experienceLevel >= minXp;
            }

            @Override
            public Component getTooltip(Player player, PlayerSkills skills) {
                int current = (player != null) ? player.experienceLevel : 0;
                boolean met = isMet(player, skills);
                return Component.literal((met ? "§a✔ " : "§c✖ ") + "§7Nivel de jugador XP: §e" + minXp + " §7(Tienes: " + current + ")");
            }
        };
    }

    /**
     * Consume niveles de experiencia al momento de desbloquear el nodo.
     */
    static SkillRequirement consumePlayerXpLevels(int xpCost) {
        return new SkillRequirement() {
            @Override
            public boolean isMet(Player player, PlayerSkills skills) {
                return player != null && player.experienceLevel >= xpCost;
            }

            @Override
            public void consume(Player player, PlayerSkills skills) {
                if (player != null) {
                    player.giveExperienceLevels(-xpCost);
                }
            }

            @Override
            public Component getTooltip(Player player, PlayerSkills skills) {
                int current = (player != null) ? player.experienceLevel : 0;
                boolean met = isMet(player, skills);
                return Component.literal((met ? "§a✔ " : "§c✖ ") + "§7Costo: §a" + xpCost + " niveles de XP §7(Tienes: " + current + ")");
            }
        };
    }

    /**
     * Requiere cierto nivel en una rama específica (ej: CaC nivel 5, Magia nivel 6).
     */
    static SkillRequirement branchLevel(ResourceLocation branchId, int minLevel) {
        return new SkillRequirement() {
            @Override
            public boolean isMet(Player player, PlayerSkills skills) {
                return skills != null && skills.getBranchLevel(branchId) >= minLevel;
            }

            @Override
            public Component getTooltip(Player player, PlayerSkills skills) {
                int current = (skills != null) ? skills.getBranchLevel(branchId) : 0;
                boolean met = isMet(player, skills);
                return Component.literal((met ? "§a✔ " : "§c✖ ") + "§7Requiere " + branchId.getPath().toUpperCase() + " Nivel " + minLevel + " §7(" + current + "/" + minLevel + ")");
            }
        };
    }

    /**
     * Requiere práctica acumulada (ej: 50 bajas con espada o flechas).
     */
    static SkillRequirement practice(ResourceLocation counterId, int requiredAmount, String practiceName) {
        return new SkillRequirement() {
            @Override
            public boolean isMet(Player player, PlayerSkills skills) {
                return skills != null && skills.getPractice(counterId) >= requiredAmount;
            }

            @Override
            public Component getTooltip(Player player, PlayerSkills skills) {
                int current = (skills != null) ? skills.getPractice(counterId) : 0;
                boolean met = isMet(player, skills);
                return Component.literal((met ? "§a✔ " : "§c✖ ") + "§7Práctica: §e" + current + "/" + requiredAmount + " " + practiceName);
            }
        };
    }

    /**
     * Requiere haber desbloqueado un nodo anterior en el árbol.
     */
    static SkillRequirement prerequisiteNode(ResourceLocation parentNodeId, String parentName) {
        return new SkillRequirement() {
            @Override
            public boolean isMet(Player player, PlayerSkills skills) {
                return skills != null && skills.isNodeUnlocked(parentNodeId);
            }

            @Override
            public Component getTooltip(Player player, PlayerSkills skills) {
                boolean met = isMet(player, skills);
                return Component.literal((met ? "§a✔ " : "§c✖ ") + "§7Requiere habilidad previa: §e" + parentName);
            }
        };
    }
}