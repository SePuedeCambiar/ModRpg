package com.example.modrpg.skills;

import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketSyncSkillsToClient;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class SkillEconomy {

    public static final int MAX_LEVEL = 100;

    // Fórmula de Costo de XP Vanilla:
    // Nivel 0 -> 1 nivel de XP
    // Nivel 20 -> 10 niveles de XP
    // Nivel 50 -> 23 niveles de XP
    // Nivel 99 -> 45 niveles de XP
    public static int getXpCost(int currentLevel) {
        return Math.max(1, 1 + (int)(currentLevel * 0.45));
    }

    // Fórmula de Práctica (Bajas necesarias para alcanzar el siguiente nivel):
    // Nivel 1 -> 3 kills
    // Nivel 10 -> 30 kills
    // Nivel 50 -> 150 kills
    public static int getRequiredKills(int nextLevel) {
        return nextLevel * 3;
    }

    // Método central para mejorar una rama
    public static void upgradeBranch(ServerPlayer player, String branch) {
        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            String b = branch.toLowerCase();
            int currentLevel = 0;
            int playerKills = 0;

            switch (b) {
                case "melee":
                    currentLevel = skills.getMeleeLevel();
                    playerKills = skills.getMeleeKills();
                    break;
                case "ranged":
                case "distancia":
                    currentLevel = skills.getRangedLevel();
                    playerKills = skills.getRangedKills();
                    break;
                case "mobility":
                case "movilidad":
                    currentLevel = skills.getMobilityLevel();
                    // Movilidad requiere combate mixto (suma de ambas ramas)
                    playerKills = skills.getMeleeKills() + skills.getRangedKills();
                    break;
                default:
                    player.sendSystemMessage(Component.literal("§c[RPG] Rama desconocida. Usa: melee, ranged o mobility."));
                    return;
            }

            // 1. Validar si ya está al nivel máximo
            if (currentLevel >= MAX_LEVEL) {
                player.sendSystemMessage(Component.literal("§6[RPG] ¡Ya has alcanzado el nivel máximo (100) en esta rama!"));
                return;
            }

            int nextLevel = currentLevel + 1;
            int xpCost = getXpCost(currentLevel);
            int killsNeeded = getRequiredKills(nextLevel);

            // 2. Validar Requisito de Práctica (Kills)
            if (playerKills < killsNeeded) {
                player.sendSystemMessage(Component.literal(
                        "§c[RPG] ¡Te falta práctica de combate!\n" +
                                "§7Necesitas: §e" + killsNeeded + " bajas §7(Tienes: §f" + playerKills + "§7)"
                ));
                return;
            }

            // 3. Validar Requisito de Niveles de Experiencia Vainilla
            if (player.experienceLevel < xpCost) {
                player.sendSystemMessage(Component.literal(
                        "§c[RPG] ¡No tienes suficiente experiencia!\n" +
                                "§7Costo: §a" + xpCost + " niveles de XP §7(Tienes: §e" + player.experienceLevel + "§7)"
                ));
                return;
            }

            // === APLICAR MEJORA ===
            // Cobrar los niveles de experiencia del jugador
            player.giveExperienceLevels(-xpCost);

            // Subir nivel según la rama
            switch (b) {
                case "melee":
                    skills.addMeleeLevel(1);
                    break;
                case "ranged":
                case "distancia":
                    skills.addRangedLevel(1);
                    break;
                case "mobility":
                case "movilidad":
                    skills.setMobilityLevel(skills.getMobilityLevel() + 1);
                    break;
            }

            // Re-calcular atributos físicos (daño base, velocidad)
            SkillAttributes.applyModifiers(player);

            // Sonido de subida de nivel
            player.level().playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS,
                    0.8f, 1.2f
            );

            // Mensaje de éxito
            player.sendSystemMessage(Component.literal(
                    "§a§l✔ [RPG] ¡Rama " + b.toUpperCase() + " mejorada a Nivel " + nextLevel + "! §7(-" + xpCost + " Niveles de XP)"
            ));

            // 4. VERIFICAR HITOS Y HABILIDADES ESPECIALES
            checkMilestones(player, skills);

            // 5. SINCRONIZAR CON LA GUI DEL CLIENTE
            syncSkills(player);
        });
    }

    // Verificación de desbloqueo de Capstones e Híbridos
    private static void checkMilestones(ServerPlayer player, PlayerSkills skills) {
        // Hito Melee: Nivel 50 desbloquea el Golpe Definitivo permanentemente
        if (skills.getMeleeLevel() >= 50 && !skills.hasCapstoneMelee()) {
            skills.setCapstoneMelee(true);
            player.sendSystemMessage(Component.literal(
                    "§6§l★ ¡NUEVA HABILIDAD DESBLOQUEADA! ★\n" +
                            "§eHas desbloqueado el §6Golpe Definitivo§e. ¡Presiona §f[R] §epara cargar un 500% de daño!"
            ));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        // Hito Híbrido: Nivel 25 en Melee y 25 en Ranged
        if (skills.getMeleeLevel() >= 25 && skills.getRangedLevel() >= 25 && !skills.hasHybridRangedMelee()) {
            skills.setHybridRangedMelee(true);
            player.sendSystemMessage(Component.literal(
                    "§d§l★ ¡RAMA HÍBRIDA DESBLOQUEADA! ★\n" +
                            "§5Has dominado el combate dual: Melee + Arquería."
            ));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    // Método auxiliar para enviar los datos más recientes al cliente
    public static void syncSkills(ServerPlayer player) {
        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            ModMessages.sendToPlayer(
                    new PacketSyncSkillsToClient(skills),
                    player
            );
        });
    }
}