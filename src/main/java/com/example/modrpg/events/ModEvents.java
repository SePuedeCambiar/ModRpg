package com.example.modrpg.events;

import com.example.modrpg.ModRpg;
import com.example.modrpg.ai.ChampionAffix;
import com.example.modrpg.ai.EnemyRpgManager;
import com.example.modrpg.ai.SquadCoordinator;
import com.example.modrpg.ai.TacticalCasterGoal;
import com.example.modrpg.ai.director.AudioFootprintTracker;
import com.example.modrpg.ai.director.PlayerStressTracker;
import com.example.modrpg.ai.director.PlayerVulnerabilityDetector;
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
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ModRpg.MODID)
public class ModEvents {

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
            SkillEconomy.syncSkills(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("§a[ModRpg] §f¡Sistema modular RPG cargado con éxito! Usa §e/rpg stats§f para ver tu progreso."));
            SkillAttributes.applyModifiers(serverPlayer);
            SkillEconomy.syncSkills(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Limpieza de memoria de telemetría y ruidos al desconectarse
        PlayerStressTracker.clearPlayer(event.getEntity().getUUID());
        PlayerVulnerabilityDetector.clearPlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RpgCommands.register(event.getDispatcher());
    }

    // =========================================================================
    // 1. TICK DE SERVIDOR: HABILIDADES, MANÁ, DIRECTOR Y ESCUADRONES F.E.A.R.
    // =========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            event.player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                if (!event.player.level().isClientSide()) {
                    skills.tickServerSide();

                    // Control de duración de Fuerza de Hierro (12s = 240 ticks)
                    if (event.player.getTags().contains(IronStrengthSkill.TAG_IRON_STRENGTH)) {
                        CompoundTag data = event.player.getPersistentData();
                        int timer = data.getInt("modrpg_iron_strength_timer");
                        if (timer <= 0) {
                            data.putInt("modrpg_iron_strength_timer", 240);
                        } else {
                            timer--;
                            if (timer <= 0) {
                                event.player.removeTag(IronStrengthSkill.TAG_IRON_STRENGTH);
                                data.remove("modrpg_iron_strength_timer");
                                if (event.player instanceof ServerPlayer sp) {
                                    sp.displayClientMessage(Component.literal("§7🛡 Tu Fuerza de Hierro se ha disipado."), true);
                                }
                            } else {
                                data.putInt("modrpg_iron_strength_timer", timer);
                            }
                        }
                    }

                    if (event.player instanceof ServerPlayer serverPlayer) {
                        // Acumulador de práctica de Movilidad por distancia a pie
                        double dx = serverPlayer.getX() - serverPlayer.xOld;
                        double dz = serverPlayer.getZ() - serverPlayer.zOld;
                        double distSq = dx * dx + dz * dz;

                        if (distSq > 0.0001 && serverPlayer.onGround()) {
                            CompoundTag data = serverPlayer.getPersistentData();
                            double acc = data.getDouble("modrpg_distance_acc") + Math.sqrt(distSq);
                            if (acc >= 1.0) {
                                int fullBlocks = (int) acc;
                                skills.addPractice(SkillRegistry.COUNTER_DISTANCE_RUN, fullBlocks);
                                data.putDouble("modrpg_distance_acc", acc - fullBlocks);
                            }
                        }

                        // SPRINT 6: Emisión de Huella Acústica por Movimiento cada 10 ticks
                        if (serverPlayer.tickCount % 10 == 0) {
                            if (serverPlayer.isShiftKeyDown()) {
                                AudioFootprintTracker.emitPing(serverPlayer, AudioFootprintTracker.NoiseCategory.SNEAK);
                            } else if (serverPlayer.isSprinting() && serverPlayer.onGround()) {
                                AudioFootprintTracker.emitPing(serverPlayer, AudioFootprintTracker.NoiseCategory.SPRINT);
                            } else if (serverPlayer.getDeltaMovement().horizontalDistanceSqr() > 0.005 && serverPlayer.onGround()) {
                                AudioFootprintTracker.emitPing(serverPlayer, AudioFootprintTracker.NoiseCategory.WALK);
                            }
                        }

                        // SPRINT 6: Reseteo de ventana de vulnerabilidad de minería si está inactivo
                        if (serverPlayer.tickCount % 5 == 0) {
                            CompoundTag data = serverPlayer.getPersistentData();
                            int lastMineTick = data.getInt("modrpg_last_mine_tick");
                            if (serverPlayer.tickCount - lastMineTick > 15) {
                                PlayerVulnerabilityDetector.resetMiningProgress(serverPlayer.getUUID());
                            }
                        }

                        // SPRINT 6: Limpieza de huellas de audio expiradas
                        if (serverPlayer.tickCount % 20 == 0) {
                            AudioFootprintTracker.cleanupExpiredPings(serverPlayer.tickCount);
                        }

                        // SPRINT 5: Evaluación del Medidor de Estrés del Director cada segundo (20 ticks)
                        if (serverPlayer.tickCount % 20 == 0) {
                            PlayerStressTracker.tick(serverPlayer);
                        }
                    }

                    // Feedback si se agota el maná
                    if (skills.consumeTogglesForceDeactivated() && event.player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.displayClientMessage(
                                Component.literal("§c§l⚡ ¡MANÁ AGOTADO! §7Tus posturas activas se han desactivado."),
                                true
                        );
                        serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.2f);
                        SkillEconomy.syncSkills(serverPlayer);
                    }

                    // Sincronización continua de maná cada segundo
                    if (event.player.tickCount % 20 == 0 && event.player instanceof ServerPlayer serverPlayer) {
                        ModMessages.sendToPlayer(
                                new PacketSyncMana(skills.getCurrentMana(), skills.getMaxMana()),
                                serverPlayer
                        );
                    }

                    // SPRINT 2: Tick del cerebro de escuadrones tácticos cada 5 ticks pasando el ServerLevel
                    if (event.player.tickCount % 5 == 0 && event.player.level() instanceof ServerLevel serverLevel) {
                        SquadCoordinator.tickSquads(serverLevel);
                    }
                } else {
                    skills.tickCooldowns();
                }
            });
        }
    }

    // =========================================================================
    // 2. SPRINT 6: REGISTRO DE VIBRACIÓN ACÚSTICA POR MINERÍA CONTINUA
    // =========================================================================
    @SubscribeEvent
    public static void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            BlockPos pos = event.getPos();
            BlockState state = event.getLevel().getBlockState(pos);

            // Bloques con dureza considerable (roca, carbón, hierro, diamantes)
            if (state.getDestroySpeed(event.getLevel(), pos) >= 1.5f) {
                PlayerVulnerabilityDetector.recordMiningProgress(player);
                player.getPersistentData().putInt("modrpg_last_mine_tick", player.tickCount);

                // Emite una onda acústica cada 10 ticks de picado continuo (18 bloques de radio)
                if (player.tickCount % 10 == 0) {
                    AudioFootprintTracker.emitPing(player, AudioFootprintTracker.NoiseCategory.MINING);
                }
            }
        }
    }

    // =========================================================================
    // 3. TICK DE ENTIDADES: ESBIRROS, REACCIONES Y COMANDANTE
    // =========================================================================
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide()) {
            if (entity.getTags().contains(MinionHelper.TAG_MINION)) {
                MinionHelper.tickMinion(entity);
            }

            // Decaimiento del cebado elemental
            ElementalReactionManager.tickPrimers(entity);

            // Pulso de Aura del Comandante de Escuadrón cada segundo
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
        }
    }

    // =========================================================================
    // 4. COMBATE: FUEGO AMIGO + I-FRAMES
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
    // 5. CAÍDA Y DETONACIONES SÍSMICAS
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
    // 6. BAJAS, RUPTURA DE MORAL Y PRÁCTICA
    // =========================================================================
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || victim instanceof Player) return;

        // F.E.A.R.: Notificar muerte al coordinador (ruptura de moral si era Líder Comandante)
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
                } else if (victim.getTags().contains("modrpg_champion")) {
                    practiceReward = 8;
                    tierLabel = " §e[CAMPEÓN +8]";
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
    // 7. PROYECTILES
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
    // 8. CÁLCULO DE DAÑO, ROTURA DE POSTURA Y REACCIONES
    // =========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity attacker = event.getSource().getEntity();
        Entity target = event.getEntity();

        // A. Afijo: Escudo Rúnico (bloqueo frontal de proyectiles)
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

        // B. Afijo: Vampírico (Cura 25% del daño infligido)
        if (attacker instanceof Monster monster && monster.getTags().contains("modrpg_affix_vampiric")) {
            float heal = event.getAmount() * 0.25f;
            monster.heal(heal);
            if (monster.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, monster.getX(), monster.getEyeY(), monster.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
            }
        }

        // C. Afijo: Quemador de Maná
        if (attacker instanceof Monster monster && monster.getTags().contains("modrpg_affix_mana_burn")) {
            if (target instanceof ServerPlayer player) {
                player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                    skills.consumeMana(20.0f);
                    ModMessages.sendToPlayer(new PacketSyncMana(skills.getCurrentMana(), skills.getMaxMana()), player);
                    player.displayClientMessage(Component.literal("§9§l⚡ ¡QUEMADURA DE MANÁ! §c(-20 Maná)"), true);
                });
            }
        }

        // Si el jugador es quien ataca
        if (attacker instanceof ServerPlayer player && target instanceof LivingEntity livingTarget) {
            MinionHelper.redirectMinionsTarget(player, livingTarget, 16.0);

            // D. INTERRUPCIÓN DE TELEGRAFIADO AMARILLO (ROMPE-POSTURA)
            if (livingTarget instanceof Mob mob && mob.getTags().contains(TacticalCasterGoal.TAG_INTERRUPTIBLE)) {
                if (player.getAttackStrengthScale(0.5f) >= 0.85f) {
                    TacticalCasterGoal.interruptCaster(mob, player);
                    player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(s -> {
                        s.addPractice(SkillRegistry.COUNTER_STAGGER_INTERRUPTS, 1);
                    });
                }
            }

            // E. DAÑO CRÍTICO POR POSTURA ROTA (+30% Daño)
            if (livingTarget.getTags().contains(TacticalCasterGoal.TAG_STAGGERED)) {
                event.setAmount(event.getAmount() * 1.30f);
            }

            // F. ROTURA DE HIELO POR FRAGILIDAD ABISAL (+50% Daño Físico)
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

                if (event.getSource().getDirectEntity() instanceof AbstractArrow) {
                    int rangedLvl = skills.getBranchLevel(SkillRegistry.BRANCH_RANGED);
                    if (rangedLvl > 0) {
                        float bonus = 1.0f + (float) Math.pow(rangedLvl / 100.0, 1.5) * 2.5f;
                        event.setAmount(event.getAmount() * bonus);
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

        // Si el jugador es quien recibe el golpe
        if (target instanceof ServerPlayer victim) {
            // SPRINT 5: Pico de estrés por daño recibido (+0.15)
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
    // 9. INICIALIZACIÓN DE IA Y BUILDS DE MOBS
    // =========================================================================
    @SubscribeEvent
    public static void onMonsterSpawn(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Monster monster) {
            EnemyRpgManager.tryInitializeMob(monster);
        }
    }
}