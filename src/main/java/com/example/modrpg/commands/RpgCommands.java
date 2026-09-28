package com.example.modrpg.commands;

import com.example.modrpg.ModRpg;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillAttributes;
import com.example.modrpg.skills.SkillEconomy;
import com.example.modrpg.skills.data.SkillBranch;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class RpgCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rpg")

                // =========================================================================
                // 1. COMANDO: /rpg stats (Dashboard Completo del Jugador)
                // =========================================================================
                .then(Commands.literal("stats")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                                player.sendSystemMessage(Component.literal("§6================ TUS ESTADÍSTICAS RPG ================"));

                                // Ramas activas con fallback en nivel 0
                                player.sendSystemMessage(Component.literal("§e--- NIVELES DE RAMAS ---"));
                                for (SkillBranch branch : SkillRegistry.getAllBranches()) {
                                    int lvl = skills.getBranchLevel(branch.id());
                                    int practice = skills.getPractice(branch.practiceCounterId());
                                    int reqPractice = SkillEconomy.getRequiredPractice(lvl + 1);

                                    player.sendSystemMessage(Component.literal(
                                            "§a• " + branch.displayName().getString() + ": §fNivel " + lvl + "/100 " +
                                                    (lvl < 100 ? "§7(Práctica: §b" + practice + "/" + reqPractice + "§7)" : "§6[MAX]")
                                    ));
                                }

                                // Contadores de práctica registrados
                                player.sendSystemMessage(Component.literal("§e--- PRÁCTICA Y COMBATE ---"));
                                player.sendSystemMessage(Component.literal("§7• Bajas CaC: §e" + skills.getPractice(SkillRegistry.COUNTER_MELEE_KILLS)));
                                player.sendSystemMessage(Component.literal("§7• Bajas a Distancia: §e" + skills.getPractice(SkillRegistry.COUNTER_RANGED_KILLS)));
                                player.sendSystemMessage(Component.literal("§7• Lanzamientos Mágicos: §e" + skills.getPractice(SkillRegistry.COUNTER_MAGIC_CASTS)));
                                player.sendSystemMessage(Component.literal("§7• Golpes Mitigados: §e" + skills.getPractice(SkillRegistry.COUNTER_DAMAGE_BLOCKED)));
                                player.sendSystemMessage(Component.literal("§7• Distancia Recorrida: §e" + skills.getPractice(SkillRegistry.COUNTER_DISTANCE_RUN) + " bloques"));

                                // Maná actual
                                player.sendSystemMessage(Component.literal("§e--- ENERGÍA ---"));
                                player.sendSystemMessage(Component.literal("§b⚡ Maná: §f" + (int) skills.getCurrentMana() + " / " + (int) skills.getMaxMana() + " §7(+" + String.format("%.1f", skills.getManaRegenPerSecond()) + "/s)"));

                                // Habilidades desbloqueadas
                                player.sendSystemMessage(Component.literal("§e--- HABILIDADES APRENDIDAS ---"));
                                if (skills.getUnlockedNodes().isEmpty()) {
                                    player.sendSystemMessage(Component.literal("§7(Ninguna habilidad desbloqueada aún. Abre el árbol con [K])"));
                                } else {
                                    for (ResourceLocation nodeId : skills.getUnlockedNodes()) {
                                        SkillNode node = SkillRegistry.get(nodeId);
                                        String name = (node != null) ? node.getDisplayName().getString() : nodeId.toString();
                                        player.sendSystemMessage(Component.literal("§a✔ " + name));
                                    }
                                }

                                player.sendSystemMessage(Component.literal("§6======================================================"));
                            });
                            return 1;
                        })
                )

                // =========================================================================
                // 2. COMANDO: /rpg upgrade <rama>
                // =========================================================================
                .then(Commands.literal("upgrade")
                        .then(Commands.argument("branch", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("melee");
                                    builder.suggest("ranged");
                                    builder.suggest("mobility");
                                    builder.suggest("magic");
                                    builder.suggest("defense");
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String branch = StringArgumentType.getString(context, "branch");
                                    SkillEconomy.upgradeBranch(player, branch);
                                    return 1;
                                })
                        )
                )

                // =========================================================================
                // 3. COMANDO ADMIN: /rpg addlevel <rama> <cantidad>
                // =========================================================================
                .then(Commands.literal("addlevel")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("branch", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("melee");
                                    builder.suggest("ranged");
                                    builder.suggest("mobility");
                                    builder.suggest("magic");
                                    builder.suggest("defense");
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String branch = StringArgumentType.getString(context, "branch");
                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                            ResourceLocation branchId = new ResourceLocation(ModRpg.MODID, branch.toLowerCase());

                                            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                                                skills.addBranchLevel(branchId, amount);
                                                player.sendSystemMessage(Component.literal(
                                                        "§a[RPG] Rama §e" + branchId.getPath().toUpperCase() + "§a aumentada a Nivel: §e" + skills.getBranchLevel(branchId)
                                                ));
                                                SkillAttributes.applyModifiers(player);
                                                SkillEconomy.syncSkills(player);
                                            });

                                            return 1;
                                        })
                                )
                        )
                )

                // =========================================================================
                // 4. COMANDO ADMIN: /rpg unlock <skill_id>
                // =========================================================================
                .then(Commands.literal("unlock")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (SkillNode node : SkillRegistry.getAll()) {
                                        builder.suggest(node.getId().getPath());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String skillPath = StringArgumentType.getString(context, "skill");
                                    ResourceLocation skillId = new ResourceLocation(ModRpg.MODID, skillPath);

                                    SkillNode node = SkillRegistry.get(skillId);
                                    if (node == null) {
                                        player.sendSystemMessage(Component.literal("§c[RPG] No existe ninguna habilidad con ID: " + skillId));
                                        return 0;
                                    }

                                    player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                                        skills.unlockNode(skillId);
                                        player.sendSystemMessage(Component.literal("§a[RPG] ¡Habilidad desbloqueada con éxito: §e" + node.getDisplayName().getString() + "§a!"));
                                        SkillEconomy.syncSkills(player);
                                    });

                                    return 1;
                                })
                        )
                )
        );
    }
}