package com.example.modrpg.ai.director;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Registra las ondas sonoras generadas por las acciones del jugador.
 * Los monstruos exploradores consultan este búfer para investigar ruidos lejanos.
 */
public class AudioFootprintTracker {

    public enum NoiseCategory {
        SNEAK(0.0),         // Sigilo total con Shift: Inaudible
        WALK(6.0),          // Caminar normal en superficie
        SPRINT(14.0),       // Correr a toda velocidad
        MINING(18.0),       // Picar roca o minerales duros
        SPELL_CAST(24.0),   // Estallidos de magia modular
        EXPLOSION(35.0);    // Detonaciones sísmicas o TNT

        private final double hearingRadius;

        NoiseCategory(double hearingRadius) {
            this.hearingRadius = hearingRadius;
        }

        public double getHearingRadius() {
            return hearingRadius;
        }
    }

    public record AcousticPing(
            UUID playerUUID,
            Vec3 position,
            NoiseCategory category,
            int tickCreated
    ) {
        public boolean isExpired(int currentTick) {
            return (currentTick - tickCreated) > 60; // El eco se disipa tras 3 segundos (60 ticks)
        }
    }

    private static final Queue<AcousticPing> ACTIVE_PINGS = new ConcurrentLinkedQueue<>();

    /**
     * Emite un pulso sonoro en el mundo. Si el radio es 0 (Shift), se ignora.
     */
    public static void emitPing(ServerPlayer player, NoiseCategory category) {
        if (player == null || category == NoiseCategory.SNEAK || category.getHearingRadius() <= 0.0) {
            return;
        }

        int currentTick = player.tickCount;
        AcousticPing ping = new AcousticPing(
                player.getUUID(),
                player.position(),
                category,
                currentTick
        );

        ACTIVE_PINGS.add(ping);

        // Partícula sutil de vibración sonora en desarrollo/debug
        if (category == NoiseCategory.MINING || category == NoiseCategory.SPELL_CAST) {
            ServerLevel level = player.serverLevel();
            level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    /**
     * Limpia los pulsos acústicos expirados en cada ciclo del Director.
     */
    public static void cleanupExpiredPings(int currentTick) {
        Iterator<AcousticPing> iterator = ACTIVE_PINGS.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isExpired(currentTick)) {
                iterator.remove();
            }
        }
    }

    /**
     * Comprueba si una posición hostil puede escuchar algún ruido reciente en su radio de audición.
     */
    public static AcousticPing findHeardPing(Vec3 listenerPos, double listenerHearingRange) {
        for (AcousticPing ping : ACTIVE_PINGS) {
            double distSq = ping.position().distanceToSqr(listenerPos);
            double effectiveRange = Math.min(ping.category().getHearingRadius(), listenerHearingRange);

            if (distSq <= (effectiveRange * effectiveRange)) {
                return ping;
            }
        }
        return null;
    }
}