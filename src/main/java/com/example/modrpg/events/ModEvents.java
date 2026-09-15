package com.example.modrpg.events;

import com.example.modrpg.ModRpg;
import com.example.modrpg.commands.RpgCommands;
import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillAttributes;
import com.example.modrpg.skills.SkillEconomy;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModRpg.MODID)
public class ModEvents {

    private static final String HYBRID_MARK_TAG = "modrpg_hunter_mark";
    private static final String DOUBLE_ATTACK_RECURSION_TAG = "modrpg_double_slice_hit";
    private static final String HYPERSONIC_ARROW_TAG = "modrpg_hypersonic_arrow";

    @SuppressWarnings({"removal", "deprecation"})
    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            if (!event.getObject().getCapability(PlayerSkillsProvider.PLAYER_SKILLS).isPresent()) {
                event.addCapability(
                        new ResourceLocation(ModRpg.MODID, "player_skills"),
                        new PlayerSkillsProvider()
                );
            }
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
            serverPlayer.sendSystemMessage(
                    Component.literal("§a[ModRpg] §f¡Sistema de habilidades RPG cargado con éxito!")
            );
            SkillAttributes.applyModifiers(serverPlayer);
            SkillEconomy.syncSkills(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                if (event.getSource().getDirectEntity() == player) {
                    skills.addMeleeKill();
                    player.displayClientMessage(
                            Component.literal("§c⚔ Kills Melee: §e" + skills.getMeleeKills()),
                            true
                    );
                } else {
                    skills.addRangedKill();
                    player.displayClientMessage(
                            Component.literal("§b🏹 Kills Distancia: §e" + skills.getRangedKills()),
                            true
                    );
                }
                SkillEconomy.syncSkills(player);
            });
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RpgCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide()) {
            event.player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(PlayerSkills::tickCooldown);
        }
    }

    // =========================================================================
    // RAMA 2: DISPARO DE FLECHAS (VIENTO A FAVOR & HIPERSÓNICA)
    // =========================================================================
    @SubscribeEvent
    public static void onArrowSpawn(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof AbstractArrow arrow) {
            if (arrow.getOwner() instanceof ServerPlayer player) {
                player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {

                    // 1. VIENTO A FAVOR (Nivel 5+): Aumenta velocidad y suelta ráfaga blanca
                    if (skills.hasTailwind()) {
                        ServerLevel level = (ServerLevel) player.level();

                        // Aceleración de trayectoria (+80%)
                        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1.8));

                        // Ráfaga blanca de viento saliendo de la punta de la flecha
                        level.sendParticles(ParticleTypes.CLOUD, arrow.getX(), arrow.getY(), arrow.getZ(), 12, 0.2, 0.2, 0.2, 0.08);
                        level.sendParticles(ParticleTypes.SWEEP_ATTACK, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0, 0, 0, 0);

                        player.displayClientMessage(
                                Component.literal("§b💨 ¡Viento a Favor activado! (+80% Velocidad)"),
                                true
                        );

                        // 2. TIRO HIPERSÓNICO (Nivel 50+): Si dispara agachado (Sneak / Shift)
                        if (skills.hasHypersonicArrow() && player.isShiftKeyDown()) {
                            // Aceleración supersónica (2.5x total)
                            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1.5));
                            arrow.setPierceLevel((byte) 5); // Atraviesa hasta 5 enemigos en fila
                            arrow.setNoGravity(true);       // Trayectoria horizontal recta
                            arrow.addTag(HYPERSONIC_ARROW_TAG);

                            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.9f, 1.8f);
                            level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getEyeY(), player.getZ(), 1, 0, 0, 0, 0);

                            player.displayClientMessage(
                                    Component.literal("§9§l⚡ ¡TIRO HIPERSÓNICO! §f(Perforación V activada)"),
                                    true
                            );
                        }
                    }
                });
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity attacker = event.getSource().getEntity();
        Entity directEntity = event.getSource().getDirectEntity();
        LivingEntity target = event.getEntity();

        if (!(attacker instanceof ServerPlayer player)) return;
        ServerLevel level = (ServerLevel) player.level();

        if (target.getTags().contains(DOUBLE_ATTACK_RECURSION_TAG)) {
            target.removeTag(DOUBLE_ATTACK_RECURSION_TAG);
            return;
        }

        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {

            // 1. IMPACTO DE FLECHAS
            if (directEntity instanceof AbstractArrow arrow) {
                int rangedLvl = skills.getRangedLevel();
                if (rangedLvl > 0) {
                    float bonusMultiplier = 1.0f + (float) Math.pow(rangedLvl / 100.0, 1.5) * 2.5f;
                    event.setAmount(event.getAmount() * bonusMultiplier);
                }

                // Impacto de Tiro Hipersónico
                if (arrow.getTags().contains(HYPERSONIC_ARROW_TAG)) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1.0, target.getZ(), 30, 0.4, 0.4, 0.4, 0.15);
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 2.0f);
                }

                // Marca del cazador (Híbrida)
                if (skills.hasHybridRangedMelee()) {
                    target.addTag(HYBRID_MARK_TAG);
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false));
                    level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.4, 0.4, 0.4, 0.2);
                    player.displayClientMessage(
                            Component.literal("§d🎯 ¡Enemigo Marcado! Remátalo cuerpo a cuerpo para combo."),
                            true
                    );
                }
            }

            // 2. COHETES CON BALLESTA
            else if (directEntity instanceof FireworkRocketEntity) {
                int rangedLvl = skills.getRangedLevel();
                float bonusExplosion = 10.0f + (rangedLvl * 0.4f);
                event.setAmount(event.getAmount() + bonusExplosion);

                level.sendParticles(ParticleTypes.DRAGON_BREATH, target.getX(), target.getY() + 0.8, target.getZ(), 45, 0.6, 0.6, 0.6, 0.1);
                level.sendParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0f, 1.2f);
            }

            // 3. COMBATE CUERPO A CUERPO
            else if (directEntity == player) {

                // A) DETONACIÓN HÍBRIDA
                if (target.getTags().contains(HYBRID_MARK_TAG)) {
                    target.removeTag(HYBRID_MARK_TAG);
                    target.removeEffect(MobEffects.GLOWING);

                    event.setAmount(event.getAmount() * 2.5f);
                    level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0, target.getZ(), 40, 0.5, 0.5, 0.5, 0.15);
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7f, 1.6f);

                    player.displayClientMessage(
                            Component.literal("§d§l⚡ ¡COMBO HÍBRIDO EJECUTADO! (+150% Daño de Vacío)"),
                            true
                    );
                }

                // B) GOLPE DEFINITIVO (TECLA [R])
                if (skills.isUltimateCharged()) {
                    skills.setUltimateCharged(false);
                    skills.setUltimateCooldown(6000);

                    float damageFinal = event.getAmount() * 5.0f;
                    event.setAmount(damageFinal);

                    level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1.0, target.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
                    level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 50, 0.5, 0.5, 0.5, 0.3);
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.4f);

                    player.displayClientMessage(
                            Component.literal("§c§l💥 ¡IMPACTO CRÍTICO (500%)! §fDaño: §4§l" + String.format("%.1f", damageFinal)),
                            true
                    );
                    return;
                }

                // C) DOBLE ATAQUE (NIVEL 4 CaC)
                if (skills.hasDoubleAttack() && player.getAttackStrengthScale(0.5f) >= 0.92f) {
                    float singleHitDamage = event.getAmount() * 0.80f;
                    event.setAmount(singleHitDamage);

                    target.addTag(DOUBLE_ATTACK_RECURSION_TAG);
                    target.invulnerableTime = 0;
                    target.hurt(player.damageSources().playerAttack(player), singleHitDamage);
                    target.invulnerableTime = 10;

                    player.swing(InteractionHand.MAIN_HAND, true);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 0.9, target.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
                    level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.9, target.getZ(), 12, 0.25, 0.25, 0.25, 0.1);
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.4f);

                    player.displayClientMessage(
                            Component.literal("§c§l⚔ ¡DOBLE ATAQUE! §f(2 impactos consecutivos al 80%)"),
                            true
                    );
                }
            }
        });
    }
}