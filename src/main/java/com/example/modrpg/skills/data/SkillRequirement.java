package com.example.modrpg.skills.data;

import com.example.modrpg.skills.PlayerSkills;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public interface SkillRequirement {

    /**
     * Comprueba si el jugador cumple esta condición.
     */
    boolean isMet(ServerPlayer player, PlayerSkills skills);

    /**
     * Consume el recurso si la condición lo requiere (ej: descontar niveles de XP al comprar).
     */
    default void consume(ServerPlayer player, PlayerSkills skills) {}

    /**
     * Devuelve el texto descriptivo con indicador visual de estado (verde ✔ / rojo ✖).
     */
    Component getTooltip(ServerPlayer player, PlayerSkills skills);

    // =========================================================================
    // FÁBRICAS DE REQUISITOS (CONDICIONES DEL ÁRBOL)
    // =========================================================================

    /**
     * Requiere que el jugador tenga al menos cierto nivel de experiencia general de Minecraft.
     */
    static SkillRequirement minPlayerXpLevel(int minXp) {
        return new SkillRequirement() {
            @Override
            public boolean isMet(ServerPlayer player, PlayerSkills skills) {
                return player.experienceLevel >= minXp;
            }

            @Override
            public Component getTooltip(ServerPlayer player, PlayerSkills skills) {
                boolean met = isMet(player, skills);
                return Component.literal((met ? "§a✔ " : "§c✖ ") + "§7Nivel de jugador XP: §e" + minXp + " §7(Tienes: " + player.experienceLevel + ")");
            }
        };
    }

    /**
     * Consume niveles de experiencia al momento de desbloquear el nodo.
     */
    static SkillRequirement consumePlayerXpLevels(int xpCost) {
        return new SkillRequirement() {
            @Override
            public boolean isMet(ServerPlayer player, PlayerSkills skills) {
                return player.experienceLevel >= xpCost;
            }

            @Override
            public void consume(ServerPlayer player, PlayerSkills skills) {
                player.giveExperienceLevels(-xpCost);
            }

            @Override
            public Component getTooltip(ServerPlayer player, PlayerSkills skills) {
                boolean met = isMet(player, skills);
                return Component.literal((met ? "§a✔ " : "§c✖ ") + "§7Costo: §a" + xpCost + " niveles de XP");
            }
        };
    }

    /**
     * Requiere cierto nivel en una rama específica (ej: CaC nivel 5, Magia nivel 6).
     */
    static SkillRequirement branchLevel(ResourceLocation branchId, int minLevel) {
        return new SkillRequirement() {
            @Override
            public boolean isMet(ServerPlayer player, PlayerSkills skills) {
                return skills.getBranchLevel(branchId) >= minLevel;
            }

            @Override
            public Component getTooltip(ServerPlayer player, PlayerSkills skills) {
                boolean met = isMet(player, skills);
                int current = skills.getBranchLevel(branchId);
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
            public boolean isMet(ServerPlayer player, PlayerSkills skills) {
                return skills.getPractice(counterId) >= requiredAmount;
            }

            @Override
            public Component getTooltip(ServerPlayer player, PlayerSkills skills) {
                boolean met = isMet(player, skills);
                int current = skills.getPractice(counterId);
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
            public boolean isMet(ServerPlayer player, PlayerSkills skills) {
                return skills.isNodeUnlocked(parentNodeId);
            }

            @Override
            public Component getTooltip(ServerPlayer player, PlayerSkills skills) {
                boolean met = isMet(player, skills);
                return Component.literal((met ? "§a✔ " : "§c✖ ") + "§7Requiere habilidad previa: §e" + parentName);
            }
        };
    }
}