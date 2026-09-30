package com.example.modrpg.ai;

import com.example.modrpg.ai.director.MacroDirectorManager;
import com.example.modrpg.ai.feedback.SquadBarkManager;
import com.example.modrpg.ai.squad.SquadCoverManager;
import com.example.modrpg.ai.squad.SquadTacticalToken;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cerebro Táctico Central del Escuadrón.
 * Unifica la concurrencia de tokens (F.E.A.R.), el grafo de coberturas espaciales
 * y la comunicación bidireccional con el Macro-Director de Pacing (Alien: Isolation).
 */
public class SquadCoordinator {

    public static class Squad {
        public final UUID squadId = UUID.randomUUID();
        public UUID leaderUUID = null;
        public ResourceKey<Level> dimension = null; // SPRINT 1: Aislamiento por dimensión
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
         * SPRINT 1 FIX: Reconstrucción no destructiva.
         * Preserva los tokens que estén en uso activo (ACTIVE) para no desincronizar
         * a casters o tiradores que estén canalizando en ese instante.
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
                // Solo remover tokens primarios que estén IDLE
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
            this.peelRequestedBy = caller.getUUID();
            this.peelTimer = 60; // 3 segundos (a 1 decremento por tick)
        }

        public boolean isPeelRequested() {
            return peelRequestedBy != null && peelTimer > 0;
        }

        public void tick(ServerLevel level) {
            if (peelTimer > 0) peelTimer--;
            else peelRequestedBy = null;

            if (moraleBreakTimer > 0) moraleBreakTimer--;

            // 1. Recopilar miembros vivos válidos en este nivel
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
                        // Mob muerto que no fue retirado: limpiar
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

        public Vec3 getFlankingPosition(Mob mob, LivingEntity target) {
            Vec3 targetPos = target.position();
            Vec3 mobPos = mob.position();

            Vec3 forward = targetPos.subtract(mobPos).normalize();
            if (forward.lengthSqr() < 1e-4) return null;

            int side = (mob.hashCode() % 2 == 0) ? 1 : -1;
            Vec3 perpendicular = new Vec3(-forward.z * side, 0, forward.x * side).normalize();

            return targetPos.subtract(forward.scale(10.0)).add(perpendicular.scale(6.0));
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

    /**
     * Muerte confirmada en combate (LivingDeathEvent).
     * Si era el líder, desata la ruptura de moral y pánico en el escuadrón.
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
     * SPRINT 1 FIX: Despawn natural, descarga de chunk o salida del nivel (EntityLeaveLevelEvent).
     * Purga la entidad de memoria silenciosamente sin activar la fanfarria de "Líder Caído".
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
     * SPRINT 1 FIX: Aislamiento por dimensión y eliminación segura de escuadrones vacíos.
     */
    public static void tickSquads(ServerLevel level) {
        if (ACTIVE_SQUADS.isEmpty()) return;

        Iterator<Map.Entry<UUID, Squad>> it = ACTIVE_SQUADS.entrySet().iterator();
        while (it.hasNext()) {
            Squad squad = it.next().getValue();

            // Descartar escuadrones que quedaron vacíos
            if (squad.memberUUIDs.isEmpty()) {
                it.remove();
                continue;
            }

            // Filtrar solo los escuadrones que correspondan a la dimensión actual
            if (squad.dimension == null || squad.dimension.equals(level.dimension())) {
                squad.tick(level);
            }
        }
    }
}