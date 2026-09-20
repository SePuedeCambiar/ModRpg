package com.example.modrpg.events;

import com.example.modrpg.ModRpg;
import com.example.modrpg.commands.RpgCommands;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillAttributes;
import com.example.modrpg.skills.SkillEconomy;
import com.example.modrpg.skills.SkillProgression;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
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
            // Reaplicar atributos y sincronizar tras reaparecer/morir
            SkillAttributes.applyModifiers(serverPlayer);
            SkillEconomy.syncSkills(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("§a[ModRpg] §f¡Sistema RPG cargado con éxito!"));
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
        if (event.phase == TickEvent.Phase.END) {
            event.player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                // 1. Descontar enfriamientos en CLIENTE Y SERVIDOR para que el HUD baje segundo a segundo
                if (!skills.getAllCooldowns().isEmpty()) {
                    skills.tickCooldowns();
                }

                // 2. Lógica exclusiva de persistencia en el SERVIDOR
                if (!event.player.level().isClientSide() && event.player instanceof ServerPlayer serverPlayer) {
                    // Práctica de Movilidad: acumular metros al esprintar (1 punto por cada segundo esprintando)
                    if (serverPlayer.isSprinting() && serverPlayer.tickCount % 20 == 0) {
                        skills.addPractice(SkillRegistry.COUNTER_DISTANCE_RUN, 1);
                        SkillEconomy.checkMilestones(serverPlayer, skills);
                        SkillEconomy.syncSkills(serverPlayer);
                    }
                }
            });
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                // Si el golpe mortal fue directo con arma cuerpo a cuerpo
                if (event.getSource().getDirectEntity() == player) {
                    skills.addPractice(SkillRegistry.COUNTER_MELEE_KILLS, 1);
                }
                // Si fue con proyectil (flecha, ballesta, etc.)
                else if (event.getSource().getDirectEntity() instanceof AbstractArrow) {
                    skills.addPractice(SkillRegistry.COUNTER_RANGED_KILLS, 1);
                }

                SkillEconomy.checkMilestones(player, skills);
                SkillEconomy.syncSkills(player);
            });
        }
    }

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

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity attacker = event.getSource().getEntity();
        Entity target = event.getEntity();

        // =========================================================================
        // 1. EL JUGADOR ES LA VÍCTIMA (Defensa y mitigación)
        // =========================================================================
        if (target instanceof ServerPlayer victim) {
            victim.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                int defLvl = skills.getBranchLevel(SkillRegistry.BRANCH_DEFENSE);

                // Mitigación pasiva según la curva de defensa
                if (defLvl > 0) {
                    float factor = SkillProgression.getDefenseDamageFactor(defLvl);
                    event.setAmount(event.getAmount() * factor);
                    skills.addPractice(SkillRegistry.COUNTER_DAMAGE_BLOCKED, 1);
                }

                // Práctica extra al bloquear con escudo
                if (victim.isBlocking()) {
                    skills.addPractice(SkillRegistry.COUNTER_DAMAGE_BLOCKED, 2);
                }

                // Habilidades pasivas o reactivas de defensa
                for (ResourceLocation nodeId : skills.getUnlockedNodes()) {
                    SkillNode node = SkillRegistry.get(nodeId);
                    if (node != null) {
                        node.onLivingHurt(victim, event, skills);
                    }
                }
            });
        }

        // =========================================================================
        // 2. EL JUGADOR ES EL ATACANTE (Daño saliente y habilidades ofensivas)
        // =========================================================================
        if (attacker instanceof ServerPlayer player) {
            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                // Bono de daño a distancia por nivel de Arquería
                if (event.getSource().getDirectEntity() instanceof AbstractArrow) {
                    int rangedLvl = skills.getBranchLevel(SkillRegistry.BRANCH_RANGED);
                    if (rangedLvl > 0) {
                        float bonus = 1.0f + (float) Math.pow(rangedLvl / 100.0, 1.5) * 2.5f;
                        event.setAmount(event.getAmount() * bonus);
                    }
                }

                // Notificar a los nodos ofensivos desbloqueados
                for (ResourceLocation nodeId : skills.getUnlockedNodes()) {
                    SkillNode node = SkillRegistry.get(nodeId);
                    if (node != null) {
                        node.onLivingHurt(player, event, skills);
                    }
                }
            });
        }
    }
}