package com.example.modrpg.events;

import com.example.modrpg.ModRpg;
import com.example.modrpg.commands.RpgCommands;
import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillAttributes;
import com.example.modrpg.skills.SkillEconomy;
import com.example.modrpg.skills.SkillProgression;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import com.example.modrpg.skills.nodes.mobility.AirJumpSkill;
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
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
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

    // 1. Tick de Cooldowns en Servidor y Cliente (Solución al Bug 1)
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            event.player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                if (!skills.getAllCooldowns().isEmpty()) {
                    skills.tickCooldowns();
                }
            });
        }
    }

    // 2. Anulación del Daño de Caída del Salto de Viento (Solución al Bug 2)
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.getTags().contains(AirJumpSkill.AIR_JUMP_SAFE_TAG)) {
                player.removeTag(AirJumpSkill.AIR_JUMP_SAFE_TAG);
                event.setCanceled(true);

                ServerLevel level = (ServerLevel) player.level();
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 10, 0.3, 0.05, 0.3, 0.05);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_FALL, SoundSource.PLAYERS, 1.0f, 1.4f);
            }
        }
    }

    // 3. Registro de Bajas y Práctica
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                if (event.getSource().getDirectEntity() == player) {
                    skills.addPractice(SkillRegistry.COUNTER_MELEE_KILLS, 1);
                } else {
                    skills.addPractice(SkillRegistry.COUNTER_RANGED_KILLS, 1);
                }
                SkillEconomy.checkMilestones(player, skills);
                SkillEconomy.syncSkills(player);
            });
        }
    }

    // 4. Modificación de Proyectiles al Disparar
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

    // 5. Cálculo y Mitigación de Daño
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity attacker = event.getSource().getEntity();
        Entity target = event.getEntity();

        // Si el atacante es el jugador (daño saliente)
        if (attacker instanceof ServerPlayer player) {
            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
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

        // Si la víctima es el jugador (mitigación pasiva de defensa)
        if (target instanceof ServerPlayer victim) {
            victim.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                int defLvl = skills.getBranchLevel(SkillRegistry.BRANCH_DEFENSE);
                if (defLvl > 0) {
                    float factor = SkillProgression.getDefenseDamageFactor(defLvl);
                    event.setAmount(event.getAmount() * factor);
                    skills.addPractice(SkillRegistry.COUNTER_DAMAGE_BLOCKED, 1);
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
}