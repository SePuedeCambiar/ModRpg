package com.example.modrpg.skills;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class SkillEconomy {

    public static final int MAX_LEVEL = 100;

    /**
     * Fórmula que escala desde 1 nivel de XP (Nivel 0) hasta 100 niveles exactos de XP (Nivel 99 -> 100).
     */
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
        // HITO 1: Nivel 20 Melee desbloquea el Ataque Giratorio (Spin Attack)
        if (skills.getMeleeLevel() >= 20 && !skills.hasSpinAttack()) {
            skills.setSpinAttack(true);
            player.sendSystemMessage(Component.literal(
                    "§b§l★ ¡HABILIDAD DESBLOQUEADA! ★\n" +
                            "§3Has desbloqueado el §bAtaque Giratorio§3. ¡Presiona §f[V] §3para barrer enemigos en 360°!"
            ));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        // HITO 2: Nivel 50 Melee desbloquea el Golpe Definitivo (+500%)
        if (skills.getMeleeLevel() >= 50 && !skills.hasCapstoneMelee()) {
            skills.setCapstoneMelee(true);
            player.sendSystemMessage(Component.literal(
                    "§6§l★ ¡HABILIDAD MAESTRA DESBLOQUEADA! ★\n" +
                            "§eHas desbloqueado el §6Golpe Definitivo§e. ¡Presiona §f[R] §epara cargar un 500% de daño!"
            ));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        // HITO 3: Nivel 25 en Melee y Distancia desbloquea Rama Híbrida
        if (skills.getMeleeLevel() >= 25 && skills.getRangedLevel() >= 25 && !skills.hasHybridRangedMelee()) {
            skills.setHybridRangedMelee(true);
            player.sendSystemMessage(Component.literal(
                    "§d§l★ ¡RAMA HÍBRIDA DESBLOQUEADA: COMBO DEL CAZADOR! ★\n" +
                            "§5Dispara una flecha para marcar al enemigo (§dBrillo§5). Si lo rematas cuerpo a cuerpo, provocas una §d¡Detonación de Vacío!§5"
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