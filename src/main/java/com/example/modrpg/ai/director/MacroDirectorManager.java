package com.example.modrpg.ai.director;

import com.example.modrpg.skills.nodes.magic.MinionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
 * SPRINT 4 FIX (C1, C3, C4):
 * - CLIMAX_TIMEOUT_TICKS: Timeout duro de 90s para evitar softlocks de combate.
 * - shouldBreakReprieve: El estrés residual alto ya no rompe la tregua de respiro.
 * - hasNearbyHostiles: Solo cuenta hostiles con target activo en el jugador.
 */
public class MacroDirectorManager {

    // C4 FIX: Timeout duro absoluto de 90 segundos para salir de CLIMAX
    public static final int CLIMAX_TIMEOUT_TICKS = 1800;

    public static class PacingData {
        private DirectorState state = DirectorState.BUILD_UP;
        private int stateTimer = 0;
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

    public static void clearAll() {
        PLAYER_PACING_MAP.clear();
    }

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

    public static void forceTriggerReprieve(UUID playerUUID, int durationTicks) {
        PacingData data = getPacingData(playerUUID);
        data.setState(DirectorState.REPRIEVE, durationTicks);
        data.setWhisper(null);
    }

    // C3 FIX: Regla formal para saber si el respiro debe romperse (exige daño hostil reciente)
    public static boolean shouldBreakReprieve(boolean hasCloseCombat, int ticksSinceLastDamage, float stress) {
        if (hasCloseCombat) return true;
        // Solo romper si hubo daño directo recibido en los últimos 2s (40 ticks) y estrés alto
        if (ticksSinceLastDamage < 40 && stress >= 0.70f) return true;
        // Estrés residual alto sin combate activo NO rompe la tregua
        return false;
    }

    public static void tick(ServerPlayer player) {
        if (player == null || !player.isAlive()) return;

        UUID uuid = player.getUUID();
        PacingData pacing = getPacingData(uuid);
        float stress = PlayerStressTracker.getStress(player);
        var vulnerabilities = PlayerVulnerabilityDetector.getActiveVulnerabilities(player);

        int currentTick = (int) (player.serverLevel().getGameTime() & 0x7FFFFFFF);

        if (pacing.activeWhisper != null && pacing.activeWhisper.isExpired(currentTick)) {
            pacing.setWhisper(null);
        }

        switch (pacing.getState()) {

            // 1. FASE DE RESPIRO
            case REPRIEVE -> {
                pacing.stateTimer -= 20;

                PlayerStressTracker.StressData stressData = PlayerStressTracker.getData(uuid);
                boolean closeCombat = hasNearbyHostiles(player, 8.0);

                // C3 FIX: No romper la tregua por el estrés residual con el que termina un combate
                if (shouldBreakReprieve(closeCombat, stressData.getTicksSinceLastDamage(), stress)) {
                    pacing.setState(DirectorState.CLIMAX, 0);
                    pacing.climaxCombatTimer = 0;
                    return;
                }

                if (pacing.stateTimer <= 0) {
                    pacing.setState(DirectorState.BUILD_UP, 0);
                }
            }

            // 2. FASE DE ACECHO EN SOMBRAS
            case BUILD_UP -> {
                var ping = AudioFootprintTracker.findHeardPing(player.position(), 24.0);
                if (ping != null && (currentTick - ping.tickCreated() <= 60)) {
                    pacing.setWhisper(new DirectorWhisper(
                            uuid, ping.position(), DirectorWhisper.WhisperType.INVESTIGATE_NOISE, currentTick
                    ));
                } else if (pacing.activeWhisper == null) {
                    Vec3 stalkPos = calculateShadowStalkWaypoint(player);
                    pacing.setWhisper(new DirectorWhisper(
                            uuid, stalkPos, DirectorWhisper.WhisperType.SHADOW_STALK, currentTick
                    ));
                }

                if (!vulnerabilities.isEmpty() || stress >= 0.70f) {
                    pacing.setState(DirectorState.AMBUSH_READY, 60);
                }
            }

            // 3. FASE DE EMBOSCADA PREPARADA
            case AMBUSH_READY -> {
                pacing.stateTimer -= 20;

                if (vulnerabilities.contains(PlayerVulnerabilityDetector.VulnerabilityType.CORNERED_CHOKE)) {
                    Vec3 exitBlock = calculateChokeExit(player);
                    pacing.setWhisper(new DirectorWhisper(
                            uuid, exitBlock, DirectorWhisper.WhisperType.CUTOFF_CHOKE, currentTick
                    ));
                }

                if (pacing.stateTimer <= 0 || hasNearbyHostiles(player, 8.0)) {
                    pacing.setState(DirectorState.CLIMAX, 0);
                    pacing.climaxCombatTimer = 0;
                }
            }

            // 4. FASE DE CLÍMAX
            case CLIMAX -> {
                pacing.climaxCombatTimer += 20;

                // Condición de Victoria: Sin hostiles atacando tras 5s
                if (!hasNearbyHostiles(player, 20.0) && pacing.climaxCombatTimer >= 100) {
                    forceTriggerReprieve(uuid, 1200);
                    return;
                }

                // C4 FIX: Timeout duro para evitar softlocks eternos por mobs neutros o granjas
                if (pacing.climaxCombatTimer >= CLIMAX_TIMEOUT_TICKS) {
                    forceTriggerReprieve(uuid, 1200);
                    return;
                }

                // Condición de Piedad por estrés extremo
                if (stress >= 0.95f && pacing.climaxCombatTimer > 900) {
                    forceTriggerReprieve(uuid, 800);
                }
            }
        }
    }

    private static Vec3 calculateShadowStalkWaypoint(ServerPlayer player) {
        Level level = player.level();
        Vec3 look = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0, look.z).normalize();
        if (horizontalLook.lengthSqr() < 1e-4) {
            horizontalLook = new Vec3(0, 0, 1);
        }

        BlockPos playerPos = player.blockPosition();

        for (int dist = 12; dist >= 4; dist -= 2) {
            Vec3 behind = player.position().subtract(horizontalLook.scale(dist));
            BlockPos targetPos = new BlockPos((int) Math.floor(behind.x), playerPos.getY(), (int) Math.floor(behind.z));

            BlockPos safeFloor = findWalkableAirPocket(level, targetPos);
            if (safeFloor != null) {
                return new Vec3(safeFloor.getX() + 0.5, safeFloor.getY(), safeFloor.getZ() + 0.5);
            }
        }

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

        if (exitDir != null) {
            BlockPos lastValid = feet.relative(exitDir);

            for (int i = 1; i <= 14; i++) {
                BlockPos nextFeet = feet.relative(exitDir, i);
                BlockPos nextHead = nextFeet.above();
                BlockPos nextFloor = nextFeet.below();

                if (!level.getBlockState(nextFeet).blocksMotion()
                        && !level.getBlockState(nextHead).blocksMotion()
                        && level.getBlockState(nextFloor).blocksMotion()) {

                    lastValid = nextFeet;

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

    // C4 FIX: Solo cuenta entidades hostiles que tengan al jugador en su mira
    private static boolean hasNearbyHostiles(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        List<LivingEntity> enemies = player.serverLevel().getEntitiesOfClass(
                LivingEntity.class, box,
                e -> e instanceof Enemy && e.isAlive() && !MinionHelper.areAllies(player, e)
                        && (e instanceof Mob mob && mob.getTarget() == player)
        );
        return !enemies.isEmpty();
    }

    public static void clearPlayer(UUID playerUUID) {
        PLAYER_PACING_MAP.remove(playerUUID);
    }
}