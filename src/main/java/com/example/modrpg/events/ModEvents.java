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
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModRpg.MODID)
public class ModEvents {

    // Etiqueta para marcar enemigos alcanzados por proyectil híbrido
    private static final String HYBRID_MARK_TAG = "modrpg_hunter_mark";

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

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity attacker = event.getSource().getEntity();
        Entity directEntity = event.getSource().getDirectEntity();
        LivingEntity target = event.getEntity();

        if (!(attacker instanceof ServerPlayer player)) return;
        ServerLevel level = (ServerLevel) player.level();

        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {

            // =========================================================================
            // 1. COMBATE A DISTANCIA: FLECHAS
            // =========================================================================
            if (directEntity instanceof AbstractArrow) {
                int rangedLvl = skills.getRangedLevel();
                if (rangedLvl > 0) {
                    // Escalado de daño a distancia: hasta +250% de daño a nivel 100
                    float bonusMultiplier = 1.0f + (float) Math.pow(rangedLvl / 100.0, 1.5) * 2.5f;
                    event.setAmount(event.getAmount() * bonusMultiplier);
                }

                // SI TIENE LA RAMA HÍBRIDA: Marca al enemigo con brillo para el combo
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

            // =========================================================================
            // 2. BALÍSTICA ESPECIAL: COHETES CON BALLESTA
            // =========================================================================
            else if (directEntity instanceof FireworkRocketEntity) {
                int rangedLvl = skills.getRangedLevel();
                // Cohete devastador: daño masivo + onda expansiva
                float bonusExplosion = 10.0f + (rangedLvl * 0.4f);
                event.setAmount(event.getAmount() + bonusExplosion);

                // Efectos visuales de dragón y sonido de trueno
                level.sendParticles(ParticleTypes.DRAGON_BREATH, target.getX(), target.getY() + 0.8, target.getZ(), 45, 0.6, 0.6, 0.6, 0.1);
                level.sendParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0f, 1.2f);
            }

            // =========================================================================
            // 3. COMBATE CUERPO A CUERPO: GOLPE DEFINITIVO & REMATE HÍBRIDO
            // =========================================================================
            else if (directEntity == player) {

                // A) DETONACIÓN HÍBRIDA (Si el objetivo tenía la marca del flechazo)
                if (target.getTags().contains(HYBRID_MARK_TAG)) {
                    target.removeTag(HYBRID_MARK_TAG);
                    target.removeEffect(MobEffects.GLOWING);

                    // +150% de daño adicional por combo
                    event.setAmount(event.getAmount() * 2.5f);

                    level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0, target.getZ(), 40, 0.5, 0.5, 0.5, 0.15);
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7f, 1.6f);

                    player.displayClientMessage(
                            Component.literal("§d§l⚡ ¡COMBO HÍBRIDO EJECUTADO! (+150% Daño de Vacío)"),
                            true
                    );
                }

                // B) GOLPE DEFINITIVO (+500% DAÑO CON TECLA [R])
                if (skills.isUltimateCharged()) {
                    skills.setUltimateCharged(false);
                    // Cooldown de 5 minutos = 6000 ticks
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
                }
            }
        });
    }
}