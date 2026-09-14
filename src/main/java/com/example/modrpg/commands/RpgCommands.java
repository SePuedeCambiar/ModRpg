package com.example.modrpg.commands;

import com.example.modrpg.skills.PlayerSkillsProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class RpgCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rpg")
                // Comando: /rpg stats
                .then(Commands.literal("stats")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                                player.sendSystemMessage(Component.literal("§6=== TUS ESTADÍSTICAS RPG ==="));
                                player.sendSystemMessage(Component.literal("§c⚔ Melee: §fNivel " + skills.getMeleeLevel() + " §7(Kills: " + skills.getMeleeKills() + ")"));
                                player.sendSystemMessage(Component.literal("§b🏹 Distancia: §fNivel " + skills.getRangedLevel() + " §7(Kills: " + skills.getRangedKills() + ")"));
                                player.sendSystemMessage(Component.literal("§a🏃 Movilidad: §fNivel " + skills.getMobilityLevel()));
                                player.sendSystemMessage(Component.literal("§6========================="));
                            });
                            return 1;
                        })
                )
                // Comando: /rpg addlevel <rama> <cantidad>
                .then(Commands.literal("addlevel")
                        .requires(source -> source.hasPermission(2)) // Requiere OP/trucos
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("melee");
                                    builder.suggest("ranged");
                                    builder.suggest("mobility");
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String skill = StringArgumentType.getString(context, "skill");
                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                                                if (skill.equalsIgnoreCase("melee")) {
                                                    skills.addMeleeLevel(amount);
                                                    player.sendSystemMessage(Component.literal("§a[RPG] Nivel Melee aumentado a: " + skills.getMeleeLevel()));
                                                } else if (skill.equalsIgnoreCase("ranged")) {
                                                    skills.addRangedLevel(amount);
                                                    player.sendSystemMessage(Component.literal("§a[RPG] Nivel Ranged aumentado a: " + skills.getRangedLevel()));
                                                } else if (skill.equalsIgnoreCase("mobility")) {
                                                    skills.setMobilityLevel(skills.getMobilityLevel() + amount);
                                                    player.sendSystemMessage(Component.literal("§a[RPG] Nivel Movilidad aumentado a: " + skills.getMobilityLevel()));
                                                } else {
                                                    player.sendSystemMessage(Component.literal("§cRama desconocida. Usa: melee, ranged o mobility"));
                                                }
                                            });
                                            return 1;
                                        })
                                )
                        )
                )
        );
    }
}