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

public class SkillEconomy {

    public static final int MAX_LEVEL = SkillProgression.MAX_LEVEL;

    /**
     * Delega el costo en XP a la curva matemática del Sprint 1 (1 nivel a inicio, 100 a nivel 99/100).
     */
    public static int getXpCost(int currentLevel) {
        return SkillProgression.getXpCost(currentLevel);
    }

    /**
     * Delega el requisito de práctica lineal universal (nextLevel * 3).
     */
    public static int getRequiredPractice(int nextLevel) {
        return SkillProgression.getRequiredPractice(nextLevel);
    }

    // Método puente para mantener compatibilidad con llamadas existentes
    public static int getRequiredKills(int nextLevel) {
        return getRequiredPractice(nextLevel);
    }

    /**
     * Procesa la compra y ascenso de nivel en cualquier rama de forma 100% dinámica.
     */
    public static void upgradeBranch(ServerPlayer player, String branchName) {
        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            ResourceLocation branchId = new ResourceLocation(ModRpg.MODID, branchName.toLowerCase());
            SkillBranch branch = SkillRegistry.getBranch(branchId);

            if (branch == null) {
                player.sendSystemMessage(Component.literal("§c[RPG] Rama desconocida: " + branchName));
                return;
            }

            int currentLevel = skills.getBranchLevel(branchId);

            // 1. Validar nivel máximo alcanzado (100)
            if (currentLevel >= MAX_LEVEL) {
                player.sendSystemMessage(Component.literal(
                        "§6[RPG] ¡Ya has alcanzado el nivel máximo (100) en " + branch.displayName().getString() + "!"
                ));
                return;
            }

            // 2. Validar nivel inicial de XP de Minecraft requerido para desbloquear la rama (Nivel 0 -> 1)
            if (currentLevel == 0 && player.experienceLevel < branch.minPlayerXpToUnlock()) {
                player.sendSystemMessage(Component.literal(
                        "§c🔒 [RPG] Iniciar " + branch.displayName().getString() +
                                " requiere ser §eNivel " + branch.minPlayerXpToUnlock() + " de XP§c en Minecraft. §7(Tienes: " + player.experienceLevel + ")"
                ));
                return;
            }

            int nextLevel = currentLevel + 1;
            int xpCost = getXpCost(currentLevel);
            int practiceNeeded = getRequiredPractice(nextLevel);
            int playerPractice = skills.getPractice(branch.practiceCounterId());

            // 3. Validar práctica de combate acumulada (bajas, daño mitigado, etc.)
            if (playerPractice < practiceNeeded) {
                player.sendSystemMessage(Component.literal(
                        "§c[RPG] ¡Te falta práctica en esta rama!\n" +
                                "§7Requerido: §e" + practiceNeeded + " puntos §7| Actual: §f" + playerPractice +
                                " §7(Faltan: §c" + (practiceNeeded - playerPractice) + "§7)"
                ));
                return;
            }

            // 4. Validar que el jugador tenga los niveles de experiencia necesarios
            if (player.experienceLevel < xpCost) {
                player.sendSystemMessage(Component.literal(
                        "§c[RPG] ¡No tienes suficiente experiencia!\n" +
                                "§7Costo: §a" + xpCost + " niveles de XP §7(Tienes: §e" + player.experienceLevel + "§7)"
                ));
                return;
            }

            // 5. Transacción legítima
            player.giveExperienceLevels(-xpCost);
            skills.addBranchLevel(branchId, 1);
            SkillAttributes.applyModifiers(player);

            // Audio y feedback visual al jugador
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);

            player.sendSystemMessage(Component.literal(
                    "§a§l✔ [RPG] ¡Rama " + branch.displayName().getString() + " mejorada a Nivel " + nextLevel + "! §7(-" + xpCost + " Niveles XP)"
            ));

            // 6. Verificar y desbloquear automáticamente habilidades pasivas o hitos alcanzados
            checkMilestones(player, skills);

            // 7. Sincronizar Capability completa con el cliente
            syncSkills(player);
        });
    }

    /**
     * Revisa el árbol tras cada nivel y desbloquea cualquier habilidad cuyos prerrequisitos se hayan cumplido.
     */
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

    /**
     * Envía los datos actualizados de habilidades, contadores y cooldowns al cliente del jugador.
     */
    public static void syncSkills(ServerPlayer player) {
        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            ModMessages.sendToPlayer(
                    new PacketSyncSkillsToClient(skills),
                    player
            );
        });
    }
}