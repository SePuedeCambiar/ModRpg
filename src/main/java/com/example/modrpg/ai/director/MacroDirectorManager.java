package com.example.modrpg.ai.director;

import com.example.modrpg.skills.nodes.magic.MinionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor Maestro de Ritmo y Tensión (Inspirado en Alien: Isolation).
 * SPRINT 4 FIX:
 * - Waypoints de acecho (SHADOW_STALK) anclados a cavidades de aire reales en cuevas.
 * - Punto de estrangulamiento (CUTOFF_CHOKE) proyectado a la boca real del túnel.
 * - Reconocimiento de todas las amenazas hostiles del juego (Enemy.class).
 * - Ruptura reactiva de la fase de Respiro ante agresiones sorpresivas.
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

        // Sincronización temporal con el reloj del servidor
        int currentTick = (int) (player.serverLevel().getGameTime() & 0x7FFFFFFF);

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

                // SPRINT 4 FIX: Salida de emergencia si el jugador entra en combate durante la tregua
                if (hasNearbyHostiles(player, 8.0) || stress >= 0.70f) {
                    pacing.setState(DirectorState.CLIMAX, 0);
                    pacing.climaxCombatTimer = 0;
                    return;
                }

                if (pacing.stateTimer <= 0) {
                    pacing.setState(DirectorState.BUILD_UP, 0);
                }
            }

            // 2. FASE DE ACECHO EN SOMBRAS: Generar pistas e investigar
            case BUILD_UP -> {
                // Comprobar si el jugador hizo ruido reciente
                var ping = AudioFootprintTracker.findHeardPing(player.position(), 24.0);
                if (ping != null) {
                    pacing.setWhisper(new DirectorWhisper(
                            uuid, ping.position(), DirectorWhisper.WhisperType.INVESTIGATE_NOISE, currentTick
                    ));
                } else if (pacing.activeWhisper == null) {
                    // SPRINT 4 FIX: Waypoint en cavidad de aire real (no dentro de roca maciza)
                    Vec3 stalkPos = calculateShadowStalkWaypoint(player);
                    pacing.setWhisper(new DirectorWhisper(
                            uuid, stalkPos, DirectorWhisper.WhisperType.SHADOW_STALK, currentTick
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

                // SPRINT 4 FIX: Bloquear la boca real del túnel, no los pies del jugador
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
                    forceTriggerReprieve(uuid, 1200); // 60 segundos de Respiro garantizados
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
     * SPRINT 4 FIX: Calcula un waypoint de acecho seguro en 3D.
     * Busca aire transitable detrás del jugador. Si la espalda está pegada a una pared,
     * proyecta a los flancos laterales en vez de dejar el waypoint dentro de la roca.
     */
    private static Vec3 calculateShadowStalkWaypoint(ServerPlayer player) {
        Level level = player.level();
        Vec3 look = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0, look.z).normalize();
        if (horizontalLook.lengthSqr() < 1e-4) {
            horizontalLook = new Vec3(0, 0, 1);
        }

        BlockPos playerPos = player.blockPosition();

        // 1. Probar distancias hacia atrás desde 12 hasta 4 bloques
        for (int dist = 12; dist >= 4; dist -= 2) {
            Vec3 behind = player.position().subtract(horizontalLook.scale(dist));
            BlockPos targetPos = new BlockPos((int) Math.floor(behind.x), playerPos.getY(), (int) Math.floor(behind.z));

            BlockPos safeFloor = findWalkableAirPocket(level, targetPos);
            if (safeFloor != null) {
                return new Vec3(safeFloor.getX() + 0.5, safeFloor.getY(), safeFloor.getZ() + 0.5);
            }
        }

        // 2. Si la espalda está totalmente pegada a la pared de roca, probar en los flancos laterales (90°)
        for (int side : new int[]{1, -1}) {
            Vec3 perp = new Vec3(-horizontalLook.z * side, 0, horizontalLook.x * side).normalize();
            Vec3 lateral = player.position().add(perp.scale(8.0));
            BlockPos targetPos = new BlockPos((int) Math.floor(lateral.x), playerPos.getY(), (int) Math.floor(lateral.z));

            BlockPos safeFloor = findWalkableAirPocket(level, targetPos);
            if (safeFloor != null) {
                return new Vec3(safeFloor.getX() + 0.5, safeFloor.getY(), safeFloor.getZ() + 0.5);
            }
        }

        return player.position();
    }

    /**
     * SPRINT 4 FIX: Proyecta a lo largo del túnel ciego hacia afuera (hasta 14 bloques)
     * buscando la boca de la cueva o la habitación abierta para colocar la barricada.
     */
    private static Vec3 calculateChokeExit(ServerPlayer player) {
        Level level = player.level();
        BlockPos feet = player.blockPosition();

        Direction exitDir = null;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos checkFeet = feet.relative(dir);
            BlockPos checkHead = checkFeet.above();
            if (!level.getBlockState(checkFeet).blocksMotion() && !level.getBlockState(checkHead).blocksMotion()) {
                exitDir = dir;
                break;
            }
        }

        // Si encontramos la dirección por donde sale el túnel, proyectar hacia la boca exterior
        if (exitDir != null) {
            BlockPos lastValid = feet.relative(exitDir);

            for (int i = 1; i <= 14; i++) {
                BlockPos nextFeet = feet.relative(exitDir, i);
                BlockPos nextHead = nextFeet.above();
                BlockPos nextFloor = nextFeet.below();

                // Si el túnel sigue abierto y transitable
                if (!level.getBlockState(nextFeet).blocksMotion()
                        && !level.getBlockState(nextHead).blocksMotion()
                        && level.getBlockState(nextFloor).blocksMotion()) {

                    lastValid = nextFeet;

                    // Si encontramos una apertura lateral amplia, es la entrada real del túnel
                    Direction left = exitDir.getClockWise();
                    Direction right = exitDir.getCounterClockWise();
                    if (!level.getBlockState(nextFeet.relative(left)).blocksMotion()
                            || !level.getBlockState(nextFeet.relative(right)).blocksMotion()) {
                        return new Vec3(nextFeet.getX() + 0.5, nextFeet.getY(), nextFeet.getZ() + 0.5);
                    }
                } else {
                    break;
                }
            }

            return new Vec3(lastValid.getX() + 0.5, lastValid.getY(), lastValid.getZ() + 0.5);
        }

        return player.position();
    }

    private static BlockPos findWalkableAirPocket(Level level, BlockPos origin) {
        for (int dy = 3; dy >= -3; dy--) {
            BlockPos check = origin.above(dy);
            BlockState feet = level.getBlockState(check);
            BlockState head = level.getBlockState(check.above());
            BlockState floor = level.getBlockState(check.below());

            if (feet.isAir() && head.isAir() && floor.blocksMotion()
                    && !floor.is(Blocks.LAVA) && !floor.is(Blocks.FIRE)) {
                return check;
            }
        }
        return null;
    }

    /**
     * SPRINT 4 FIX: Consulta usando Enemy.class para incluir Slimes, Ghasts, Phantoms y Magma Cubes.
     */
    private static boolean hasNearbyHostiles(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        List<LivingEntity> enemies = player.serverLevel().getEntitiesOfClass(
                LivingEntity.class, box,
                e -> e instanceof Enemy && e.isAlive() && !MinionHelper.areAllies(player, e)
        );
        return !enemies.isEmpty();
    }

    public static void clearPlayer(UUID playerUUID) {
        PLAYER_PACING_MAP.remove(playerUUID);
    }
}