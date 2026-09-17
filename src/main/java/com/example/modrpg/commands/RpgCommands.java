package com.example.modrpg.commands;

import com.example.modrpg.ModRpg;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillAttributes;
import com.example.modrpg.skills.SkillEconomy;
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
                // 1. COMANDO: /rpg stats (Muestra todas las ramas y habilidades activas)
                // =========================================================================
                .then(Commands.literal("stats")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                                player.sendSystemMessage(Component.literal("§6================ TUS ESTADÍSTICAS RPG ================"));

                                // Ramas activas
                                player.sendSystemMessage(Component.literal("§e--- NIVELES DE RAMAS ---"));
                                skills.getAllBranchLevels().forEach((branchId, lvl) -> {
                                    player.sendSystemMessage(Component.literal("§a• " + branchId.getPath().toUpperCase() + ": §fNivel " + lvl + "/100"));
                                });

                                // Práctica acumulada
                                player.sendSystemMessage(Component.literal("§e--- PRÁCTICA Y BAJAS ---"));
                                skills.getAllPracticeCounters().forEach((counterId, count) -> {
                                    player.sendSystemMessage(Component.literal("§7• " + counterId.getPath() + ": §e" + count));
                                });

                                // Habilidades desbloqueadas
                                player.sendSystemMessage(Component.literal("§e--- HABILIDADES DESBLOQUEADAS ---"));
                                if (skills.getUnlockedNodes().isEmpty()) {
                                    player.sendSystemMessage(Component.literal("§7(Ninguna habilidad desbloqueada aún)"));
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
                // 4. COMANDO ADMIN: /rpg unlock <skill_id> (Para pruebas instantáneas)
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