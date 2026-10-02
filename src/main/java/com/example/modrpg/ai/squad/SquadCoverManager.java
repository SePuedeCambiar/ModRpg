package com.example.modrpg.ai.squad;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Administra el grafo de coberturas espaciales del escuadrón (F.E.A.R.):
 * SPRINT 4 & 6 FIX:
 * - Evita que en cuevas los casters intenten caminar hacia coberturas detrás de paredes macizas.
 * - Limita los raycasts a un presupuesto estricto para proteger los TPS del servidor.
 * - Purga nodos comprometidos cuando el jugador se desplaza y gana línea de visión sobre ellos.
 */
public class SquadCoverManager {

    private final Map<BlockPos, CoverNode> knownCoverNodes = new ConcurrentHashMap<>();
    private int scanCooldown = 0;

    public void tick(ServerLevel level, LivingEntity target, Collection<Mob> members) {
        // 1. Decrementar reservas y expirar leases activos
        for (CoverNode node : knownCoverNodes.values()) {
            node.tick();
        }

        if (target == null || members.isEmpty()) {
            knownCoverNodes.clear();
            scanCooldown = 0;
            return;
        }

        // 2. Limpieza de nodos comprometidos: si el jugador se movió y ahora ve la cobertura, purgarla
        cleanupCompromisedNodes(level, target);

        if (scanCooldown > 0) {
            scanCooldown--;
            return;
        }

        // SPRINT 4 PERF FIX: Si ya existen suficientes coberturas disponibles (>= 4), pausar escaneo 1.5s
        long availableCount = knownCoverNodes.values().stream().filter(n -> n.isAvailable(null)).count();
        if (availableCount >= 4) {
            scanCooldown = 30; // 1.5 segundos de reposo
            return;
        }

        scanCooldown = 25; // Escaneo estándar cada 1.25 segundos

        // 3. Escaneo Time-Sliced centrado en el miembro del escuadrón más cercano a la amenaza
        Mob anchorMember = selectAnchorMember(members, target);
        if (anchorMember != null) {
            scanNearbyObstacles(level, anchorMember.blockPosition(), target);
        }
    }

    /**
     * SPRINT 4 FIX: Elimina nodos de cobertura si el jugador rodeó el muro y ahora tiene visión directa.
     */
    private void cleanupCompromisedNodes(ServerLevel level, LivingEntity target) {
        Vec3 targetEye = target.getEyePosition();
        knownCoverNodes.entrySet().removeIf(entry -> {
            CoverNode node = entry.getValue();
            // Solo comprobar nodos libres para no desorientar abruptamente a un mob que esté llegando
            if (node.isAvailable(null)) {
                Vec3 standEye = node.getCenterVec().add(0, 1.2, 0);
                HitResult hit = level.clip(new ClipContext(
                        targetEye, standEye,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target
                ));
                return hit.getType() != HitResult.Type.BLOCK; // Si el raycast no choca con un muro, el nodo está expuesto
            }
            return false;
        });
    }

    private Mob selectAnchorMember(Collection<Mob> members, LivingEntity target) {
        Mob closest = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Mob m : members) {
            if (m.isAlive()) {
                double distSq = m.distanceToSqr(target);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    closest = m;
                }
            }
        }
        return closest != null ? closest : (members.isEmpty() ? null : members.iterator().next());
    }

    /**
     * SPRINT 4 FIX: Escaneo optimizado con límite estricto de 8 raycasts por ciclo
     * y radio reducido de 8 a 6 bloques (147 candidatos vs 324 originales).
     */
    private void scanNearbyObstacles(ServerLevel level, BlockPos origin, LivingEntity target) {
        Vec3 targetEye = target.getEyePosition();
        int searchRadius = 6;
        int raycastsBudget = 8; // Presupuesto de raycasts por escaneo para proteger TPS

        for (int dx = -searchRadius; dx <= searchRadius && raycastsBudget > 0; dx += 2) {
            for (int dz = -searchRadius; dz <= searchRadius && raycastsBudget > 0; dz += 2) {
                for (int dy = -1; dy <= 2 && raycastsBudget > 0; dy++) {
                    BlockPos wallCandidate = origin.offset(dx, dy, dz);
                    BlockState wallState = level.getBlockState(wallCandidate);

                    // Muro debe bloquear movimiento y tener al menos 2 bloques de altura
                    if (!wallState.blocksMotion() || !level.getBlockState(wallCandidate.above()).blocksMotion()) {
                        continue;
                    }

                    double toWallX = (wallCandidate.getX() + 0.5) - targetEye.x;
                    double toWallZ = (wallCandidate.getZ() + 0.5) - targetEye.z;

                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        // Descarte por Dot Product: Solo considerar caras que caen en la sombra opuesta al jugador
                        double dot = (dir.getStepX() * toWallX) + (dir.getStepZ() * toWallZ);
                        if (dot <= 0) continue;

                        BlockPos standPos = wallCandidate.relative(dir);
                        if (knownCoverNodes.containsKey(standPos)) continue;

                        BlockState standState = level.getBlockState(standPos);
                        BlockState standAbove = level.getBlockState(standPos.above());
                        BlockState standBelow = level.getBlockState(standPos.below());

                        // Comprobar espacio libre para el cuerpo y suelo seguro no inflamable ni líquido
                        if (standState.isAir() && standAbove.isAir() && standBelow.blocksMotion()
                                && !standBelow.is(Blocks.LAVA)
                                && !standBelow.is(Blocks.FIRE)
                                && !standBelow.is(Blocks.MAGMA_BLOCK)) {

                            Vec3 standEye = new Vec3(standPos.getX() + 0.5, standPos.getY() + 1.5, standPos.getZ() + 0.5);
                            HitResult hit = level.clip(new ClipContext(
                                    targetEye, standEye,
                                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target
                            ));
                            raycastsBudget--;

                            if (hit.getType() == HitResult.Type.BLOCK) {
                                knownCoverNodes.put(standPos, new CoverNode(standPos));
                            }

                            if (raycastsBudget <= 0) break;
                        }
                    }
                }
            }
        }

        // Capacidad máxima de memoria de coberturas (12 nodos relevantes)
        if (knownCoverNodes.size() > 12) {
            knownCoverNodes.entrySet().removeIf(entry -> entry.getValue().isAvailable(null));
        }
    }

    /**
     * SPRINT 4 FIX: Encuentra y reserva la cobertura más cercana validando que sea
     * físicamente alcanzable y no esté bloqueada al otro lado de una pared de cueva.
     */
    public Vec3 findAndClaimCover(Mob mob, LivingEntity target, double maxDistance) {
        if (knownCoverNodes.isEmpty() || mob == null || !mob.isAlive()) return null;

        Vec3 mobEye = mob.getEyePosition();
        Vec3 mobPos = mob.position();
        double bestDistSq = maxDistance * maxDistance;
        CoverNode bestNode = null;

        for (CoverNode node : knownCoverNodes.values()) {
            if (node.isAvailable(mob.getUUID())) {
                double distSq = node.getCenterVec().distanceToSqr(mobPos);
                if (distSq < bestDistSq) {
                    // Validar si la cobertura es alcanzable o si está separada por roca sólida impenetrable
                    Vec3 nodeEye = node.getCenterVec().add(0, 1.2, 0);
                    HitResult directLine = mob.level().clip(new ClipContext(
                            mobEye, nodeEye,
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob
                    ));

                    // Si hay visión directa hacia la cobertura O está muy cerca (< 4 bloques):
                    if (directLine.getType() == HitResult.Type.MISS || distSq <= 16.0) {
                        bestDistSq = distSq;
                        bestNode = node;
                    }
                    // Si hay una pared de por medio, validar si el pathfinder puede crear una ruta real
                    else if (mob.getNavigation().createPath(node.getPos(), 0) != null) {
                        bestDistSq = distSq;
                        bestNode = node;
                    }
                }
            }
        }

        if (bestNode != null && bestNode.claim(mob.getUUID(), 80)) { // Reserva por 4 segundos
            return bestNode.getCenterVec();
        }

        return null;
    }

    public void releaseCover(UUID mobUUID) {
        if (mobUUID == null) return;
        for (CoverNode node : knownCoverNodes.values()) {
            if (Objects.equals(node.getReservedBy(), mobUUID)) {
                node.release();
            }
        }
    }
}