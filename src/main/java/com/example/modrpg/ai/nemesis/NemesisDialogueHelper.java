package com.example.modrpg.ai.nemesis;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orquesta los efectos de audio, partículas épicas y mensajes en chat/Action Bar
 * para los eventos dramáticos del Némesis.
 */
public class NemesisDialogueHelper {

    private static final Map<UUID, Long> LAST_INTRO_TICK = new ConcurrentHashMap<>();

    /**
     * SPRINT 1 FIX: Elimina el registro de la entidad de memoria cuando muere o escapa.
     */
    public static void clearNemesisMemory(UUID mobUUID) {
        if (mobUUID != null) {
            LAST_INTRO_TICK.remove(mobUUID);
        }
    }

    /**
     * Emite la presentación cinematográfica del Capitán Némesis al iniciar combate.
     * SPRINT 1 FIX (Bug m-09): Usa level.getGameTime() en lugar de mob.tickCount
     * para evitar que el temporizador se corrompa si el chunk se recarga.
     */
    public static void triggerIntro(ServerLevel level, Mob mob, NemesisCaptain captain, ServerPlayer player) {
        if (mob == null || player == null || level.isClientSide()) return;

        long currentTick = level.getGameTime();
        long lastTick = LAST_INTRO_TICK.getOrDefault(mob.getUUID(), -2400L);

        if (currentTick - lastTick < 1200L) {
            return; // Cooldown de 60s (1200 ticks de servidor) por entidad para no spamear
        }

        LAST_INTRO_TICK.put(mob.getUUID(), currentTick);

        // Failsafe anti-fugas: si el mapa supera 50 capitanes registrados, purgar entradas antiguas (> 30 min)
        if (LAST_INTRO_TICK.size() > 50) {
            LAST_INTRO_TICK.entrySet().removeIf(entry -> (currentTick - entry.getValue()) > 36000L);
        }

        // 1. Audio épico: Cuerno de asalto grave y campana resonante
        level.playSound(null, mob.getX(), mob.getY() + 1.0, mob.getZ(),
                SoundEvents.RAID_HORN.get(), SoundSource.HOSTILE, 1.4f, 0.82f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 1.2f, 0.6f);

        // 2. Partículas oscuras alrededor del némesis
        level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 1.0, mob.getZ(), 30, 0.5, 0.6, 0.5, 0.1);
        level.sendParticles(ParticleTypes.FLASH, mob.getX(), mob.getEyeY(), mob.getZ(), 1, 0, 0, 0, 0);

        // 3. Título en pantalla y anuncio dramático en chat
        String introDialogue = NemesisPersonalityEngine.buildIntroDialogue(captain, mob.getRandom());

        player.sendSystemMessage(Component.literal("§4§l=================================================="));
        player.sendSystemMessage(Component.literal("§c§l⚔ ¡UN CAPITÁN NÉMESIS TE HA LOCALIZADO! ⚔"));
        player.sendSystemMessage(Component.literal("§6§l" + captain.getName() + " " + captain.getTitle()));
        player.sendSystemMessage(Component.literal("§e" + introDialogue));
        player.sendSystemMessage(Component.literal("§4§l=================================================="));

        player.displayClientMessage(
                Component.literal("§4§l⚔ NÉMESIS: §6§l" + captain.getName() + " " + captain.getTitle()),
                true
        );
    }

    /**
     * Burla cuando el némesis conecta un golpe demoledor.
     */
    public static void triggerTaunt(ServerLevel level, Mob mob, NemesisCaptain captain, ServerPlayer player) {
        if (mob == null || captain == null || player == null) return;
        String taunt = NemesisPersonalityEngine.buildTauntDialogue(captain, mob.getRandom());
        player.displayClientMessage(Component.literal("§c" + captain.getName() + ": §e" + taunt), true);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.VINDICATOR_CELEBRATE, SoundSource.HOSTILE, 1.0f, 0.9f);
    }

    /**
     * Aviso dramático cuando el némesis huye con vida en una bomba de humo.
     */
    public static void triggerEscape(ServerLevel level, Mob mob, NemesisCaptain captain, ServerPlayer player) {
        if (captain == null || player == null) return;
        String escape = NemesisPersonalityEngine.buildEscapeDialogue(captain, mob.getRandom());
        player.sendSystemMessage(Component.literal("§5§l✦ NÉMESIS EN RETIRADA: §6" + captain.getName() + ": §d" + escape));
        player.displayClientMessage(Component.literal("§5§l💨 ¡EL NÉMESIS HA ESCAPADO CON VIDA!"), true);

        // Limpiar memoria temporal de la entidad que acaba de despawnear
        if (mob != null) {
            clearNemesisMemory(mob.getUUID());
        }
    }

    /**
     * Fanfarria de victoria cuando el jugador logra derrotarlo definitivamente.
     */
    public static void triggerDeath(ServerLevel level, Mob mob, NemesisCaptain captain, ServerPlayer player) {
        if (captain == null || player == null) return;
        String death = NemesisPersonalityEngine.buildDeathDialogue(captain, mob.getRandom());

        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1.0f);

        player.sendSystemMessage(Component.literal("§6§l=================================================="));
        player.sendSystemMessage(Component.literal("§a§l✦ ¡NÉMESIS ELIMINADO PARA SIEMPRE! ✦"));
        player.sendSystemMessage(Component.literal("§fHas derrotado a §e§l" + captain.getName() + " " + captain.getTitle()));
        player.sendSystemMessage(Component.literal("§7" + death));
        player.sendSystemMessage(Component.literal("§6§l=================================================="));

        // Limpiar memoria de la entidad al confirmarse su muerte
        if (mob != null) {
            clearNemesisMemory(mob.getUUID());
        }
    }
}