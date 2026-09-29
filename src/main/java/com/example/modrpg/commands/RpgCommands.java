package com.example.modrpg.commands;

import com.example.modrpg.ModRpg;
import com.example.modrpg.ai.director.PlayerStressTracker;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class RpgCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rpg")

                // =========================================================================
                // 1. COMANDO: /rpg stats
                // =========================================================================
                .then(Commands.literal("stats")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                                player.sendSystemMessage(Component.literal("§6================ TUS ESTADÍSTICAS RPG ================"));

                                ResourceLocation primary = skills.getPrimaryBranch();
                                ResourceLocation secondary = skills.getSecondaryBranch();

                                player.sendSystemMessage(Component.literal(
                                        "§e★ Especializaciones: §6Principal: " + (primary != null ? "§l" + primary.getPath().toUpperCase() : "§7[Sin sellar]") +
                                                " §7| §bSecundaria: " + (secondary != null ? "§l" + secondary.getPath().toUpperCase() : "§7[Sin sellar]")
                                ));

                                player.sendSystemMessage(Component.literal("§e--- NIVELES DE RAMAS ---"));
                                for (SkillBranch branch : SkillRegistry.getAllBranches()) {
                                    int lvl = skills.getBranchLevel(branch.id());
                                    int maxAllowed = skills.getMaxLevelForBranch(branch.id());
                                    int practice = skills.getPractice(branch.practiceCounterId());
                                    int reqPractice = SkillEconomy.getRequiredPractice(lvl + 1);

                                    String capTag = (lvl >= maxAllowed) ? " §6[LÍMITE " + maxAllowed + "]" : " §7(Práctica: §b" + practice + "/" + reqPractice + "§7)";
                                    player.sendSystemMessage(Component.literal(
                                            "§a• " + branch.displayName().getString() + ": §fNivel " + lvl + "/" + maxAllowed + capTag
                                    ));
                                }

                                player.sendSystemMessage(Component.literal("§e--- PRÁCTICA Y COMBATE ---"));
                                player.sendSystemMessage(Component.literal("§7• Bajas CaC: §e" + skills.getPractice(SkillRegistry.COUNTER_MELEE_KILLS)));
                                player.sendSystemMessage(Component.literal("§7• Bajas a Distancia: §e" + skills.getPractice(SkillRegistry.COUNTER_RANGED_KILLS)));
                                player.sendSystemMessage(Component.literal("§7• Monstruos Campeones / Élites: §e" + skills.getPractice(SkillRegistry.COUNTER_ELITE_KILLS)));
                                player.sendSystemMessage(Component.literal("§7• Lanzamientos Mágicos: §e" + skills.getPractice(SkillRegistry.COUNTER_MAGIC_CASTS)));
                                player.sendSystemMessage(Component.literal("§7• Golpes Mitigados: §e" + skills.getPractice(SkillRegistry.COUNTER_DAMAGE_BLOCKED)));
                                player.sendSystemMessage(Component.literal("§7• Distancia Recorrida: §e" + skills.getPractice(SkillRegistry.COUNTER_DISTANCE_RUN) + " bloques"));

                                player.sendSystemMessage(Component.literal("§e--- ENERGÍA ---"));
                                player.sendSystemMessage(Component.literal("§b⚡ Maná: §f" + (int) skills.getCurrentMana() + " / " + (int) skills.getMaxMana() + " §7(+" + String.format("%.1f", skills.getManaRegenPerSecond()) + "/s)"));

                                // =========================================================================
                                // SPRINT 5: TELEMETRÍA DEL MACRO-DIRECTOR (TENSIÓN Y ESTRÉS)
                                // =========================================================================
                                float stress = PlayerStressTracker.getStress(player);
                                var tier = PlayerStressTracker.getStressTier(stress);

                                player.sendSystemMessage(Component.literal("§e--- TENSIÓN PSICOLÓGICA (DIRECTOR) ---"));
                                player.sendSystemMessage(Component.literal(
                                        "§7• Estado de Tensión: " + tier.getBadge() + " §f" + String.format("%.1f%%", stress * 100.0f)
                                ));

                                player.sendSystemMessage(Component.literal("§6======================================================"));
                            });
                            return 1;
                        })
                )

                // =========================================================================
                // 2. COMANDO: /rpg respec (Reasignación de Especializaciones)
                // =========================================================================
                .then(Commands.literal("respec")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                                if (skills.getPrimaryBranch() == null && skills.getSecondaryBranch() == null) {
                                    player.sendSystemMessage(Component.literal("§e[RPG] Aún no has sellado ninguna especialización superior al Nivel 20."));
                                    return;
                                }

                                skills.respecSpecializations();
                                SkillAttributes.applyModifiers(player);
                                SkillEconomy.syncSkills(player);

                                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                        SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.2f);

                                player.sendSystemMessage(Component.literal(
                                        "§d§l✦ ¡VOTOS REINICIADOS! ✦\n" +
                                                "§7Tus votos de especialización han sido liberados y las ramas vuelven a Nivel 20.\n" +
                                                "§aAhora puedes elegir una nueva Rama Principal y Secundaria."
                                ));
                            });
                            return 1;
                        })
                )

                // =========================================================================
                // 3. COMANDO: /rpg upgrade <rama>
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
                // 4. COMANDO ADMIN: /rpg addlevel <rama> <cantidad>
                // =========================================================================
                .then(Commands.literal("addlevel")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("branch", StringArgumentType.word())
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
                // 5. COMANDO ADMIN: /rpg unlock <skill_id>
                // =========================================================================
                .then(Commands.literal("unlock")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("skill", StringArgumentType.word())
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