package com.example.modrpg.events;

import com.example.modrpg.ModRpg;
import com.example.modrpg.commands.RpgCommands;
import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillAttributes;
import com.example.modrpg.skills.SkillEconomy;
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

    // Guardia de recursión: evita que golpes secundarios (doble ataque, magias)
    // disparen un bucle infinito de eventos que congele el servidor
    private static final ThreadLocal<Boolean> IS_PROCESSING_HURT = ThreadLocal.withInitial(() -> false);

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
            SkillAttributes.applyModifiers(serverPlayer);
            updateStepHeight(serverPlayer);
            SkillEconomy.syncSkills(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("§a[ModRpg] §f¡Sistema modular RPG cargado con éxito!"));
            SkillAttributes.applyModifiers(serverPlayer);
            updateStepHeight(serverPlayer);
            SkillEconomy.syncSkills(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RpgCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide() && event.player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                // 1. OPTIMIZACIÓN: Solo iterar si hay enfriamientos activos
                if (!skills.getAllCooldowns().isEmpty()) {
                    skills.tickCooldowns();
                }

                // 2. OPTIMIZACIÓN: Solo escribir la altura si difiere del valor actual
                float targetStep = skills.isNodeUnlocked(SkillRegistry.NODE_LIGHT_STEP) ? 1.25f : 0.6f;
                if (Math.abs(serverPlayer.maxUpStep() - targetStep) > 0.01f) {
                    serverPlayer.setMaxUpStep(targetStep);
                }
            });
        }
    }

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
        // OPTIMIZACIÓN CRÍTICA: Si ya estamos dentro de un cálculo de daño del mod,
        // no procesar recursivamente para evitar tirones de TPS y bucles infinitos
        if (IS_PROCESSING_HURT.get()) return;

        Entity attacker = event.getSource().getEntity();
        Entity target = event.getEntity();

        try {
            IS_PROCESSING_HURT.set(true);

            // 1. Daño saliente del jugador (Habilidades ofensivas)
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

            // 2. Daño entrante al jugador (Habilidades defensivas / mitigación)
            if (target instanceof ServerPlayer victim) {
                victim.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                    for (ResourceLocation nodeId : skills.getUnlockedNodes()) {
                        SkillNode node = SkillRegistry.get(nodeId);
                        if (node != null) {
                            node.onLivingHurt(victim, event, skills);
                        }
                    }
                });
            }
        } finally {
            IS_PROCESSING_HURT.set(false);
        }
    }

    private static void updateStepHeight(ServerPlayer player) {
        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            float targetStep = skills.isNodeUnlocked(SkillRegistry.NODE_LIGHT_STEP) ? 1.25f : 0.6f;
            player.setMaxUpStep(targetStep);
        });
    }
}