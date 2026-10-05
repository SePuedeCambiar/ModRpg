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
 * SPRINT 3 & 4 FIX:
 * - Time-slicing cada 10 ticks en la limpieza de nodos para proteger los TPS.
 * - Método público pruneExcessNodes(): poda únicamente los nodos sobrantes en vez de vaciar la lista entera.
 * - Filtro de obstáculos con raycasts presupuestados y descarte por Dot Product.
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

        // B8 FIX: Time-slicing en la verificación de línea de visión (corre cada 10 ticks, no en cada tick)
        if (scanCooldown % 10 == 0) {
            cleanupCompromisedNodes(level, target);
        }

        if (scanCooldown > 0) {
            scanCooldown--;
            return;
        }

        // Si ya existen suficientes coberturas disponibles (>= 4), pausar escaneo 1.5s
        long availableCount = knownCoverNodes.values().stream().filter(n -> n.isAvailable(null)).count();
        if (availableCount >= 4) {
            scanCooldown = 30; // 1.5 segundos de reposo
            return;
        }

        scanCooldown = 25; // Escaneo estándar cada 1.25 segundos

        // 2. Escaneo centrado en el miembro del escuadrón más cercano a la amenaza
        Mob anchorMember = selectAnchorMember(members, target);
        if (anchorMember != null) {
            scanNearbyObstacles(level, anchorMember.blockPosition(), target);
        }
    }

    /**
     * Elimina nodos de cobertura si el jugador rodeó el muro y ahora tiene visión directa.
     */
    private void cleanupCompromisedNodes(ServerLevel level, LivingEntity target) {
        Vec3 targetEye = target.getEyePosition();
        knownCoverNodes.entrySet().removeIf(entry -> {
            CoverNode node = entry.getValue();
            if (node.isAvailable(null)) {
                Vec3 standEye = node.getCenterVec().add(0, 1.5, 0);
                HitResult hit = level.clip(new ClipContext(
                        targetEye, standEye,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target
                ));
                return hit.getType() != HitResult.Type.BLOCK;
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
     * Escaneo de obstáculos con presupuesto estricto de 8 raycasts por ciclo.
     */
    private void scanNearbyObstacles(ServerLevel level, BlockPos origin, LivingEntity target) {
        Vec3 targetEye = target.getEyePosition();
        int searchRadius = 6;
        int raycastsBudget = 8;

        for (int dx = -searchRadius; dx <= searchRadius && raycastsBudget > 0; dx += 2) {
            for (int dz = -searchRadius; dz <= searchRadius && raycastsBudget > 0; dz += 2) {
                for (int dy = -1; dy <= 2 && raycastsBudget > 0; dy++) {
                    BlockPos wallCandidate = origin.offset(dx, dy, dz);
                    BlockState wallState = level.getBlockState(wallCandidate);

                    if (!wallState.blocksMotion() || !level.getBlockState(wallCandidate.above()).blocksMotion()) {
                        continue;
                    }

                    double toWallX = (wallCandidate.getX() + 0.5) - targetEye.x;
                    double toWallZ = (wallCandidate.getZ() + 0.5) - targetEye.z;

                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        double dot = (dir.getStepX() * toWallX) + (dir.getStepZ() * toWallZ);
                        if (dot <= 0) continue;

                        BlockPos standPos = wallCandidate.relative(dir);
                        if (knownCoverNodes.containsKey(standPos)) continue;

                        BlockState standState = level.getBlockState(standPos);
                        BlockState standAbove = level.getBlockState(standPos.above());
                        BlockState standBelow = level.getBlockState(standPos.below());

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

        // B8 FIX: Podar únicamente los nodos sobrantes de forma controlada
        pruneExcessNodes();
    }

    /**
     * B8 FIX: Poda controlada de nodos excedentes.
     * Si la cantidad de coberturas supera el tope de 12, elimina únicamente los nodos excedentes
     * que estén libres, dejando la lista acotada a 12 en vez de vaciarla a 0.
     */
    public void pruneExcessNodes() {
        if (knownCoverNodes.size() > 12) {
            Iterator<Map.Entry<BlockPos, CoverNode>> it = knownCoverNodes.entrySet().iterator();
            while (it.hasNext() && knownCoverNodes.size() > 12) {
                CoverNode node = it.next().getValue();
                if (node.isAvailable(null)) {
                    it.remove();
                }
            }
        }
    }

    /**
     * Encuentra y reserva la cobertura más cercana validando que sea alcanzable.
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
                    Vec3 nodeEye = node.getCenterVec().add(0, 1.5, 0);
                    HitResult directLine = mob.level().clip(new ClipContext(
                            mobEye, nodeEye,
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob
                    ));

                    if (directLine.getType() == HitResult.Type.MISS || distSq <= 16.0) {
                        bestDistSq = distSq;
                        bestNode = node;
                    } else if (mob.getNavigation().createPath(node.getPos(), 0) != null) {
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