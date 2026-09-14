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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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

    // 1. Pegar la ficha de habilidades al jugador cuando se crea
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

    // 2. Transferir datos al morir o cambiar dimensión + Sincronizar al cliente
    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();

        event.getOriginal().getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(oldSkills -> {
            event.getEntity().getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(newSkills -> {
                newSkills.copyFrom(oldSkills);
            });
        });

        event.getOriginal().invalidateCaps();

        // Sincronizamos con el cliente tras revivir o cruzar portales
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SkillEconomy.syncSkills(serverPlayer);
        }
    }

    // 3. Avisar, aplicar atributos y sincronizar datos al entrar
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(
                    Component.literal("§a[ModRpg] §f¡Sistema de habilidades RPG cargado con éxito!")
            );
            SkillAttributes.applyModifiers(serverPlayer);
            // Sincronizar datos con la GUI del cliente
            SkillEconomy.syncSkills(serverPlayer);
        }
    }

    // 4. Contador de bajas de práctica + Sincronización en tiempo real
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // Al comprobar ServerPlayer nos aseguramos de que corra solo en el servidor
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
                // Mantiene el contador de bajas actualizado al instante en la pantalla/GUI
                SkillEconomy.syncSkills(player);
            });
        }
    }

    // 5. REGISTRAR COMANDOS (/rpg stats, /rpg upgrade, /rpg addlevel)
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RpgCommands.register(event.getDispatcher());
    }

    // 6. REDUCIR ENFRIAMIENTOS CADA TICK (20 veces por segundo)
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide()) {
            event.player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(PlayerSkills::tickCooldown);
        }
    }

    // 7. EJECUTAR EL GOLPE DEFINITIVO AL ATACAR (+500% DAÑO)
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            if (event.getSource().getDirectEntity() == player) {
                player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                    if (skills.isUltimateCharged()) {
                        // 1. Desarmar carga
                        skills.setUltimateCharged(false);

                        // 2. Establecer enfriamiento de 20 segundos (400 ticks)
                        skills.setUltimateCooldown(400);

                        // 3. Multiplicar daño por 5 (+500%)
                        float damageOriginal = event.getAmount();
                        float damageFinal = damageOriginal * 5.0f;
                        event.setAmount(damageFinal);

                        Entity target = event.getEntity();
                        ServerLevel level = (ServerLevel) player.level();

                        // 4. Efectos visuales de impacto crítico y explosión
                        level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1.0, target.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
                        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 50, 0.5, 0.5, 0.5, 0.3);

                        // 5. Sonido contundente
                        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.4f);

                        // 6. Mensaje en pantalla
                        player.displayClientMessage(
                                Component.literal("§c§l💥 ¡IMPACTO CRÍTICO (500%)! §fDaño: §4§l" + String.format("%.1f", damageFinal)),
                                true
                        );
                    }
                });
            }
        }
    }
}