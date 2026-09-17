package com.example.modrpg.skills;

import com.example.modrpg.ModRpg;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class SkillEconomy {

    public static final int MAX_LEVEL = 100;

    public static int getXpCost(int currentLevel) {
        if (currentLevel >= MAX_LEVEL) return 100;
        // Costo base creciente según el nivel
        return Math.max(1, (int) Math.round(1.0 + Math.pow((double) currentLevel / 99.0, 1.6) * 99.0));
    }

    public static int getRequiredKills(int nextLevel) {
        return nextLevel * 3;
    }

    public static void upgradeBranch(ServerPlayer player, String branchName) {
        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            ResourceLocation branchId = new ResourceLocation(ModRpg.MODID, branchName.toLowerCase());
            int currentLevel = skills.getBranchLevel(branchId);

            if (currentLevel >= MAX_LEVEL) {
                player.sendSystemMessage(Component.literal("§6[RPG] ¡Ya has alcanzado el nivel máximo en " + branchName.toUpperCase() + "!"));
                return;
            }

            // REQUISITO DIAGRAMA 2: Desbloquear CaC requiere nivel 10 de XP general
            if (branchId.equals(SkillRegistry.BRANCH_MELEE) && currentLevel == 0) {
                if (player.experienceLevel < 10) {
                    player.sendSystemMessage(Component.literal(
                            "§c🔒 [RPG] Para desbloquear el camino Cuerpo a Cuerpo (CaC) necesitas al menos §eNivel 10 de XP§c.\n§7(Tu nivel actual: §f" + player.experienceLevel + "§7)"
                    ));
                    return;
                }
            }

            // Práctica de bajas
            ResourceLocation counter = branchName.equalsIgnoreCase("ranged") ? SkillRegistry.COUNTER_RANGED_KILLS : SkillRegistry.COUNTER_MELEE_KILLS;
            int playerKills = skills.getPractice(counter);

            int nextLevel = currentLevel + 1;
            int xpCost = getXpCost(currentLevel);
            int killsNeeded = getRequiredKills(nextLevel);

            if (playerKills < killsNeeded) {
                player.sendSystemMessage(Component.literal(
                        "§c[RPG] ¡Te falta práctica de combate!\n§7Necesitas: §e" + killsNeeded + " bajas §7(Tienes: §f" + playerKills + "§7)"
                ));
                return;
            }

            if (player.experienceLevel < xpCost) {
                player.sendSystemMessage(Component.literal(
                        "§c[RPG] ¡No tienes suficiente experiencia!\n§7Costo: §a" + xpCost + " niveles de XP §7(Tienes: §e" + player.experienceLevel + "§7)"
                ));
                return;
            }

            player.giveExperienceLevels(-xpCost);
            skills.addBranchLevel(branchId, 1);
            SkillAttributes.applyModifiers(player);

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            player.sendSystemMessage(Component.literal("§a§l✔ [RPG] ¡Rama " + branchName.toUpperCase() + " mejorada a Nivel " + nextLevel + "! §7(-" + xpCost + " Niveles XP)"));

            checkMilestones(player, skills);
            syncSkills(player);
        });
    }

    public static void checkMilestones(ServerPlayer player, PlayerSkills skills) {
        for (SkillNode node : SkillRegistry.getAll()) {
            if (!skills.isNodeUnlocked(node.getId()) && node.canUnlock(player, skills)) {
                skills.unlockNode(node.getId());
                player.sendSystemMessage(Component.literal(
                        "§6§l★ ¡HABILIDAD DESBLOQUEADA! ★\n§eHas aprendido: §f§l" + node.getDisplayName().getString() + "\n§7" + node.getDescription().getString()
                ));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }
    }

    public static void syncSkills(ServerPlayer player) {
        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            com.example.modrpg.networking.ModMessages.sendToPlayer(
                    new com.example.modrpg.networking.PacketSyncSkillsToClient(skills),
                    player
            );
        });
    }
}