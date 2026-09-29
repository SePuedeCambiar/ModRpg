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
     * Emite la presentación cinematográfica del Capitán Némesis al iniciar combate.
     */
    public static void triggerIntro(ServerLevel level, Mob mob, NemesisCaptain captain, ServerPlayer player) {
        if (mob == null || player == null) return;

        long currentTick = mob.tickCount;
        long lastTick = LAST_INTRO_TICK.getOrDefault(mob.getUUID(), -2400L);
        if (currentTick - lastTick < 1200L) {
            return; // Cooldown de 60s por entidad para no repetir la presentación
        }
        LAST_INTRO_TICK.put(mob.getUUID(), currentTick);

        // 1. Audio épico: Cuerno de asalto grave y campana resonante
        level.playSound(null, mob.getX(), mob.getY() + 1.0, mob.getZ(),
                SoundEvents.RAID_HORN.get(), SoundSource.HOSTILE, 1.4f, 0.82f);
        // CORREGIDO: BELL_RESONATE es un SoundEvent directo en 1.20.1 (sin .get())
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 1.2f, 0.6f);

        // 2. Partículas oscuras alrededor del némesis
        level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 1.0, mob.getZ(), 30, 0.5, 0.6, 0.5, 0.1);
        level.sendParticles(ParticleTypes.FLASH, mob.getX(), mob.getEyeY(), mob.getZ(), 1, 0, 0, 0, 0);

        // 3. Título en pantalla para el jugador
        String introDialogue = NemesisPersonalityEngine.buildIntroDialogue(captain);

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
        String taunt = NemesisPersonalityEngine.buildTauntDialogue(captain);
        player.displayClientMessage(Component.literal("§c" + captain.getName() + ": §e" + taunt), true);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.VINDICATOR_CELEBRATE, SoundSource.HOSTILE, 1.0f, 0.9f);
    }

    /**
     * Aviso dramático cuando el némesis huye con vida en una bomba de humo.
     */
    public static void triggerEscape(ServerLevel level, Mob mob, NemesisCaptain captain, ServerPlayer player) {
        String escape = NemesisPersonalityEngine.buildEscapeDialogue(captain);
        player.sendSystemMessage(Component.literal("§5§l✦ NÉMESIS EN RETIRADA: §6" + captain.getName() + ": §d" + escape));
        player.displayClientMessage(Component.literal("§5§l💨 ¡EL NÉMESIS HA ESCAPADO CON VIDA!"), true);
    }

    /**
     * Fanfarria de victoria cuando el jugador logra derrotarlo definitivamente.
     */
    public static void triggerDeath(ServerLevel level, Mob mob, NemesisCaptain captain, ServerPlayer player) {
        String death = NemesisPersonalityEngine.buildDeathDialogue(captain);

        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1.0f);

        player.sendSystemMessage(Component.literal("§6§l=================================================="));
        player.sendSystemMessage(Component.literal("§a§l✦ ¡NÉMESIS ELIMINADO PARA SIEMPRE! ✦"));
        player.sendSystemMessage(Component.literal("§fHas derrotado a §e§l" + captain.getName() + " " + captain.getTitle()));
        player.sendSystemMessage(Component.literal("§7" + death));
        player.sendSystemMessage(Component.literal("§6§l=================================================="));
    }
}