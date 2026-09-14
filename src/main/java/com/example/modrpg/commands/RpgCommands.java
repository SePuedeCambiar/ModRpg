package com.example.modrpg.commands;

import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillAttributes;
import com.example.modrpg.skills.SkillEconomy;
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
                // ==========================================
                // 1. COMANDO: /rpg stats
                // ==========================================
                .then(Commands.literal("stats")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                                player.sendSystemMessage(Component.literal("§6================ TUS ESTADÍSTICAS RPG ================"));

                                // Rama Melee
                                int nextMeleeLvl = skills.getMeleeLevel() + 1;
                                int meleeXpCost = SkillEconomy.getXpCost(skills.getMeleeLevel());
                                int meleeKillsReq = SkillEconomy.getRequiredKills(nextMeleeLvl);
                                player.sendSystemMessage(Component.literal(
                                        "§c⚔ Melee: §fNivel " + skills.getMeleeLevel() + " §7| Kills: §e" + skills.getMeleeKills() +
                                                (skills.getMeleeLevel() < 100 ? " §7(Sig: §a" + meleeXpCost + " Niveles XP§7, §e" + meleeKillsReq + " Kills§7)" : " §6[NIVEL MÁXIMO]")
                                ));

                                // Rama Distancia
                                int nextRangedLvl = skills.getRangedLevel() + 1;
                                int rangedXpCost = SkillEconomy.getXpCost(skills.getRangedLevel());
                                int rangedKillsReq = SkillEconomy.getRequiredKills(nextRangedLvl);
                                player.sendSystemMessage(Component.literal(
                                        "§b🏹 Distancia: §fNivel " + skills.getRangedLevel() + " §7| Kills: §e" + skills.getRangedKills() +
                                                (skills.getRangedLevel() < 100 ? " §7(Sig: §a" + rangedXpCost + " Niveles XP§7, §e" + rangedKillsReq + " Kills§7)" : " §6[NIVEL MÁXIMO]")
                                ));

                                // Rama Movilidad
                                int nextMobLvl = skills.getMobilityLevel() + 1;
                                int mobXpCost = SkillEconomy.getXpCost(skills.getMobilityLevel());
                                int mobKillsReq = SkillEconomy.getRequiredKills(nextMobLvl);
                                int totalKills = skills.getMeleeKills() + skills.getRangedKills();
                                player.sendSystemMessage(Component.literal(
                                        "§a🏃 Movilidad: §fNivel " + skills.getMobilityLevel() + " §7| Kills totales: §e" + totalKills +
                                                (skills.getMobilityLevel() < 100 ? " §7(Sig: §a" + mobXpCost + " Niveles XP§7, §e" + mobKillsReq + " Kills§7)" : " §6[NIVEL MÁXIMO]")
                                ));

                                // Estado de Habilidades Maestras
                                player.sendSystemMessage(Component.literal("§e--- HABILIDADES ESPECIALES ---"));
                                player.sendSystemMessage(Component.literal(
                                        "§6⚡ Golpe Definitivo (500%): " + (skills.hasCapstoneMelee() ? "§a§lDESBLOQUEADO" : "§c§lBLOQUEADO §7(Req. Nivel 50 Melee)")
                                ));
                                player.sendSystemMessage(Component.literal(
                                        "§d☯ Híbrido (Melee + Distancia): " + (skills.hasHybridRangedMelee() ? "§a§lDESBLOQUEADO" : "§c§lBLOQUEADO §7(Req. Nivel 25 en ambos)")
                                ));

                                player.sendSystemMessage(Component.literal("§6======================================================"));
                            });
                            return 1;
                        })
                )
                // ==========================================
                // 2. COMANDO: /rpg upgrade <rama> (Para supervivencia)
                // ==========================================
                .then(Commands.literal("upgrade")
                        .then(Commands.argument("branch", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("melee");
                                    builder.suggest("ranged");
                                    builder.suggest("mobility");
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String branch = StringArgumentType.getString(context, "branch");
                                    // Llamamos al gestor económico
                                    SkillEconomy.upgradeBranch(player, branch);
                                    return 1;
                                })
                        )
                )
                // ==========================================
                // 3. COMANDO: /rpg addlevel <rama> <cantidad> (Trucos / Admin)
                // ==========================================
                .then(Commands.literal("addlevel")
                        .requires(source -> source.hasPermission(2)) // Solo OP
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

                                            SkillAttributes.applyModifiers(player);
                                            return 1;
                                        })
                                )
                        )
                )
        );
    }
}