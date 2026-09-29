package com.example.modrpg.ai.squad;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Administra el grafo de coberturas espaciales del escuadrón.
 * Utiliza Time-Slicing y cálculo de sombras vectoriales para erradicar el lag.
 */
public class SquadCoverManager {

    private final Map<BlockPos, CoverNode> knownCoverNodes = new ConcurrentHashMap<>();
    private int scanCooldown = 0;

    public void tick(ServerLevel level, LivingEntity target, Collection<Mob> members) {
        // 1. Decrementar reservas existentes
        for (CoverNode node : knownCoverNodes.values()) {
            node.tick();
        }

        // 2. Limpieza periódica de nodos que ya no ofrecen cobertura (ej: si el jugador se movió)
        if (scanCooldown > 0) {
            scanCooldown--;
            return;
        }

        scanCooldown = 10; // Solo escanea 1 vez cada 10 ticks (0.5 segundos)

        if (target == null || members.isEmpty()) {
            knownCoverNodes.clear();
            return;
        }

        // 3. Escaneo Time-Sliced alrededor de un miembro aleatorio del escuadrón
        Mob anchorMember = members.iterator().next();
        scanNearbyObstacles(level, anchorMember.blockPosition(), target);
    }

    /**
     * Algoritmo de Sombra Vectorial:
     * Identifica bloques sólidos de 2 de altura y solo evalúa los bloques adyacentes
     * que caen geométricamente en la "sombra" opuesta al jugador.
     */
    private void scanNearbyObstacles(ServerLevel level, BlockPos origin, LivingEntity target) {
        Vec3 targetEye = target.getEyePosition();
        int searchRadius = 8;

        for (int dx = -searchRadius; dx <= searchRadius; dx += 2) {
            for (int dz = -searchRadius; dz <= searchRadius; dz += 2) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos wallCandidate = origin.offset(dx, dy, dz);
                    BlockState wallState = level.getBlockState(wallCandidate);

                    // Muro debe ser sólido y tener al menos 2 bloques de altura sólida
                    if (!wallState.isSolid() || !level.getBlockState(wallCandidate.above()).isSolid()) {
                        continue;
                    }

                    // Vector proyectado desde el jugador hacia el centro del muro
                    double toWallX = (wallCandidate.getX() + 0.5) - targetEye.x;
                    double toWallZ = (wallCandidate.getZ() + 0.5) - targetEye.z;

                    // Evaluar las 4 caras cardinales adyacentes al muro
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        // Optimización Dot Product: Solo considerar la cara en la dirección de la sombra
                        double dot = (dir.getStepX() * toWallX) + (dir.getStepZ() * toWallZ);
                        if (dot <= 0) {
                            continue; // Descarta el 75% de las caras sin lanzar raycast
                        }

                        BlockPos standPos = wallCandidate.relative(dir);

                        // La posición de cobertura debe tener suelo sólido y aire para el cuerpo
                        if (level.getBlockState(standPos).isAir() &&
                                level.getBlockState(standPos.above()).isAir() &&
                                level.getBlockState(standPos.below()).isSolid()) {

                            // Único raycast confirmatorio de Línea de Visión (LoS)
                            Vec3 standEye = new Vec3(standPos.getX() + 0.5, standPos.getY() + 1.5, standPos.getZ() + 0.5);
                            HitResult hit = level.clip(new ClipContext(
                                    targetEye, standEye,
                                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target
                            ));

                            if (hit.getType() == HitResult.Type.BLOCK) {
                                knownCoverNodes.putIfAbsent(standPos, new CoverNode(standPos));
                            }
                        }
                    }
                }
            }
        }

        // Limitar tamaño de la memoria de coberturas para evitar acumulación
        if (knownCoverNodes.size() > 16) {
            knownCoverNodes.entrySet().removeIf(entry -> entry.getValue().isAvailable(null));
        }
    }

    /**
     * Encuentra y reserva la cobertura válida más cercana para el mob solicitante.
     */
    public Vec3 findAndClaimCover(Mob mob, LivingEntity target, double maxDistance) {
        if (knownCoverNodes.isEmpty() || mob == null) return null;

        Vec3 mobPos = mob.position();
        CoverNode bestNode = null;
        double bestDistSq = maxDistance * maxDistance;

        for (CoverNode node : knownCoverNodes.values()) {
            if (node.isAvailable(mob.getUUID())) {
                double distSq = node.getCenterVec().distanceToSqr(mobPos);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestNode = node;
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