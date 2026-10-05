package com.example.modrpg.ai;

import com.example.modrpg.ai.director.MacroDirectorManager;
import com.example.modrpg.ai.feedback.SquadBarkManager;
import com.example.modrpg.ai.squad.SquadCoverManager;
import com.example.modrpg.ai.squad.SquadTacticalToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cerebro Táctico Central del Escuadrón (F.E.A.R.):
 * - Sprint 1: Fugas por despawn natural y aislamiento por dimensión.
 * - Sprint 3: Preservación de tokens activos y limpieza atómica de emergencias (clearPeelRequest).
 * - Sprint 4: Flanqueo 3D con Ground Snapping para montañas, cuevas y desniveles.
 */
public class SquadCoordinator {

    public static class Squad {
        public final UUID squadId = UUID.randomUUID();
        public UUID leaderUUID = null;
        public ResourceKey<Level> dimension = null;
        public final Set<UUID> memberUUIDs = Collections.newSetFromMap(new ConcurrentHashMap<>());

        private final SquadCoverManager coverManager = new SquadCoverManager();
        private final List<SquadTacticalToken> tokenPool = new CopyOnWriteArrayList<>();

        // Señales de combate
        private UUID peelRequestedBy = null;
        private int peelTimer = 0;
        private int moraleBreakTimer = 0;

        public Squad() {
            rebuildTokenPool();
        }

        public SquadCoverManager getCoverManager() {
            return coverManager;
        }

        /**
         * Reconstrucción no destructiva de tokens: preserva los que estén en uso activo (ACTIVE).
         */
        public void rebuildTokenPool() {
            int desiredPrimaryCount = Math.max(1, memberUUIDs.size() / 3);

            // 1. Asegurar token PEEL
            if (tokenPool.stream().noneMatch(t -> t.getType() == SquadTacticalToken.TokenType.PEEL)) {
                tokenPool.add(new SquadTacticalToken(SquadTacticalToken.TokenType.PEEL));
            }

            // 2. Asegurar token SUPPRESSION
            if (tokenPool.stream().noneMatch(t -> t.getType() == SquadTacticalToken.TokenType.SUPPRESSION)) {
                tokenPool.add(new SquadTacticalToken(SquadTacticalToken.TokenType.SUPPRESSION));
            }

            // 3. Ajustar tokens PRIMARY_ATTACK preservando los activos
            long currentPrimaryCount = tokenPool.stream().filter(t -> t.getType() == SquadTacticalToken.TokenType.PRIMARY_ATTACK).count();

            if (currentPrimaryCount < desiredPrimaryCount) {
                for (int i = 0; i < (desiredPrimaryCount - currentPrimaryCount); i++) {
                    tokenPool.add(new SquadTacticalToken(SquadTacticalToken.TokenType.PRIMARY_ATTACK));
                }
            } else if (currentPrimaryCount > desiredPrimaryCount) {
                for (SquadTacticalToken token : tokenPool) {
                    if (token.getType() == SquadTacticalToken.TokenType.PRIMARY_ATTACK && token.isAvailable()) {
                        tokenPool.remove(token);
                        currentPrimaryCount--;
                        if (currentPrimaryCount <= desiredPrimaryCount) break;
                    }
                }
            }
        }

        public boolean isInPanic() {
            return moraleBreakTimer > 0;
        }

        public boolean requestToken(Mob mob, SquadTacticalToken.TokenType type, int leaseTicks) {
            if (mob == null || !mob.isAlive()) return false;

            if (isInPanic() && type != SquadTacticalToken.TokenType.PEEL) {
                return false;
            }

            for (SquadTacticalToken token : tokenPool) {
                if (token.getType() == type && (token.isAvailable() || Objects.equals(token.getHolderUUID(), mob.getUUID()))) {
                    return token.claim(mob, leaseTicks);
                }
            }
            return false;
        }

        public void releaseToken(Mob mob, SquadTacticalToken.TokenType type) {
            if (mob == null) return;
            for (SquadTacticalToken token : tokenPool) {
                if (token.getType() == type && Objects.equals(token.getHolderUUID(), mob.getUUID())) {
                    token.release();
                    break;
                }
            }
        }

        public void forceReleaseAllTokens(UUID mobUUID) {
            if (mobUUID == null) return;
            for (SquadTacticalToken token : tokenPool) {
                if (Objects.equals(token.getHolderUUID(), mobUUID)) {
                    token.release();
                }
            }
        }

        public void requestPeel(Mob caller, LivingEntity threat) {
            if (caller == null || !caller.isAlive()) return;
            this.peelRequestedBy = caller.getUUID();
            this.peelTimer = 60; // 3 segundos de ventana de intercepción
        }

        /**
         * SPRINT 3 FIX: Da por concluida la alerta de rescate para evitar que múltiples tanques re-embistan.
         */
        public void clearPeelRequest() {
            this.peelRequestedBy = null;
            this.peelTimer = 0;
        }

        public boolean isPeelRequested() {
            return peelRequestedBy != null && peelTimer > 0;
        }

        public void tick(ServerLevel level) {
            if (peelTimer > 0) peelTimer--;
            else peelRequestedBy = null;

            if (moraleBreakTimer > 0) moraleBreakTimer--;

            // 1. Recopilar miembros vivos válidos y limpiar referencias muertas
            List<Mob> aliveMembers = new ArrayList<>();
            LivingEntity sharedTarget = null;

            Iterator<UUID> it = memberUUIDs.iterator();
            while (it.hasNext()) {
                UUID memberId = it.next();
                if (level.getEntity(memberId) instanceof Mob mob) {
                    if (mob.isAlive()) {
                        aliveMembers.add(mob);
                        if (sharedTarget == null && mob.getTarget() != null && mob.getTarget().isAlive()) {
                            sharedTarget = mob.getTarget();
                        }
                    } else {
                        it.remove();
                        MOB_SQUAD_MAP.remove(memberId);
                    }
                }
            }

            // 2. Tick de coberturas espaciales
            coverManager.tick(level, sharedTarget, aliveMembers);

            // 3. Heartbeat de integridad de tokens
            for (SquadTacticalToken token : tokenPool) {
                if (token.getState() == SquadTacticalToken.TokenState.ACTIVE) {
                    Mob holder = (token.getHolderUUID() != null) ? (Mob) level.getEntity(token.getHolderUUID()) : null;
                    token.tick(holder);
                }
            }
        }

        /**
         * SPRINT 4 FIX: Flanqueo 3D anclado al suelo real (Ground Snapping).
         * - Usa el vector de mirada del objetivo para buscar el punto ciego real (> 75°).
         * - Escanea verticalmente en la columna de destino para no incrustar al mob en roca ni dejarlo en el aire.
         * - Si la posición es inaccesible o cae en lava/precipicio, devuelve null limpiamente.
         */
        public Vec3 getFlankingPosition(Mob mob, LivingEntity target) {
            if (mob == null || target == null || !mob.isAlive() || !target.isAlive()) {
                return null;
            }

            Level level = mob.level();
            Vec3 targetPos = target.position();
            Vec3 targetLook = target.getLookAngle();

            // 1. Vector horizontal de mirada del objetivo (Jugador)
            Vec3 lookHorizontal = new Vec3(targetLook.x, 0, targetLook.z).normalize();
            if (lookHorizontal.lengthSqr() < 1e-4) {
                lookHorizontal = new Vec3(0, 0, 1);
            }

            // 2. Determinar flanco izquierdo o derecho según el mob para rodearlo por ambos lados
            int side = (mob.hashCode() % 2 == 0) ? 1 : -1;
            Vec3 perpendicular = new Vec3(-lookHorizontal.z * side, 0, lookHorizontal.x * side).normalize();

            // 3. Proyectar hacia el cuadrante trasero-lateral (5m hacia atrás, 6.5m hacia el costado)
            Vec3 idealFlankPos = targetPos
                    .subtract(lookHorizontal.scale(5.0))
                    .add(perpendicular.scale(6.5));

            int targetX = (int) Math.floor(idealFlankPos.x);
            int targetZ = (int) Math.floor(idealFlankPos.z);
            int anchorY = (int) Math.floor(targetPos.y);

            // 4. Buscar suelo transitable seguro dentro de +- 4 bloques verticales del jugador
            BlockPos safeFloorPos = findSafeFlankFloor(level, targetX, anchorY, targetZ);
            if (safeFloorPos != null) {
                return new Vec3(safeFloorPos.getX() + 0.5, safeFloorPos.getY(), safeFloorPos.getZ() + 0.5);
            }

            // Si está dentro de la montaña o en un abismo, retornar null para no forzar ruta contra la pared
            return null;
        }

        private static BlockPos findSafeFlankFloor(Level level, int x, int anchorY, int z) {
            for (int dy = 4; dy >= -4; dy--) {
                BlockPos pos = new BlockPos(x, anchorY + dy, z);
                if (isSafeStandPosition(level, pos)) {
                    return pos;
                }
            }
            return null;
        }

        private static boolean isSafeStandPosition(Level level, BlockPos pos) {
            BlockState feet = level.getBlockState(pos);
            BlockState head = level.getBlockState(pos.above());
            BlockState floor = level.getBlockState(pos.below());

            if (!feet.isAir() || !head.isAir()) {
                return false;
            }

            return floor.blocksMotion()
                    && !floor.is(Blocks.LAVA)
                    && !floor.is(Blocks.FIRE)
                    && !floor.is(Blocks.MAGMA_BLOCK);
        }

        public void triggerMoraleBreak(ServerLevel level, Mob deadLeader) {
            this.moraleBreakTimer = 60; // 3 segundos de pánico

            for (SquadTacticalToken token : tokenPool) {
                token.release();
            }

            SquadBarkManager.triggerBark(deadLeader, SquadBarkManager.BarkType.MORALE_BREAK, level);

            AABB searchPlayer = deadLeader.getBoundingBox().inflate(32.0);
            List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class, searchPlayer);
            for (ServerPlayer player : nearbyPlayers) {
                MacroDirectorManager.forceTriggerReprieve(player.getUUID(), 1600);
                player.displayClientMessage(
                        Component.literal("§a§l🕊 [RESPIRO CONCEDIDO] §7El escuadrón retrocede. Tienes 80s de tregua."),
                        true
                );
            }

            Vec3 deathPos = deadLeader.position();
            AABB area = new AABB(deathPos.x - 20, deathPos.y - 8, deathPos.z - 20, deathPos.x + 20, deathPos.y + 8, deathPos.z + 20);
            List<Mob> nearby = level.getEntitiesOfClass(Mob.class, area, m -> memberUUIDs.contains(m.getUUID()) && m.isAlive());

            for (Mob survivor : nearby) {
                survivor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                survivor.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
                survivor.getNavigation().stop();
                level.sendParticles(ParticleTypes.SMOKE, survivor.getX(), survivor.getEyeY() + 0.3, survivor.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    private static final Map<UUID, Squad> ACTIVE_SQUADS = new ConcurrentHashMap<>();
    private static final Map<UUID, Squad> MOB_SQUAD_MAP = new ConcurrentHashMap<>();

    public static Squad getSquadFor(Mob mob) {
        return (mob != null) ? MOB_SQUAD_MAP.get(mob.getUUID()) : null;
    }

    public static void assignToSquad(Mob mob) {
        if (mob.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) mob.level();

        AABB search = mob.getBoundingBox().inflate(16.0);
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, search, m -> m != mob && MOB_SQUAD_MAP.containsKey(m.getUUID()));

        Squad squad;
        if (!nearby.isEmpty()) {
            squad = MOB_SQUAD_MAP.get(nearby.get(0).getUUID());
        } else {
            squad = new Squad();
            squad.dimension = level.dimension();
            ACTIVE_SQUADS.put(squad.squadId, squad);
        }

        squad.memberUUIDs.add(mob.getUUID());
        MOB_SQUAD_MAP.put(mob.getUUID(), squad);
        squad.rebuildTokenPool();
    }

    public static void clearAll() {
        ACTIVE_SQUADS.clear();
        MOB_SQUAD_MAP.clear();
    }


    /**
     * Muerte confirmada en combate (LivingDeathEvent).
     */
    public static void onMobDeath(Mob deadMob) {
        if (deadMob == null) return;
        UUID mobId = deadMob.getUUID();
        SquadBarkManager.clearMobMemory(mobId);

        Squad squad = MOB_SQUAD_MAP.remove(mobId);
        if (squad != null) {
            squad.forceReleaseAllTokens(mobId);
            squad.getCoverManager().releaseCover(mobId);
            squad.memberUUIDs.remove(mobId);
            squad.rebuildTokenPool();

            if (deadMob.level() instanceof ServerLevel level) {
                if (Objects.equals(squad.leaderUUID, mobId)) {
                    squad.triggerMoraleBreak(level, deadMob);
                    squad.leaderUUID = null;
                }
            }
            if (squad.memberUUIDs.isEmpty()) {
                ACTIVE_SQUADS.remove(squad.squadId);
            }
        }
    }

    /**
     * Despawn natural o salida de nivel sin muerte (EntityLeaveLevelEvent).
     */
    public static void onMobDespawnOrLeave(Mob mob) {
        if (mob == null) return;
        UUID mobId = mob.getUUID();
        SquadBarkManager.clearMobMemory(mobId);

        Squad squad = MOB_SQUAD_MAP.remove(mobId);
        if (squad != null) {
            squad.forceReleaseAllTokens(mobId);
            squad.getCoverManager().releaseCover(mobId);
            squad.memberUUIDs.remove(mobId);
            if (Objects.equals(squad.leaderUUID, mobId)) {
                squad.leaderUUID = null;
            }
            squad.rebuildTokenPool();

            if (squad.memberUUIDs.isEmpty()) {
                ACTIVE_SQUADS.remove(squad.squadId);
            }
        }
    }

    /**
     * Tick maestro ejecutado desde ModEvents.onLevelTick exactamente 1 vez por tick de servidor.
     */
    public static void tickSquads(ServerLevel level) {
        if (ACTIVE_SQUADS.isEmpty()) return;

        Iterator<Map.Entry<UUID, Squad>> it = ACTIVE_SQUADS.entrySet().iterator();
        while (it.hasNext()) {
            Squad squad = it.next().getValue();

            if (squad.memberUUIDs.isEmpty()) {
                it.remove();
                continue;
            }

            if (squad.dimension == null || squad.dimension.equals(level.dimension())) {
                squad.tick(level);
            }
        }
    }
}