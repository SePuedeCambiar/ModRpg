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

                // =========================================================================
                // 1. COMANDO: /rpg stats (Panel detallado en el chat)
                // =========================================================================
                .then(Commands.literal("stats")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                                player.sendSystemMessage(Component.literal("§6================ TUS ESTADÍSTICAS RPG ================"));

                                // --- Rama Melee ---
                                int nextMeleeLvl = skills.getMeleeLevel() + 1;
                                int meleeXpCost = SkillEconomy.getXpCost(skills.getMeleeLevel());
                                int meleeKillsReq = SkillEconomy.getRequiredKills(nextMeleeLvl);
                                player.sendSystemMessage(Component.literal(
                                        "§c⚔ Melee: §fNivel " + skills.getMeleeLevel() + "/100 §7| Bajas: §e" + skills.getMeleeKills() +
                                                (skills.getMeleeLevel() < 100 ? " §7(Sig: §a" + meleeXpCost + " Niveles XP§7, §e" + meleeKillsReq + " Kills§7)" : " §6[MÁXIMO]")
                                ));

                                // --- Rama Distancia ---
                                int nextRangedLvl = skills.getRangedLevel() + 1;
                                int rangedXpCost = SkillEconomy.getXpCost(skills.getRangedLevel());
                                int rangedKillsReq = SkillEconomy.getRequiredKills(nextRangedLvl);
                                player.sendSystemMessage(Component.literal(
                                        "§b🏹 Distancia: §fNivel " + skills.getRangedLevel() + "/100 §7| Bajas: §e" + skills.getRangedKills() +
                                                (skills.getRangedLevel() < 100 ? " §7(Sig: §a" + rangedXpCost + " Niveles XP§7, §e" + rangedKillsReq + " Kills§7)" : " §6[MÁXIMO]")
                                ));

                                // --- Rama Movilidad ---
                                int nextMobLvl = skills.getMobilityLevel() + 1;
                                int mobXpCost = SkillEconomy.getXpCost(skills.getMobilityLevel());
                                int mobKillsReq = SkillEconomy.getRequiredKills(nextMobLvl);
                                int totalKills = skills.getMeleeKills() + skills.getRangedKills();
                                player.sendSystemMessage(Component.literal(
                                        "§a🏃 Movilidad: §fNivel " + skills.getMobilityLevel() + "/100 §7| Bajas totales: §e" + totalKills +
                                                (skills.getMobilityLevel() < 100 ? " §7(Sig: §a" + mobXpCost + " Niveles XP§7, §e" + mobKillsReq + " Kills§7)" : " §6[MÁXIMO]")
                                ));

                                // --- TALENTOS DESBLOQUEADOS ---
                                player.sendSystemMessage(Component.literal("§e--- ESTADO DE TALENTOS Y HABILIDADES ---"));

                                // Rama 1: CaC
                                player.sendSystemMessage(Component.literal(
                                        "§c⚔ Doble Ataque: " + (skills.hasDoubleAttack() ? "§a§lDESBLOQUEADO §7(Pasivo con 100% barra)" : "§c§lBLOQUEADO §7(Req. Nivel 4 Melee)")
                                ));
                                player.sendSystemMessage(Component.literal(
                                        "§b🌀 Torbellino 360°: " + (skills.hasSpinAttack() ? "§a§lDESBLOQUEADO §7[Tecla V]" : "§c§lBLOQUEADO §7(Req. Nivel 20 Melee)")
                                ));
                                player.sendSystemMessage(Component.literal(
                                        "§6⚡ Golpe Definitivo (500%): " + (skills.hasCapstoneMelee() ? "§a§lDESBLOQUEADO §7[Tecla R]" : "§c§lBLOQUEADO §7(Req. Nivel 50 Melee)")
                                ));

                                // Rama 2: Arquería
                                player.sendSystemMessage(Component.literal(
                                        "§b💨 Viento a Favor: " + (skills.hasTailwind() ? "§a§lDESBLOQUEADO §7(Flechas +80% velocidad)" : "§c§lBLOQUEADO §7(Req. Nivel 5 Distancia)")
                                ));
                                player.sendSystemMessage(Component.literal(
                                        "§9⚡ Tiro Hipersónico: " + (skills.hasHypersonicArrow() ? "§a§lDESBLOQUEADO §7(Sneak + Disparo)" : "§c§lBLOQUEADO §7(Req. Nivel 50 Distancia)")
                                ));

                                // Sinergias
                                player.sendSystemMessage(Component.literal(
                                        "§d☯ Cazador Híbrido: " + (skills.hasHybridRangedMelee() ? "§a§lDESBLOQUEADO §7(Combo Flecha + Espada)" : "§c§lBLOQUEADO §7(Req. 25 en ambas)")
                                ));

                                player.sendSystemMessage(Component.literal("§6======================================================"));
                            });
                            return 1;
                        })
                )

                // =========================================================================
                // 2. COMANDO: /rpg upgrade <rama> (Para supervivencia, gasta XP legítima)
                // =========================================================================
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
                                    SkillEconomy.upgradeBranch(player, branch);
                                    return 1;
                                })
                        )
                )

                // =========================================================================
                // 3. COMANDO: /rpg addlevel <rama> <cantidad> (Modo Admin / Pruebas)
                // =========================================================================
                .then(Commands.literal("addlevel")
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
                                                    player.sendSystemMessage(Component.literal("§a[RPG] Nivel Melee aumentado a: §e" + skills.getMeleeLevel()));
                                                } else if (skill.equalsIgnoreCase("ranged")) {
                                                    skills.addRangedLevel(amount);
                                                    player.sendSystemMessage(Component.literal("§a[RPG] Nivel Ranged aumentado a: §e" + skills.getRangedLevel()));
                                                } else if (skill.equalsIgnoreCase("mobility")) {
                                                    skills.setMobilityLevel(skills.getMobilityLevel() + amount);
                                                    player.sendSystemMessage(Component.literal("§a[RPG] Nivel Movilidad aumentado a: §e" + skills.getMobilityLevel()));
                                                } else {
                                                    player.sendSystemMessage(Component.literal("§cRama desconocida. Usa: melee, ranged o mobility"));
                                                    return;
                                                }

                                                // Refrescamos atributos y sincronizamos con el cliente inmediatamente
                                                SkillAttributes.applyModifiers(player);
                                                SkillEconomy.syncSkills(player);
                                            });

                                            return 1;
                                        })
                                )
                        )
                )
        );
    }
}