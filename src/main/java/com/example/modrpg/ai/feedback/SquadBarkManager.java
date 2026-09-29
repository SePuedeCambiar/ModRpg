package com.example.modrpg.ai.feedback;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Motor de Radio-Táctica inspirado en F.E.A.R.
 * Transmite las intenciones del escuadrón al jugador mediante audio direccional,
 * avisos en Action Bar y modulación de tono acústico.
 */
public class SquadBarkManager {

    public enum BarkType {
        FLANKING(
                "§6[Escuadrón] §e¡Rodeando por el flanco!",
                1.75f, // Pitch alto: silbato táctico
                80     // 4 segundos de cooldown para evitar spam
        ),
        SUPPRESSION_CALL(
                "§c[Vanguardia] §f¡Fijen al blanco! ¡No lo dejen asomar!",
                1.10f,
                100
        ),
        PEEL_REQUEST(
                "§d[Hechicero] §c¡Me tienen acorralado! ¡A mí!",
                1.55f, // Alarido agudo de socorro
                60
        ),
        VANGUARD_INTERCEPT(
                "§6[Vanguardia] §4¡Atrás, insecto! ¡Enfócame a mí!",
                0.80f, // Rugido grave intimidante
                70
        ),
        MORALE_BREAK(
                "§4§l¡LÍDER CAÍDO! §7¡El escuadrón entra en pánico!",
                0.60f, // Tono desafinado de derrota
                120
        ),
        SEARCHING(
                "§7[Escuadrón] §8¿A dónde se fue? ¡Revisen las esquinas!",
                0.95f,
                90
        );

        private final String message;
        private final float soundPitch;
        private final int cooldownTicks;

        BarkType(String message, float soundPitch, int cooldownTicks) {
            this.message = message;
            this.soundPitch = soundPitch;
            this.cooldownTicks = cooldownTicks;
        }

        public String getMessage() { return message; }
        public float getSoundPitch() { return soundPitch; }
        public int getCooldownTicks() { return cooldownTicks; }
    }

    // Cooldown por entidad y tipo de bark: Map<MobUUID, Map<BarkType, TickStamp>>
    private static final Map<UUID, Map<BarkType, Integer>> COOLDOWNS = new ConcurrentHashMap<>();

    /**
     * Emite una señal táctica audible y visible para todos los jugadores dentro de un radio de 18 bloques.
     */
    public static void triggerBark(Mob emitter, BarkType type, ServerLevel level) {
        if (emitter == null || level.isClientSide() || !emitter.isAlive()) return;

        int currentTicks = emitter.tickCount;
        UUID mobId = emitter.getUUID();

        COOLDOWNS.putIfAbsent(mobId, new ConcurrentHashMap<>());
        Map<BarkType, Integer> mobCooldowns = COOLDOWNS.get(mobId);

        int lastTrigger = mobCooldowns.getOrDefault(type, -type.getCooldownTicks());
        if (currentTicks - lastTrigger < type.getCooldownTicks()) {
            return; // Bloqueado por cooldown para evitar spam auditivo/textual
        }

        mobCooldowns.put(type, currentTicks);

        // 1. Proyectar sonido en el entorno según el tipo de acción
        playBarkSound(emitter, type, level);

        // 2. Transmitir mensaje en Action Bar a los jugadores en rango táctico (18 bloques)
        AABB audienceZone = emitter.getBoundingBox().inflate(18.0);
        var nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class, audienceZone);

        for (ServerPlayer player : nearbyPlayers) {
            player.displayClientMessage(Component.literal(type.getMessage()), true);
        }
    }

    private static void playBarkSound(Mob emitter, BarkType type, ServerLevel level) {
        double x = emitter.getX();
        double y = emitter.getY() + 1.2;
        double z = emitter.getZ();

        switch (type) {
            case FLANKING -> {
                // Silbato corto / chasquido táctico
                level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_SNARE.get(), SoundSource.HOSTILE, 1.2f, type.getSoundPitch());
                level.playSound(null, x, y, z, SoundEvents.BAT_TAKEOFF, SoundSource.HOSTILE, 0.8f, 1.4f);
            }
            case SUPPRESSION_CALL -> {
                level.playSound(null, x, y, z, SoundEvents.RAID_HORN.get(), SoundSource.HOSTILE, 0.9f, type.getSoundPitch());
            }
            case PEEL_REQUEST -> {
                level.playSound(null, x, y, z, SoundEvents.GHAST_HURT, SoundSource.HOSTILE, 1.0f, type.getSoundPitch());
            }
            case VANGUARD_INTERCEPT -> {
                level.playSound(null, x, y, z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.2f, type.getSoundPitch());
                level.playSound(null, x, y, z, SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0f, 1.2f);
            }
            case MORALE_BREAK -> {
                level.playSound(null, x, y, z, SoundEvents.RAID_HORN.get(), SoundSource.HOSTILE, 1.4f, type.getSoundPitch());
                level.playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0f, 0.7f);
            }
            case SEARCHING -> {
                level.playSound(null, x, y, z, SoundEvents.VILLAGER_NO, SoundSource.HOSTILE, 0.8f, type.getSoundPitch());
            }
        }
    }

    public static void clearMobMemory(UUID mobId) {
        COOLDOWNS.remove(mobId);
    }
}