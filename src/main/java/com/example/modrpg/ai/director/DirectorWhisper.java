package com.example.modrpg.ai.director;

import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/**
 * Pistas transmitidas por el Director a los escuadrones hostiles.
 * En lugar de dar coordenadas directas del jugador, envía waypoints de interés táctico.
 */
public record DirectorWhisper(
        UUID playerUUID,
        Vec3 waypoint,
        WhisperType type,
        int tickCreated
) {
    public enum WhisperType {
        INVESTIGATE_NOISE, // Guiar al escuadrón hacia el eco acústico del pico o hechizo
        SHADOW_STALK,      // Posición a 12 bloques a espaldas del jugador
        CUTOFF_CHOKE       // Bloquear la salida de un túnel o puerta donde el jugador se encerró
    }

    public boolean isExpired(int currentTick) {
        return (currentTick - tickCreated) > 200; // Expira tras 10 segundos
    }
}