package com.example.modrpg.events;

import com.example.modrpg.ModRpg;
import com.example.modrpg.ai.ChampionAffix;
import com.example.modrpg.ai.EnemyRpgManager;
import com.example.modrpg.ai.SquadCoordinator;
import com.example.modrpg.ai.TacticalCasterGoal;
import com.example.modrpg.ai.director.AudioFootprintTracker;
import com.example.modrpg.ai.director.MacroDirectorManager;
import com.example.modrpg.ai.director.PlayerStressTracker;
import com.example.modrpg.ai.director.PlayerVulnerabilityDetector;
import com.example.modrpg.ai.nemesis.NemesisDialogueHelper;
import com.example.modrpg.ai.nemesis.NemesisHordeManager;
import com.example.modrpg.ai.nemesis.NemesisSavedData;
import com.example.modrpg.ai.nemesis.PlayerCombatProfiler;
import com.example.modrpg.commands.RpgCommands;
import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketSyncMana;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillAttributes;
import com.example.modrpg.skills.SkillEconomy;
import com.example.modrpg.skills.SkillProgression;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import com.example.modrpg.skills.magic.modular.ElementalReactionManager;
import com.example.modrpg.skills.nodes.defense.IronStrengthSkill;
import com.example.modrpg.skills.nodes.magic.MinionHelper;
import com.example.modrpg.skills.nodes.mobility.AirJumpSkill;
import com.example.modrpg.skills.nodes.mobility.ImpactJumpSkill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStoppedEvent;



import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ModRpg.MODID)
public class ModEvents {
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SquadCoordinator.clearAll();
        AudioFootprintTracker.clearAll();
    }

    // =========================================================================
    // 1. GESTIÓN DE CAPABILITIES Y CICLO DE VIDA DEL JUGADOR
    // =========================================================================

    @SuppressWarnings({"removal", "deprecation"})
    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(
                    new ResourceLocation(ModRpg.MODID, "player_skills"),
                    new PlayerSkillsProvider()
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(oldSkills -> {
            event.getEntity().getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(newSkills -> {
                newSkills.copyFrom(oldSkills);
            });
        });
        event.getOriginal().invalidateCaps();

        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // SPRINT 1 FIX: Reaplicar modificadores tras clonar (muerte/respawn)
            SkillAttributes.applyModifiers(serverPlayer);
            SkillEconomy.syncSkills(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("§a[ModRpg] §f¡Sistema RPG cargado con éxito! Usa §e/rpg stats§f para ver tu progreso."));
            SkillAttributes.applyModifiers(serverPlayer);
            SkillEconomy.syncSkills(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        PlayerStressTracker.clearPlayer(uuid);
        PlayerVulnerabilityDetector.clearPlayer(uuid);
        MacroDirectorManager.clearPlayer(uuid);
        // SPRINT 1 FIX: Purgar cooldown de incursión Némesis
        NemesisHordeManager.clearPlayer(uuid);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RpgCommands.register(event.getDispatcher());
    }

    // =========================================================================
    // 2. TICK DEL SERVIDOR (NIVEL): Escuadrones Tácticos a 1x por Tick
    // =========================================================================

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        // SPRINT 1 FIX: Corre exactamente 1 vez por tick del nivel sin multiplicar por jugadores
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
            SquadCoordinator.tickSquads(serverLevel);
        }
    }

    // =========================================================================
    // 3. CICLO DE VIDA DE ENTIDADES: Evicción de Fugas por Despawn / Chunk Unload
    // =========================================================================

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        // SPRINT 1 FIX: Purgar mobs que despawnean por distancia (>128 bloques) o descarga de chunks
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob) {
            SquadCoordinator.onMobDespawnOrLeave(mob);
        }
    }

    // =========================================================================
    // 4. TICK DEL JUGADOR (Servidor: Habilidades, Movilidad, Maná y Director)
    // =========================================================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;

        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            if (!player.level().isClientSide()) {
                skills.tickServerSide();

                // A. Control de duración de Fuerza de Hierro (12s = 240 ticks)
                if (player.getTags().contains(IronStrengthSkill.TAG_IRON_STRENGTH)) {
                    CompoundTag data = player.getPersistentData();
                    int timer = data.getInt("modrpg_iron_strength_timer");
                    if (timer <= 0) {
                        data.putInt("modrpg_iron_strength_timer", 240);
                    } else {
                        timer--;
                        if (timer <= 0) {
                            player.removeTag(IronStrengthSkill.TAG_IRON_STRENGTH);
                            data.remove("modrpg_iron_strength_timer");
                            if (player instanceof ServerPlayer sp) {
                                sp.displayClientMessage(Component.literal("§7🛡 Tu Fuerza de Hierro se ha disipado."), true);
                            }
                        } else {
                            data.putInt("modrpg_iron_strength_timer", timer);
                        }
                    }
                }

                // B. Control físico y temporizador de la Red Gravitatoria del Némesis
                if (player.getTags().contains("modrpg_grounded_tether")) {
                    CompoundTag data = player.getPersistentData();
                    int tetherTimer = data.getInt("modrpg_tether_timer");
                    if (tetherTimer <= 0) {
                        player.removeTag("modrpg_grounded_tether");
                        data.remove("modrpg_tether_timer");
                        if (player instanceof ServerPlayer sp) {
                            sp.displayClientMessage(Component.literal("§a⛓ Te has liberado de la red gravitatoria."), true);
                        }
                    } else {
                        data.putInt("modrpg_tether_timer", tetherTimer - 1);
                        Vec3 vel = player.getDeltaMovement();
                        if (vel.y > 0.0) {
                            player.setDeltaMovement(new Vec3(vel.x, -0.2, vel.z));
                            player.hurtMarked = true;
                        }
                    }
                }

                // C. SPRINT 2 FIX: Acumulador de práctica de Movilidad por distancia a pie
                if (player instanceof ServerPlayer serverPlayer) {
                    CompoundTag data = serverPlayer.getPersistentData();

                    double currentX = serverPlayer.getX();
                    double currentZ = serverPlayer.getZ();

                    if (!data.contains("modrpg_last_x")) {
                        data.putDouble("modrpg_last_x", currentX);
                        data.putDouble("modrpg_last_z", currentZ);
                    }

                    double lastX = data.getDouble("modrpg_last_x");
                    double lastZ = data.getDouble("modrpg_last_z");

                    double dx = currentX - lastX;
                    double dz = currentZ - lastZ;
                    double distSq = dx * dx + dz * dz;

                    // Permite sprint-jumping, escaleras y desniveles.
                    // Excluye vehículos, vuelo en creativo/espectador y teletransporte (> 16 bloques en 1 tick).
                    boolean isFlying = serverPlayer.getAbilities().flying;
                    boolean isRiding = serverPlayer.isPassenger();

                    if (distSq > 0.0025 && distSq < 256.0 && !isFlying && !isRiding) {
                        double acc = data.getDouble("modrpg_distance_acc") + Math.sqrt(distSq);
                        if (acc >= 1.0) {
                            int fullBlocks = (int) acc;
                            skills.addPractice(SkillRegistry.COUNTER_DISTANCE_RUN, fullBlocks);
                            data.putDouble("modrpg_distance_acc", acc - fullBlocks);
                        }
                    }

                    data.putDouble("modrpg_last_x", currentX);
                    data.putDouble("modrpg_last_z", currentZ);

                    // D. SPRINT 2 FIX: Emisión de Huella Acústica basada en delta real
                    if (serverPlayer.tickCount % 10 == 0) {
                        if (serverPlayer.isShiftKeyDown()) {
                            AudioFootprintTracker.emitPing(serverPlayer, AudioFootprintTracker.NoiseCategory.SNEAK);
                        } else if (serverPlayer.isSprinting() && distSq > 0.005) {
                            AudioFootprintTracker.emitPing(serverPlayer, AudioFootprintTracker.NoiseCategory.SPRINT);
                        } else if (distSq > 0.005) {
                            AudioFootprintTracker.emitPing(serverPlayer, AudioFootprintTracker.NoiseCategory.WALK);
                        }
                    }

                    // E. Reseteo de minería si el jugador se detiene
                    if (serverPlayer.tickCount % 5 == 0) {
                        int lastMineTick = data.getInt("modrpg_last_mine_tick");
                        if (serverPlayer.tickCount - lastMineTick > 15) {
                            PlayerVulnerabilityDetector.resetMiningProgress(serverPlayer.getUUID());
                        }
                    }

                    // F. CICLO CADA SEGUNDO (20 Ticks): Maná, Estrés, Macro-Director, Incursiones y Sincronización
                    if (serverPlayer.tickCount % 20 == 0) {
                        ModMessages.sendToPlayer(
                                new PacketSyncMana(skills.getCurrentMana(), skills.getMaxMana()),
                                serverPlayer
                        );

                        PlayerStressTracker.tick(serverPlayer);
                        AudioFootprintTracker.cleanupExpiredPings(serverPlayer.tickCount);
                        MacroDirectorManager.tick(serverPlayer);
                        NemesisHordeManager.tryTriggerNemesisRaid(serverPlayer.serverLevel(), serverPlayer);

                        // SPRINT 2 FIX: Sincronizar skills al cliente para actualizar puntos de práctica en el árbol [K]
                        SkillEconomy.syncSkills(serverPlayer);
                    }
                }

                // G. Feedback si se agota el maná por posturas sostenidas
                if (skills.consumeTogglesForceDeactivated() && player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                            Component.literal("§c§l⚡ ¡MANÁ AGOTADO! §7Tus posturas activas se han desactivado."),
                            true
                    );
                    sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                            SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.2f);
                    SkillEconomy.syncSkills(sp);
                }

            } else {
                skills.tickCooldowns();
            }
        });
    }

    // =========================================================================
    // 5. DETECCIÓN ACÚSTICA DE MINERÍA DE BLOQUES DUROS
    // =========================================================================

    @SubscribeEvent
    public static void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            BlockPos pos = event.getPos();
            BlockState state = event.getLevel().getBlockState(pos);

            if (state.getDestroySpeed(event.getLevel(), pos) >= 1.5f) {
                PlayerVulnerabilityDetector.recordMiningProgress(player);
                player.getPersistentData().putInt("modrpg_last_mine_tick", player.tickCount);

                if (player.tickCount % 10 == 0) {
                    AudioFootprintTracker.emitPing(player, AudioFootprintTracker.NoiseCategory.MINING);
                }
            }
        }
    }

    // =========================================================================
    // 6. TICK DE ENTIDADES VIVAS: Esbirros, Decaimiento Elemental y Némesis
    // =========================================================================

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        if (entity.getTags().contains(MinionHelper.TAG_MINION)) {
            MinionHelper.tickMinion(entity);
        }

        ElementalReactionManager.tickPrimers(entity);

        if (entity instanceof Monster monster && monster.getTags().contains("modrpg_affix_commander")) {
            if (monster.tickCount % 20 == 0) {
                ServerLevel level = (ServerLevel) monster.level();
                ChampionAffix.emitCommanderAura(monster, level);

                AABB aura = monster.getBoundingBox().inflate(12.0);
                List<Monster> allies = level.getEntitiesOfClass(Monster.class, aura, m -> m != monster && m.isAlive());
                for (Monster ally : allies) {
                    ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 0, false, false));
                    ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, false, false));
                }
            }
        }

        if (entity instanceof Monster monster && monster.getTags().contains("modrpg_nemesis_captain") && monster.isAlive()) {
            if (monster.tickCount % 20 == 0 && monster.getTarget() instanceof ServerPlayer player) {
                if (monster.distanceToSqr(player) <= 256.0) {
                    UUID captainId = monster.getPersistentData().getUUID("modrpg_nemesis_uuid");
                    var nemesisData = NemesisSavedData.get((ServerLevel) monster.level());
                    var captain = nemesisData.getCaptain(captainId);
                    if (captain != null) {
                        NemesisDialogueHelper.triggerIntro((ServerLevel) monster.level(), monster, captain, player);
                    }
                }
            }
        }
    }

    // =========================================================================
    // 7. ATAQUE INICIAL: Fuego Amigo, I-Frames de Dash y Fuerza de Hierro
    // =========================================================================

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        Entity attacker = event.getSource().getEntity();
        Entity victim = event.getEntity();

        if (MinionHelper.areAllies(attacker, victim)) {
            event.setCanceled(true);
            return;
        }

        if (victim instanceof ServerPlayer player) {
            if (player.getTags().contains(IronStrengthSkill.TAG_IRON_STRENGTH)) {
                event.setCanceled(true);
                player.heal(2.5f);

                ServerLevel level = (ServerLevel) player.level();
                level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.5f);
                return;
            }

            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                if (skills.hasDashIFrames()) {
                    event.setCanceled(true);
                    ServerLevel level = (ServerLevel) player.level();
                    level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.5f, 2.0f);
                }
            });
        }
    }

    // =========================================================================
    // 8. CAÍDA FÍSICA: Salto con Impacto y Salto de Viento
    // =========================================================================

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = (ServerLevel) player.level();

            if (player.getTags().contains(ImpactJumpSkill.TAG_GROUND_SLAM)) {
                player.removeTag(ImpactJumpSkill.TAG_GROUND_SLAM);
                event.setCanceled(true);

                AABB blastArea = player.getBoundingBox().inflate(5.5, 2.0, 5.5);
                List<LivingEntity> enemies = level.getEntitiesOfClass(
                        LivingEntity.class, blastArea,
                        e -> e != player && e.isAlive() && !MinionHelper.areAllies(player, e)
                );

                for (LivingEntity enemy : enemies) {
                    enemy.hurt(player.damageSources().playerAttack(player), 150.0f);
                    double dx = enemy.getX() - player.getX();
                    double dz = enemy.getZ() - player.getZ();
                    enemy.knockback(1.8, -dx, -dz);
                }

                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY(), player.getZ(), 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0, 0, 0, 0);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 0.8f);

                player.displayClientMessage(
                        Component.literal("§4§l💥 ¡DETONACIÓN SÍSMICA! §c150 Daño infligido a §e" + enemies.size() + "§c objetivos"),
                        true
                );
                return;
            }

            if (player.getTags().contains(AirJumpSkill.AIR_JUMP_SAFE_TAG)) {
                player.removeTag(AirJumpSkill.AIR_JUMP_SAFE_TAG);
                event.setCanceled(true);

                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 10, 0.3, 0.05, 0.3, 0.05);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_FALL, SoundSource.PLAYERS, 1.0f, 1.4f);
            }
        }
    }

    // =========================================================================
    // 9. MUERTE DE ENTIDADES: Escuadrones, Némesis y Práctica Ponderada
    // =========================================================================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || victim instanceof Player) return;

        if (victim instanceof Mob mob) {
            SquadCoordinator.onMobDeath(mob);
        }

        DamageSource source = event.getSource();
        Entity directAttacker = source.getDirectEntity();
        Entity trueAttacker = source.getEntity();
        ServerLevel level = (ServerLevel) victim.level();

        ServerPlayer player = null;

        if (trueAttacker instanceof ServerPlayer sp) {
            player = sp;
        } else if (directAttacker instanceof ServerPlayer sp) {
            player = sp;
        } else if (trueAttacker != null && trueAttacker.getTags().contains(MinionHelper.TAG_MINION)) {
            for (String tag : trueAttacker.getTags()) {
                if (tag.startsWith(MinionHelper.TAG_OWNER_PREFIX)) {
                    try {
                        String uuidStr = tag.substring(MinionHelper.TAG_OWNER_PREFIX.length());
                        player = (ServerPlayer) level.getPlayerByUUID(UUID.fromString(uuidStr));
                    } catch (Exception ignored) {}
                    break;
                }
            }
        } else if (victim.getLastHurtByMob() instanceof ServerPlayer sp) {
            player = sp;
        }

        // D3 FIX: Actualizar a DEAD en NemesisSavedData incluso si murió por causas ambientales (player == null)
        if (victim instanceof Monster deadMonster && deadMonster.getTags().contains("modrpg_nemesis_captain")) {
            UUID captainId = deadMonster.getPersistentData().getUUID("modrpg_nemesis_uuid");
            var nemesisData = NemesisSavedData.get(level);
            var captain = nemesisData.getCaptain(captainId);
            if (captain != null) {
                NemesisHordeManager.handleCaptainDeath(nemesisData, captain);
                if (player != null) {
                    NemesisDialogueHelper.triggerDeath(level, deadMonster, captain, player);
                    NemesisHordeManager.onNemesisKilled(level, deadMonster, player);
                }
            }
            NemesisDialogueHelper.clearNemesisMemory(deadMonster.getUUID());
        }

        if (player != null) {
            final ServerPlayer finalPlayer = player;
            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                boolean isRanged = (directAttacker instanceof Projectile) || source.is(DamageTypes.ARROW);

                int practiceReward = 1;
                String tierLabel = "";

                boolean isBoss = victim.getType() == EntityType.WITHER ||
                        victim.getType() == EntityType.ENDER_DRAGON ||
                        victim.getType() == EntityType.WARDEN ||
                        victim.getType() == EntityType.ELDER_GUARDIAN;

                if (isBoss) {
                    practiceReward = 50;
                    tierLabel = " §6[JEFE +50]";
                } else if (victim.getTags().contains("modrpg_champion") || victim.getTags().contains("modrpg_nemesis_captain")) {
                    practiceReward = 8;
                    tierLabel = " §e[CAMPEÓN / NÉMESIS +8]";
                    skills.addPractice(SkillRegistry.COUNTER_ELITE_KILLS, 1);
                } else if (victim.getTags().contains(EnemyRpgManager.TAG_CASTER)) {
                    practiceReward = 3;
                    tierLabel = " §b[CASTER +3]";
                }

                if (isRanged) {
                    skills.addPractice(SkillRegistry.COUNTER_RANGED_KILLS, practiceReward);
                    int count = skills.getPractice(SkillRegistry.COUNTER_RANGED_KILLS);
                    finalPlayer.displayClientMessage(
                            Component.literal("§b🏹 ¡Baja a distancia!" + tierLabel + " §7(Total: §e" + count + "§7)"),
                            true
                    );
                } else {
                    skills.addPractice(SkillRegistry.COUNTER_MELEE_KILLS, practiceReward);
                    int count = skills.getPractice(SkillRegistry.COUNTER_MELEE_KILLS);
                    finalPlayer.displayClientMessage(
                            Component.literal("§c⚔ ¡Baja cuerpo a cuerpo!" + tierLabel + " §7(Total: §e" + count + "§7)"),
                            true
                    );
                }

                level.playSound(null, finalPlayer.getX(), finalPlayer.getY(), finalPlayer.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.8f);

                SkillEconomy.checkMilestones(finalPlayer, skills);
                SkillEconomy.syncSkills(finalPlayer);
            });
        }
    }
    // =========================================================================
    // 10. PROYECTILES DEL JUGADOR
    // =========================================================================

    @SubscribeEvent
    public static void onArrowSpawn(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof AbstractArrow arrow) {
            if (arrow.getOwner() instanceof ServerPlayer player) {
                player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                    for (ResourceLocation nodeId : skills.getUnlockedNodes()) {
                        SkillNode node = SkillRegistry.get(nodeId);
                        if (node != null) {
                            node.onArrowShoot(player, event, arrow, skills);
                        }
                    }
                });
            }
        }
    }

    // =========================================================================
    // 11. CÁLCULO DE DAÑO: Escalado CaC, Afijos, Contramedidas Némesis y Reacciones
    // =========================================================================

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity attacker = event.getSource().getEntity();
        Entity target = event.getEntity();

        // ---------------------------------------------------------------------
        // A. AFIJOS BÁSICOS DE CAMPEÓN
        // ---------------------------------------------------------------------
        if (target instanceof Monster monster && monster.getTags().contains("modrpg_affix_runic_shield")) {
            if (event.getSource().getDirectEntity() instanceof Projectile projectile) {
                Vec3 look = monster.getLookAngle();
                Vec3 toProjectile = projectile.position().subtract(monster.position()).normalize();
                if (look.dot(toProjectile) > 0.2) {
                    event.setCanceled(true);
                    ServerLevel level = (ServerLevel) monster.level();
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT, projectile.getX(), projectile.getY(), projectile.getZ(), 15, 0.3, 0.3, 0.3, 0.1);
                    level.playSound(null, monster.getX(), monster.getY(), monster.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.2f, 1.5f);
                    if (attacker instanceof ServerPlayer sp) {
                        sp.displayClientMessage(Component.literal("§b🛡 ¡El Escudo Rúnico desvió tu flecha frontal! Flanquéalo."), true);
                    }
                    return;
                }
            }
        }

        if (attacker instanceof Monster monster && monster.getTags().contains("modrpg_affix_vampiric")) {
            float heal = event.getAmount() * 0.25f;
            monster.heal(heal);
            if (monster.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, monster.getX(), monster.getEyeY(), monster.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
            }
        }

        if (attacker instanceof Monster monster && monster.getTags().contains("modrpg_affix_mana_burn")) {
            if (target instanceof ServerPlayer player) {
                player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                    skills.consumeMana(20.0f);
                    ModMessages.sendToPlayer(new PacketSyncMana(skills.getCurrentMana(), skills.getMaxMana()), player);
                    player.displayClientMessage(Component.literal("§9§l⚡ ¡QUEMADURA DE MANÁ! §c(-20 Maná)"), true);
                });
            }
        }

        // ---------------------------------------------------------------------
        // B. CONTRAMEDIDAS ADAPTATIVAS DEL CAPITÁN NÉMESIS
        // ---------------------------------------------------------------------
        if (attacker instanceof Monster monster && monster.getTags().contains("modrpg_nemesis_captain") && target instanceof ServerPlayer victim) {
            String trait = monster.getPersistentData().getString("modrpg_nemesis_trait");
            ServerLevel level = (ServerLevel) victim.level();

            if ("SHIELD_BREAKER".equals(trait) && victim.isBlocking()) {
                victim.disableShield(true);
                victim.stopUsingItem();

                level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2f, 1.8f);
                level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.5f, 0.8f);
                level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getEyeY(), victim.getZ(), 25, 0.3, 0.3, 0.3, 0.15);

                victim.displayClientMessage(Component.literal("§4§l⚡ ¡ESCUDO DESTROZADO! §c(Desactivado por 5s)"), true);
            }

            if ("ANTI_AIR_GRAVITY".equals(trait)) {
                victim.addTag("modrpg_grounded_tether");
                victim.getPersistentData().putInt("modrpg_tether_timer", 120);

                level.sendParticles(ParticleTypes.PORTAL, victim.getX(), victim.getY() + 0.2, victim.getZ(), 30, 0.4, 0.1, 0.4, 0.1);
                level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.4f, 0.7f);

                victim.displayClientMessage(Component.literal("§c⛓ ¡ANCLADO AL SUELO! §7(Movimiento vertical bloqueado por 6s)"), true);
            }

            if ("MANA_DRAINER".equals(trait)) {
                victim.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                    skills.consumeMana(25.0f);
                    ModMessages.sendToPlayer(new PacketSyncMana(skills.getCurrentMana(), skills.getMaxMana()), victim);
                });

                if (monster.getPersistentData().contains("modrpg_primer_timer")) {
                    monster.getPersistentData().putInt("modrpg_primer_timer", 20);
                }

                victim.displayClientMessage(Component.literal("§5⚡ ¡DISIPACIÓN ARCANA! §7(-25 Maná / Cebados disipados)"), true);
            }
        }

        if (target instanceof Monster monster && monster.getTags().contains("modrpg_nemesis_captain")) {
            String trait = monster.getPersistentData().getString("modrpg_nemesis_trait");
            if ("PROJECTILE_DEFLECTOR".equals(trait) && event.getSource().getDirectEntity() instanceof AbstractArrow arrow) {
                Vec3 look = monster.getLookAngle();
                Vec3 toArrow = arrow.position().subtract(monster.position()).normalize();

                if (look.dot(toArrow) > 0.0) {
                    event.setCanceled(true);
                    ServerLevel level = (ServerLevel) monster.level();
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT, arrow.getX(), arrow.getY(), arrow.getZ(), 15, 0.3, 0.3, 0.3, 0.1);
                    level.playSound(null, monster.getX(), monster.getY(), monster.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.2f, 1.4f);

                    if (attacker instanceof ServerPlayer sp) {
                        sp.displayClientMessage(Component.literal("§b🛡 ¡El Muro Cinético desvió tu flecha frontal! Flanquéalo."), true);
                    }
                    return;
                }
            }
        }

        // ---------------------------------------------------------------------
        // C. SI EL JUGADOR ES EL AGRESOR
        // ---------------------------------------------------------------------
        if (attacker instanceof ServerPlayer player && target instanceof LivingEntity livingTarget) {
            MinionHelper.redirectMinionsTarget(player, livingTarget, 16.0);

            if (event.getSource().getDirectEntity() instanceof AbstractArrow) {
                PlayerCombatProfiler.recordRangedDamage(player, event.getAmount());
            } else if (event.getSource().is(DamageTypes.MAGIC) || event.getSource().is(DamageTypes.INDIRECT_MAGIC)) {
                String favElement = PlayerCombatProfiler.getFavoriteElement(player);
                PlayerCombatProfiler.recordMagicCast(player, favElement);
            } else {
                PlayerCombatProfiler.recordMeleeDamage(player, event.getAmount());
            }

            if (livingTarget instanceof Mob mob && mob.getTags().contains(TacticalCasterGoal.TAG_INTERRUPTIBLE)) {
                if (player.getAttackStrengthScale(0.5f) >= 0.85f) {
                    TacticalCasterGoal.interruptCaster(mob, player);
                    player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(s -> {
                        s.addPractice(SkillRegistry.COUNTER_STAGGER_INTERRUPTS, 1);
                    });
                }
            }

            if (livingTarget.getTags().contains(TacticalCasterGoal.TAG_STAGGERED)) {
                event.setAmount(event.getAmount() * 1.30f);
            }

            if (livingTarget.getTags().contains("modrpg_brittle_ice")) {
                livingTarget.removeTag("modrpg_brittle_ice");
                event.setAmount(event.getAmount() * 1.50f);
                ServerLevel level = (ServerLevel) player.level();
                level.sendParticles(ParticleTypes.SNOWFLAKE, livingTarget.getX(), livingTarget.getY() + 1.0, livingTarget.getZ(), 25, 0.4, 0.4, 0.4, 0.1);
                level.playSound(null, livingTarget.getX(), livingTarget.getY(), livingTarget.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.5f, 1.2f);
                player.displayClientMessage(Component.literal("§3💥 ¡GOLPE DE ROTURA DE HIELO! §f(+50% Daño)"), true);
            }

            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                if (event.getSource().is(DamageTypes.MAGIC) || event.getSource().is(DamageTypes.INDIRECT_MAGIC)) {
                    skills.addPractice(SkillRegistry.COUNTER_MAGIC_CASTS, 1);
                }

                // ARQUERÍA: Multiplicador progresivo a distancia
                if (event.getSource().getDirectEntity() instanceof AbstractArrow) {
                    int rangedLvl = skills.getBranchLevel(SkillRegistry.BRANCH_RANGED);
                    if (rangedLvl > 0) {
                        float bonus = 1.0f + (float) Math.pow(rangedLvl / 100.0, 1.5) * 2.5f;
                        event.setAmount(event.getAmount() * bonus);
                    }
                }

                // SPRINT 2 FIX: Escalado real de daño Cuerpo a Cuerpo (CaC)
                // Se aplica a golpes directos con la mano/arma (no flechas, no magia, no explosiones)
                boolean isDirectMelee = event.getSource().getDirectEntity() == player
                        && !(event.getSource().getDirectEntity() instanceof Projectile)
                        && !event.getSource().is(DamageTypes.ARROW)
                        && !event.getSource().is(DamageTypes.MAGIC)
                        && !event.getSource().is(DamageTypes.INDIRECT_MAGIC)
                        && !event.getSource().is(DamageTypes.THORNS)
                        && !event.getSource().is(DamageTypes.EXPLOSION);

                if (isDirectMelee) {
                    int meleeLvl = skills.getBranchLevel(SkillRegistry.BRANCH_MELEE);
                    if (meleeLvl > 0) {
                        // Progresión lineal estricta: +2% por nivel (+100% a nivel 50, +200% a nivel 100)
                        float meleeMultiplier = 1.0f + (meleeLvl * 0.02f);
                        event.setAmount(event.getAmount() * meleeMultiplier);
                    }
                }

                for (ResourceLocation nodeId : skills.getUnlockedNodes()) {
                    SkillNode node = SkillRegistry.get(nodeId);
                    if (node != null) {
                        node.onLivingHurt(player, event, skills);
                    }
                }
            });
        }

        // ---------------------------------------------------------------------
        // D. SI EL JUGADOR ES LA VÍCTIMA
        // ---------------------------------------------------------------------
        if (target instanceof ServerPlayer victim) {
            if (victim.isBlocking()) {
                PlayerCombatProfiler.recordShieldBlock(victim);
            }

            PlayerStressTracker.onPlayerDamaged(victim, event.getAmount());

            victim.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                skills.addPractice(SkillRegistry.COUNTER_DAMAGE_BLOCKED, 1);

                int defLvl = skills.getBranchLevel(SkillRegistry.BRANCH_DEFENSE);
                if (defLvl > 0) {
                    float factor = SkillProgression.getDefenseDamageFactor(defLvl);
                    event.setAmount(event.getAmount() * factor);
                }

                for (ResourceLocation nodeId : skills.getUnlockedNodes()) {
                    SkillNode node = SkillRegistry.get(nodeId);
                    if (node != null) {
                        node.onLivingHurt(victim, event, skills);
                    }
                }
            });
        }
    }

    // =========================================================================
    // 12. GENERACIÓN DE ENTIDADES: Inicialización Táctica
    // =========================================================================

    // =========================================================================
    // 12. GENERACIÓN DE ENTIDADES: Inicialización Táctica
    // =========================================================================

    @SubscribeEvent
    public static void onMonsterSpawn(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Monster monster) {
            // D2 FIX: No cancelar esbirros del jugador, ni entidades cargadas de disco, ni mobs inicializados
            if (event.loadedFromDisk()
                    || monster.getTags().contains(MinionHelper.TAG_MINION)
                    || monster.getTags().contains(EnemyRpgManager.TAG_INITIALIZED)) {
                EnemyRpgManager.tryInitializeMob(monster);
                return;
            }

            if (!monster.getTags().contains(NemesisHordeManager.TAG_NEMESIS_SQUAD)) {
                AABB checkArea = monster.getBoundingBox().inflate(32.0);
                List<ServerPlayer> nearbyPlayers = event.getLevel().getEntitiesOfClass(ServerPlayer.class, checkArea);
                for (ServerPlayer sp : nearbyPlayers) {
                    if (NemesisHordeManager.isNemesisRaidActiveNear(sp)) {
                        event.setCanceled(true);
                        return;
                    }
                }
            }

            EnemyRpgManager.tryInitializeMob(monster);
        }
    }

}