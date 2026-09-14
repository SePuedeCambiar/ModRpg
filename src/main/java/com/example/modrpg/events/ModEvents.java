package com.example.modrpg.events;

import com.example.modrpg.ModRpg;
import com.example.modrpg.commands.RpgCommands;
import com.example.modrpg.skills.PlayerSkillsProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModRpg.MODID)
public class ModEvents {

    // 1. Pegar la ficha de habilidades al jugador cuando se crea en el mundo
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

    // 2. Si el jugador muere o cambia de dimensión, transferir sus datos al nuevo cuerpo
    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();

        event.getOriginal().getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(oldSkills -> {
            event.getEntity().getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(newSkills -> {
                newSkills.copyFrom(oldSkills);
            });
        });

        event.getOriginal().invalidateCaps();
    }

    // 3. Avisar en el chat cuando el jugador entre al mundo
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        player.sendSystemMessage(
                Component.literal("§a[ModRpg] §f¡Sistema de habilidades RPG cargado con éxito!")
        );
    }

    // 4. DETECTAR CUANDO EL JUGADOR MATA UN MOB (Práctica)
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            if (!player.level().isClientSide()) {
                player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                    // Si el atacante directo es el mismo jugador -> Cuerpo a cuerpo
                    if (event.getSource().getDirectEntity() == player) {
                        skills.addMeleeKill();
                        player.displayClientMessage(
                                Component.literal("§c⚔ Kills Melee: §e" + skills.getMeleeKills()),
                                true
                        );
                    }
                    // Si el atacante directo fue una flecha/proyectil -> A distancia
                    else {
                        skills.addRangedKill();
                        player.displayClientMessage(
                                Component.literal("§b🏹 Kills Distancia: §e" + skills.getRangedKills()),
                                true
                        );
                    }
                });
            }
        }
    }

    // 5. REGISTRAR COMANDOS (/rpg stats, /rpg addlevel)
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RpgCommands.register(event.getDispatcher());
    }
}