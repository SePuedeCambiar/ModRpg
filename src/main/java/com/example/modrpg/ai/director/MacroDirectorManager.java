package com.example.modrpg.ai.director;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor Maestro de Ritmo y Tensión (Inspirado en Alien: Isolation).
 * Itera una vez por segundo sobre los jugadores para orquestar el flujo de combate.
 */
public class MacroDirectorManager {

    public static class PacingData {
        private DirectorState state = DirectorState.BUILD_UP;
        private int stateTimer = 0; // Temporizador para el período de respiro o emboscada
        private DirectorWhisper activeWhisper = null;
        private int climaxCombatTimer = 0;

        public DirectorState getState() { return state; }
        public int getStateTimer() { return stateTimer; }
        public DirectorWhisper getActiveWhisper() { return activeWhisper; }

        public void setState(DirectorState newState, int durationTicks) {
            this.state = newState;
            this.stateTimer = durationTicks;
        }

        public void setWhisper(DirectorWhisper whisper) {
            this.activeWhisper = whisper;
        }
    }

    private static final Map<UUID, PacingData> PLAYER_PACING_MAP = new ConcurrentHashMap<>();

    public static PacingData getPacingData(UUID playerUUID) {
        return PLAYER_PACING_MAP.computeIfAbsent(playerUUID, k -> new PacingData());
    }

    public static boolean isPlayerInReprieve(UUID playerUUID) {
        PacingData data = PLAYER_PACING_MAP.get(playerUUID);
        return data != null && data.getState() == DirectorState.REPRIEVE;
    }

    public static DirectorWhisper getWhisperForPlayer(UUID playerUUID) {
        PacingData data = PLAYER_PACING_MAP.get(playerUUID);
        return (data != null) ? data.getActiveWhisper() : null;
    }

    /**
     * Fuerza la entrada en modo Respiro tras un combate intenso o la muerte de un líder.
     */
    public static void forceTriggerReprieve(UUID playerUUID, int durationTicks) {
        PacingData data = getPacingData(playerUUID);
        data.setState(DirectorState.REPRIEVE, durationTicks);
        data.setWhisper(null);
    }

    /**
     * Ciclo principal del Macro-Director ejecutado cada segundo (20 ticks).
     */
    public static void tick(ServerPlayer player) {
        if (player == null || !player.isAlive()) return;

        UUID uuid = player.getUUID();
        PacingData pacing = getPacingData(uuid);
        float stress = PlayerStressTracker.getStress(player);
        var vulnerabilities = PlayerVulnerabilityDetector.getActiveVulnerabilities(player);

        int currentTick = player.tickCount;

        // Limpieza de susurro expirado
        if (pacing.activeWhisper != null && pacing.activeWhisper.isExpired(currentTick)) {
            pacing.setWhisper(null);
        }

        // =========================================================================
        // MÁQUINA DE ESTADOS DEL RITMO
        // =========================================================================
        switch (pacing.getState()) {

            // 1. FASE DE RESPIRO: El Director retiene las hordas
            case REPRIEVE -> {
                pacing.stateTimer -= 20;
                if (pacing.stateTimer <= 0) {
                    pacing.setState(DirectorState.BUILD_UP, 0);
                }
            }

            // 2. FASE DE ACECHO EN SOMBRAS: Generar pistas e investigar
            case BUILD_UP -> {
                // Comprobar si el jugador hizo ruido reciente
                var ping = AudioFootprintTracker.findHeardPing(player.position(), 24.0);
                if (ping != null) {
                    // Pista acústica hacia el origen del sonido
                    pacing.setWhisper(new DirectorWhisper(
                            uuid, ping.position(), DirectorWhisper.WhisperType.INVESTIGATE_NOISE, currentTick
                    ));
                } else if (pacing.activeWhisper == null) {
                    // Pista de acecho hacia el punto ciego (12 bloques detrás del jugador)
                    Vec3 look = player.getLookAngle();
                    Vec3 behind = player.position().subtract(look.x * 12.0, 0, look.z * 12.0);
                    pacing.setWhisper(new DirectorWhisper(
                            uuid, behind, DirectorWhisper.WhisperType.SHADOW_STALK, currentTick
                    ));
                }

                // Transición a Emboscada si hay vulnerabilidad activa O estrés elevado
                if (!vulnerabilities.isEmpty() || stress >= 0.70f) {
                    pacing.setState(DirectorState.AMBUSH_READY, 60); // 3 segundos de preparación
                }
            }

            // 3. FASE DE EMBOSCADA PREPARADA: Cortar salidas
            case AMBUSH_READY -> {
                pacing.stateTimer -= 20;

                // Si el jugador está encajonado en un túnel ciego, enviar waypoint a la entrada
                if (vulnerabilities.contains(PlayerVulnerabilityDetector.VulnerabilityType.CORNERED_CHOKE)) {
                    Vec3 exitBlock = calculateChokeExit(player);
                    pacing.setWhisper(new DirectorWhisper(
                            uuid, exitBlock, DirectorWhisper.WhisperType.CUTOFF_CHOKE, currentTick
                    ));
                }

                // Si el temporizador llega a cero o el jugador está a menos de 8 bloques de hostiles -> CLÍMAX
                if (pacing.stateTimer <= 0 || hasNearbyHostiles(player, 8.0)) {
                    pacing.setState(DirectorState.CLIMAX, 0);
                    pacing.climaxCombatTimer = 0;
                }
            }

            // 4. FASE DE CLÍMAX: Combate activo F.E.A.R.
            case CLIMAX -> {
                pacing.climaxCombatTimer += 20;

                // Condición de Victoria: Si no quedan hostiles a 20 bloques tras 5 segundos de combate
                if (!hasNearbyHostiles(player, 20.0) && pacing.climaxCombatTimer >= 100) {
                    // El jugador sobrevivió a la emboscada: Garantizar 60 segundos de Respiro
                    forceTriggerReprieve(uuid, 1200);
                    return;
                }

                // Condición de Piedad (Fail-Safe): Si el combate dura más de 90 segundos o estrés extremo persistente
                if (stress >= 0.95f && pacing.climaxCombatTimer > 900) {
                    forceTriggerReprieve(uuid, 800); // 40 segundos de tregua forzada
                }
            }
        }
    }

    /**
     * Calcula la posición de la salida de un túnel ciego para que los enemigos bloqueen la puerta.
     */
    private static Vec3 calculateChokeExit(ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos check = feet.relative(dir);
            // El lado que tenga aire es la única salida por donde puede escapar
            if (player.level().getBlockState(check).isAir()) {
                return new Vec3(check.getX() + 0.5, check.getY(), check.getZ() + 0.5);
            }
        }
        return player.position();
    }

    private static boolean hasNearbyHostiles(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        List<Monster> monsters = player.serverLevel().getEntitiesOfClass(
                Monster.class, box, Monster::isAlive
        );
        return !monsters.isEmpty();
    }

    public static void clearPlayer(UUID playerUUID) {
        PLAYER_PACING_MAP.remove(playerUUID);
    }
}