package com.example.modrpg.skills;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class SkillEconomy {

    public static final int MAX_LEVEL = 100;

    public static int getXpCost(int currentLevel) {
        if (currentLevel >= MAX_LEVEL) return 100;
        double progress = (double) currentLevel / 99.0;
        return Math.max(1, (int) Math.round(1.0 + Math.pow(progress, 1.6) * 99.0));
    }

    public static int getRequiredKills(int nextLevel) {
        return nextLevel * 3;
    }

    public static void upgradeBranch(ServerPlayer player, String branch) {
        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            String b = branch.toLowerCase();
            int currentLevel;
            int playerKills;

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
                    playerKills = skills.getMeleeKills() + skills.getRangedKills();
                    break;
                default:
                    player.sendSystemMessage(Component.literal("§c[RPG] Rama desconocida. Usa: melee, ranged o mobility."));
                    return;
            }

            if (currentLevel >= MAX_LEVEL) {
                player.sendSystemMessage(Component.literal("§6[RPG] ¡Ya has alcanzado el nivel máximo (100) en esta rama!"));
                return;
            }

            int nextLevel = currentLevel + 1;
            int xpCost = getXpCost(currentLevel);
            int killsNeeded = getRequiredKills(nextLevel);

            if (playerKills < killsNeeded) {
                player.sendSystemMessage(Component.literal(
                        "§c[RPG] ¡Te falta práctica de combate!\n" +
                                "§7Necesitas: §e" + killsNeeded + " bajas §7(Tienes: §f" + playerKills + "§7)"
                ));
                return;
            }

            if (player.experienceLevel < xpCost) {
                player.sendSystemMessage(Component.literal(
                        "§c[RPG] ¡No tienes suficiente experiencia!\n" +
                                "§7Costo: §a" + xpCost + " niveles de XP §7(Tienes: §e" + player.experienceLevel + "§7)"
                ));
                return;
            }

            // Cobrar XP y subir nivel
            player.giveExperienceLevels(-xpCost);

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

            SkillAttributes.applyModifiers(player);

            player.level().playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS,
                    0.8f, 1.2f
            );

            player.sendSystemMessage(Component.literal(
                    "§a§l✔ [RPG] ¡Rama " + b.toUpperCase() + " mejorada a Nivel " + nextLevel + "! §7(-" + xpCost + " Niveles de XP)"
            ));

            checkMilestones(player, skills);
            syncSkills(player);
        });
    }

    private static void checkMilestones(ServerPlayer player, PlayerSkills skills) {
        // HITO DIAGRAMA 1: Nivel 4 Melee desbloquea DOBLE ATAQUE (Pentágono Rojo)
        if (skills.getMeleeLevel() >= 4 && !skills.hasDoubleAttack()) {
            skills.setDoubleAttack(true);
            player.sendSystemMessage(Component.literal(
                    "§c§l★ ¡HABILIDAD PRIMARIA DESBLOQUEADA! ★\n" +
                            "§6Has dominado el §c§lDoble Ataque§6. ¡Al golpear con la barra al 100% asestarás dos cortes consecutivos!"
            ));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        // HITO DIAGRAMA 2: Nivel 20 Melee desbloquea el Ataque Giratorio (Torbellino)
        if (skills.getMeleeLevel() >= 20 && !skills.hasSpinAttack()) {
            skills.setSpinAttack(true);
            player.sendSystemMessage(Component.literal(
                    "§b§l★ ¡HABILIDAD SECUNDARIA DESBLOQUEADA! ★\n" +
                            "§3Has desbloqueado el §bAtaque Giratorio§3. ¡Presiona §f[V] §3para barrer en 360°!"
            ));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        // HITO DIAGRAMA 3: Nivel 50 Melee desbloquea Golpe Definitivo (+500%)
        if (skills.getMeleeLevel() >= 50 && !skills.hasCapstoneMelee()) {
            skills.setCapstoneMelee(true);
            player.sendSystemMessage(Component.literal(
                    "§6§l★ ¡HABILIDAD DEFINITIVA DESBLOQUEADA! ★\n" +
                            "§eHas desbloqueado el §6Golpe Definitivo§e. ¡Presiona §f[R] §epara cargar un 500% de daño!"
            ));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        // HITO HÍBRIDO: Nivel 25 Melee + 25 Distancia
        if (skills.getMeleeLevel() >= 25 && skills.getRangedLevel() >= 25 && !skills.hasHybridRangedMelee()) {
            skills.setHybridRangedMelee(true);
            player.sendSystemMessage(Component.literal(
                    "§d§l★ ¡COMBO HÍBRIDO DEL CAZADOR DESBLOQUEADO! ★\n" +
                            "§5Flechas marcan al objetivo; remátalo cuerpo a cuerpo para una §dDetonación de Vacío§5."
            ));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
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