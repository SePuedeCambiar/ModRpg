package com.example.modrpg.commands;

import com.example.modrpg.ModRpg;
import com.example.modrpg.ai.EnemyArchetype;
import com.example.modrpg.ai.EnemyRpgManager;
import com.example.modrpg.ai.SquadCoordinator;
import com.example.modrpg.ai.director.*;
import com.example.modrpg.ai.feedback.AmbushTelegraphHelper;
import com.example.modrpg.ai.nemesis.*;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillAttributes;
import com.example.modrpg.skills.SkillEconomy;
import com.example.modrpg.skills.data.SkillBranch;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RpgCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rpg")

                // =========================================================================
                // 1. /rpg stats
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

                                player.sendSystemMessage(Component.literal("§e--- ENERGÍA & TENSIÓN ---"));
                                player.sendSystemMessage(Component.literal("§b⚡ Maná: §f" + (int) skills.getCurrentMana() + " / " + (int) skills.getMaxMana()));

                                float stress = PlayerStressTracker.getStress(player);
                                var tier = PlayerStressTracker.getStressTier(stress);
                                var pacing = MacroDirectorManager.getPacingData(player.getUUID());
                                Set<PlayerVulnerabilityDetector.VulnerabilityType> vulns = PlayerVulnerabilityDetector.getActiveVulnerabilities(player);

                                player.sendSystemMessage(Component.literal("§7• Estrés: " + tier.getBadge() + " §f" + String.format("%.1f%%", stress * 100.0f) + " | Director: " + pacing.getState().getBadge()));
                                if (!vulns.isEmpty()) {
                                    StringBuilder sb = new StringBuilder("§c⚠ Vulnerabilidades: §f");
                                    vulns.forEach(v -> sb.append("[").append(v.getLabel()).append("] "));
                                    player.sendSystemMessage(Component.literal(sb.toString()));
                                }
                                player.sendSystemMessage(Component.literal("§6======================================================"));
                            });
                            return 1;
                        })
                )

                // =========================================================================
                // 2. /rpg respec
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
                                player.sendSystemMessage(Component.literal("§d§l✦ ¡VOTOS REINICIADOS! §7Tus ramas han vuelto a Nivel 20."));
                            });
                            return 1;
                        })
                )

                // =========================================================================
                // 3. /rpg upgrade <rama>
                // =========================================================================
                .then(Commands.literal("upgrade")
                        .then(Commands.argument("branch", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("melee").suggest("ranged").suggest("mobility").suggest("magic").suggest("defense");
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    SkillEconomy.upgradeBranch(player, StringArgumentType.getString(context, "branch"));
                                    return 1;
                                })
                        )
                )

                // =========================================================================
                // 4. COMANDOS ADMIN: addlevel y unlock
                // =========================================================================
                .then(Commands.literal("addlevel")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("branch", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ResourceLocation branchId = new ResourceLocation(ModRpg.MODID, StringArgumentType.getString(context, "branch").toLowerCase());
                                            int amount = IntegerArgumentType.getInteger(context, "amount");
                                            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                                                skills.addBranchLevel(branchId, amount);
                                                SkillAttributes.applyModifiers(player);
                                                SkillEconomy.syncSkills(player);
                                                player.sendSystemMessage(Component.literal("§a[RPG] Rama " + branchId.getPath() + " subida a nivel: " + skills.getBranchLevel(branchId)));
                                            });
                                            return 1;
                                        })
                                )
                        )
                )
                .then(Commands.literal("unlock")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ResourceLocation skillId = new ResourceLocation(ModRpg.MODID, StringArgumentType.getString(context, "skill"));
                                    player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                                        skills.unlockNode(skillId);
                                        SkillEconomy.syncSkills(player);
                                        player.sendSystemMessage(Component.literal("§a[RPG] Habilidad forzada: " + skillId));
                                    });
                                    return 1;
                                })
                        )
                )

                // =========================================================================
                // 5. SUITE DE DEBUG Y TESTING DE IA (/rpg debug ...)
                // =========================================================================
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))

                        // A. Forzar spawn de Horda 3D en la posición actual
                        .then(Commands.literal("spawn_horde")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ServerLevel level = player.serverLevel();
                                    NemesisHordeManager.spawnTacticalHorde(level, player);
                                    player.sendSystemMessage(Component.literal("§a[DEBUG] Incursión táctica generada a nivel Y=" + (int) player.getY()));
                                    return 1;
                                })
                        )

                        // B. Generar un Capitán Némesis individual con rasgo personalizado
                        .then(Commands.literal("spawn_captain")
                                .then(Commands.argument("trait", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (NemesisTrait t : NemesisTrait.values()) {
                                                builder.suggest(t.getId());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ServerLevel level = player.serverLevel();
                                            String traitName = StringArgumentType.getString(context, "trait");

                                            Zombie captainMob = EntityType.ZOMBIE.create(level);
                                            if (captainMob != null) {
                                                captainMob.moveTo(player.getX() + 3.0, player.getY(), player.getZ() + 3.0, 0, 0);

                                                NemesisCaptain captainData = new NemesisCaptain(
                                                        UUID.randomUUID(), "Gorgash", "el Destructor",
                                                        traitName, 50, (int) (level.getDayTime() / 24000L),
                                                        NemesisCaptain.Status.STALKING, "NONE"
                                                );

                                                NemesisSavedData.get(level).addOrUpdateCaptain(captainData);

                                                captainMob.addTag(NemesisHordeManager.TAG_NEMESIS_SQUAD);
                                                captainMob.addTag("modrpg_nemesis_captain");
                                                captainMob.getPersistentData().putUUID("modrpg_nemesis_uuid", captainData.getCaptainUUID());
                                                captainMob.getPersistentData().putString("modrpg_nemesis_trait", traitName);
                                                captainMob.setCustomName(Component.literal("§4§l" + captainData.getName() + " §6§l" + captainData.getTitle()));
                                                captainMob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                                                captainMob.goalSelector.addGoal(0, new com.example.modrpg.ai.nemesis.goals.NemesisEscapeGoal(captainMob));

                                                EnemyRpgManager.tryInitializeMob(captainMob);
                                                level.addFreshEntity(captainMob);

                                                player.sendSystemMessage(Component.literal("§a[DEBUG] Capitán Némesis generado con rasgo: §e" + traitName));
                                            }
                                            return 1;
                                        })
                                )
                        )

                        // C. Dañar al Némesis cercano a <25% HP para testear huida en cuevas
                        .then(Commands.literal("hurt_captain")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    AABB search = player.getBoundingBox().inflate(16.0);
                                    List<Monster> mobs = player.serverLevel().getEntitiesOfClass(
                                            Monster.class, search, m -> m.getTags().contains("modrpg_nemesis_captain") && m.isAlive()
                                    );

                                    if (mobs.isEmpty()) {
                                        player.sendSystemMessage(Component.literal("§c[DEBUG] No hay ningún Capitán Némesis cerca."));
                                        return 0;
                                    }

                                    Monster captain = mobs.get(0);
                                    captain.setHealth(captain.getMaxHealth() * 0.20f); // Fijar vida al 20%
                                    player.sendSystemMessage(Component.literal("§e[DEBUG] Vida del Capitán reducida al 20% (" + (int) captain.getHealth() + " HP). Observa su escape."));
                                    return 1;
                                })
                        )

                        // D. Forzar Fase del Alien Director (REPRIEVE, BUILD_UP, AMBUSH_READY, CLIMAX)
                        .then(Commands.literal("set_director")
                                .then(Commands.argument("state", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (DirectorState s : DirectorState.values()) builder.suggest(s.name());
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String stateName = StringArgumentType.getString(context, "state");
                                            DirectorState state = DirectorState.valueOf(stateName);
                                            MacroDirectorManager.getPacingData(player.getUUID()).setState(state, 600);
                                            player.sendSystemMessage(Component.literal("§a[DEBUG] Fase del Director forzada a: " + state.getBadge()));
                                            return 1;
                                        })
                                )
                        )

                        // E. Modificar Estrés Psicológico (0.0 a 1.0)
                        .then(Commands.literal("set_stress")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.0f, 1.0f))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            float val = FloatArgumentType.getFloat(context, "value");
                                            PlayerStressTracker.getData(player.getUUID()).setStress(val);
                                            player.sendSystemMessage(Component.literal("§a[DEBUG] Nivel de estrés fijado en: " + String.format("%.2f", val)));
                                            return 1;
                                        })
                                )
                        )

                        // F. Probar pre-aviso de emboscada (Verificar si cae grava o vibra el suelo)
                        .then(Commands.literal("test_ambush_cue")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    AmbushTelegraphHelper.triggerAmbushPreCue(player.serverLevel(), player);
                                    player.sendSystemMessage(Component.literal("§a[DEBUG] Pre-aviso sensorial disparado en tu posición."));
                                    return 1;
                                })
                        )

                        // G. Diagnóstico de Escuadrón F.E.A.R. cercano (Tokens y Coberturas)
                        .then(Commands.literal("squad_info")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    AABB search = player.getBoundingBox().inflate(24.0);
                                    List<Monster> mobs = player.serverLevel().getEntitiesOfClass(
                                            Monster.class, search, m -> m.isAlive() && SquadCoordinator.getSquadFor(m) != null
                                    );

                                    if (mobs.isEmpty()) {
                                        player.sendSystemMessage(Component.literal("§c[DEBUG] No hay escuadrones tácticos activos cerca de ti."));
                                        return 0;
                                    }

                                    player.sendSystemMessage(Component.literal("§6================ DIAGNÓSTICO DE ESCUADRÓN ================"));
                                    SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mobs.get(0));
                                    player.sendSystemMessage(Component.literal("§eSquad ID: §f" + squad.squadId));
                                    player.sendSystemMessage(Component.literal("§eMiembros vivos: §f" + squad.memberUUIDs.size()));
                                    player.sendSystemMessage(Component.literal("§eEn pánico: §f" + squad.isInPanic()));
                                    player.sendSystemMessage(Component.literal("§ePetición de auxilio (Peel): §f" + squad.isPeelRequested()));

                                    for (Monster m : mobs) {
                                        String name = m.getCustomName() != null ? m.getCustomName().getString() : m.getName().getString();
                                        boolean isStaggered = m.getTags().contains("modrpg_staggered");
                                        player.sendSystemMessage(Component.literal(
                                                "§7• " + name + " §7| HP: §a" + (int) m.getHealth() + " §7| Stagger: " + (isStaggered ? "§c[SÍ]" : "§a[NO]")
                                        ));
                                    }
                                    player.sendSystemMessage(Component.literal("§6=========================================================="));
                                    return 1;
                                })
                        )
                )
        );
    }
}