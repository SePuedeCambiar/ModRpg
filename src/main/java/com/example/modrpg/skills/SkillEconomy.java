package com.example.modrpg.skills;

import com.example.modrpg.ModRpg;
import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketSyncSkillsToClient;
import com.example.modrpg.skills.data.SkillBranch;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Objects;

public class SkillEconomy {

    public static final int MAX_LEVEL = SkillProgression.MAX_LEVEL;

    public static int getXpCost(int currentLevel) {
        return SkillProgression.getXpCost(currentLevel);
    }

    public static int getRequiredPractice(int nextLevel) {
        return SkillProgression.getRequiredPractice(nextLevel);
    }

    public static void upgradeBranch(ServerPlayer player, String branchName) {
        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            ResourceLocation branchId = new ResourceLocation(ModRpg.MODID, branchName.toLowerCase());
            SkillBranch branch = SkillRegistry.getBranch(branchId);

            if (branch == null) {
                player.sendSystemMessage(Component.literal("§c[RPG] Rama desconocida: " + branchName));
                return;
            }

            int currentLevel = skills.getBranchLevel(branchId);
            int maxAllowed = skills.getMaxLevelForBranch(branchId);

            // 1. Validar límite máximo absoluto de 100
            if (currentLevel >= MAX_LEVEL) {
                player.sendSystemMessage(Component.literal("§6★ ¡Esta rama ya está en el Nivel Máximo (100)!"));
                return;
            }

            // =========================================================================
            // 2. VOTOS DE ESPECIALIZACIÓN Y LÍMITES (Sprint 4)
            // =========================================================================
            if (currentLevel >= PlayerSkills.CAP_BASE) { // Intento de subir a 21+
                ResourceLocation primary = skills.getPrimaryBranch();
                ResourceLocation secondary = skills.getSecondaryBranch();

                if (primary == null) {
                    skills.setPrimaryBranch(branchId);
                    player.sendSystemMessage(Component.literal(
                            "§6§l★ ¡VOTO DE MAESTRÍA SELLADO! ★\n" +
                                    "§eHas elegido §f§l" + branch.displayName().getString() + "§e como tu §6Rama Principal§e.\n" +
                                    "§7(Puede alcanzar Nivel 100 y desbloquear la Habilidad Definitiva)"
                    ));
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.2f, 1.0f);
                } else if (!primary.equals(branchId) && secondary == null) {
                    skills.setSecondaryBranch(branchId);
                    player.sendSystemMessage(Component.literal(
                            "§e§l☯ ¡VOTO SECUNDARIO SELLADO! ☯\n" +
                                    "§eHas elegido §f§l" + branch.displayName().getString() + "§e como tu §bRama Secundaria§e.\n" +
                                    "§7(Puede alcanzar Nivel 50 para sinergias híbridas)"
                    ));
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.4f);
                } else if (!Objects.equals(primary, branchId) && !Objects.equals(secondary, branchId)) {
                    player.sendSystemMessage(Component.literal(
                            "§c🔒 [RPG] Esta rama está limitada a Nivel " + PlayerSkills.CAP_BASE + ".\n" +
                                    "§7Tus votos actuales son: Principal (§6" + primary.getPath().toUpperCase() + "§7) y Secundaria (§b" + secondary.getPath().toUpperCase() + "§7).\n" +
                                    "§eUsa §f/rpg respec§e si deseas reiniciar tus especializaciones."
                    ));
                    return;
                } else if (Objects.equals(secondary, branchId) && currentLevel >= PlayerSkills.CAP_SECONDARY) {
                    player.sendSystemMessage(Component.literal(
                            "§c🔒 [RPG] Tu Rama Secundaria está al límite (Nivel " + PlayerSkills.CAP_SECONDARY + "). Solo tu Rama Principal puede alcanzar Nivel 100."
                    ));
                    return;
                }
            }

            // 3. Validar XP mínima inicial (Nivel 0 -> 1)
            if (currentLevel == 0 && player.experienceLevel < branch.minPlayerXpToUnlock()) {
                player.sendSystemMessage(Component.literal(
                        "§c🔒 [RPG] Requiere ser Nivel " + branch.minPlayerXpToUnlock() + " de XP en Minecraft. (Tienes: " + player.experienceLevel + ")"
                ));
                return;
            }

            int nextLevel = currentLevel + 1;

            // =========================================================================
            // 4. PRUEBAS DE ASCENSIÓN (HITOS EN NIVELES 25, 50, 75)
            // =========================================================================
            String trialFailure = checkAscensionTrial(player, skills, nextLevel);
            if (trialFailure != null) {
                player.sendSystemMessage(Component.literal(trialFailure));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
                return;
            }

            int xpCost = getXpCost(currentLevel);
            int practiceNeeded = getRequiredPractice(nextLevel);
            int playerPractice = skills.getPractice(branch.practiceCounterId());

            // 5. Validar Práctica
            if (playerPractice < practiceNeeded) {
                player.sendSystemMessage(Component.literal(
                        "§c[RPG] ¡Te falta práctica en esta rama!\n" +
                                "§7Requerido: §e" + practiceNeeded + " puntos §7| Actual: §f" + playerPractice +
                                " §7(Faltan: §c" + (practiceNeeded - playerPractice) + "§7)"
                ));
                return;
            }

            // 6. Validar Niveles de XP
            if (player.experienceLevel < xpCost) {
                player.sendSystemMessage(Component.literal(
                        "§c[RPG] ¡No tienes suficiente experiencia!\n" +
                                "§7Costo: §a" + xpCost + " niveles de XP §7(Tienes: §e" + player.experienceLevel + "§7)"
                ));
                return;
            }

            // 7. Transacción legítima
            player.giveExperienceLevels(-xpCost);
            skills.addBranchLevel(branchId, 1);
            SkillAttributes.applyModifiers(player);

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);

            player.sendSystemMessage(Component.literal(
                    "§a§l✔ [RPG] ¡Rama " + branch.displayName().getString() + " mejorada a Nivel " + nextLevel + "! §7(-" + xpCost + " Niveles XP)"
            ));

            checkMilestones(player, skills);
            syncSkills(player);
        });
    }

    /**
     * Evalúa si el jugador cumple los desafíos de combate para pasar de los niveles 25, 50 y 75.
     */
    private static String checkAscensionTrial(ServerPlayer player, PlayerSkills skills, int nextLevel) {
        int eliteKills = skills.getPractice(SkillRegistry.COUNTER_ELITE_KILLS);

        // Hito 1: Nivel 25 -> 26 (Prueba del Iniciado: Requiere haber vencido al menos 3 Campeones / Élites)
        if (nextLevel == 26 && eliteKills < 3) {
            return "§c§l⚔ PRUEBA DE ASCENSIÓN (Nvl 25 -> 26) BLOQUEADA:\n" +
                    "§7Debes demostrar tu valía derrotando al menos §e3 Monstruos Campeones / Élites§7.\n" +
                    "§cProgreso actual: §e" + eliteKills + " / 3";
        }

        // Hito 2: Nivel 50 -> 51 (Prueba del Maestro: Requiere 10 Campeones vencidos)
        if (nextLevel == 51 && eliteKills < 10) {
            return "§6§l★ PRUEBA DEL MAESTRO (Nvl 50 -> 51) BLOQUEADA:\n" +
                    "§7Para dominar tu especialización debes derrotar a §e10 Monstruos Campeones / Élites§7.\n" +
                    "§cProgreso actual: §e" + eliteKills + " / 10";
        }

        // Hito 3: Nivel 75 -> 76 (Prueba del Gran Maestro: Requiere 25 Campeones vencidos)
        if (nextLevel == 76 && eliteKills < 25) {
            return "§d§l👑 PRUEBA DEL GRAN MAESTRO (Nvl 75 -> 76) BLOQUEADA:\n" +
                    "§7Solo los héroes legendarios ascienden aquí. Requiere §e25 Monstruos Campeones§7 derrotados.\n" +
                    "§cProgreso actual: §e" + eliteKills + " / 25";
        }

        return null; // Aprobado
    }

    public static void checkMilestones(ServerPlayer player, PlayerSkills skills) {
        for (SkillNode node : SkillRegistry.getAll()) {
            if (!skills.isNodeUnlocked(node.getId()) && node.canUnlock(player, skills)) {
                skills.unlockNode(node.getId());
                node.onUnlocked(player, skills);

                player.sendSystemMessage(Component.literal(
                        "§6§l★ ¡HABILIDAD DESBLOQUEADA! ★\n" +
                                "§eHas aprendido: §f§l" + node.getDisplayName().getString() + "\n" +
                                "§7" + node.getDescription().getString()
                ));

                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }
    }

    public static void syncSkills(ServerPlayer player) {
        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            ModMessages.sendToPlayer(new PacketSyncSkillsToClient(skills), player);
        });
    }
}