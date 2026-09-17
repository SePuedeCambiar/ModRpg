package com.example.modrpg.events;

import com.example.modrpg.ModRpg;
import com.example.modrpg.commands.RpgCommands;
import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillAttributes;
import com.example.modrpg.skills.SkillEconomy;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
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
            serverPlayer.sendSystemMessage(Component.literal("§a[ModRpg] §f¡Sistema modular RPG cargado con éxito!"));
            SkillAttributes.applyModifiers(serverPlayer);
            SkillEconomy.syncSkills(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RpgCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide()) {
            event.player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(PlayerSkills::tickCooldowns);
        }
    }

    // =========================================================================
    // REGISTRO DE BAJAS / PRÁCTICA DINÁMICA
    // =========================================================================
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                if (event.getSource().getDirectEntity() == player) {
                    skills.addPractice(SkillRegistry.COUNTER_MELEE_KILLS, 1);
                    player.displayClientMessage(
                            Component.literal("§c⚔ Bajas CaC: §e" + skills.getPractice(SkillRegistry.COUNTER_MELEE_KILLS)), true
                    );
                } else {
                    skills.addPractice(SkillRegistry.COUNTER_RANGED_KILLS, 1);
                    player.displayClientMessage(
                            Component.literal("§b🏹 Bajas Distancia: §e" + skills.getPractice(SkillRegistry.COUNTER_RANGED_KILLS)), true
                    );
                }
                SkillEconomy.checkMilestones(player, skills);
                SkillEconomy.syncSkills(player);
            });
        }
    }

    // =========================================================================
    // ENRUTADOR DE DISPAROS DE PROYECTIL
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
    // ENRUTADOR DE DAÑO Y COMBATE
    // =========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity attacker = event.getSource().getEntity();
        Entity directEntity = event.getSource().getDirectEntity();

        if (!(attacker instanceof ServerPlayer player)) return;

        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            // 1. Escalado base de daño con arcos
            if (directEntity instanceof AbstractArrow) {
                int rangedLvl = skills.getBranchLevel(SkillRegistry.BRANCH_RANGED);
                if (rangedLvl > 0) {
                    float bonusMultiplier = 1.0f + (float) Math.pow(rangedLvl / 100.0, 1.5) * 2.5f;
                    event.setAmount(event.getAmount() * bonusMultiplier);
                }
            }

            // 2. Cohetes con ballesta
            else if (directEntity instanceof FireworkRocketEntity) {
                int rangedLvl = skills.getBranchLevel(SkillRegistry.BRANCH_RANGED);
                float bonusExplosion = 10.0f + (rangedLvl * 0.4f);
                event.setAmount(event.getAmount() + bonusExplosion);

                ServerLevel level = (ServerLevel) player.level();
                level.sendParticles(ParticleTypes.DRAGON_BREATH, event.getEntity().getX(), event.getEntity().getY() + 0.8, event.getEntity().getZ(), 45, 0.6, 0.6, 0.6, 0.1);
                level.playSound(null, event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0f, 1.2f);
            }

            // 3. Ejecución de habilidades modulares registradas
            for (ResourceLocation nodeId : skills.getUnlockedNodes()) {
                SkillNode node = SkillRegistry.get(nodeId);
                if (node != null) {
                    node.onLivingHurt(player, event, skills);
                }
            }
        });
    }
}