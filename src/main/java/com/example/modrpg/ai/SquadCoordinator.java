package com.example.modrpg.ai;

import com.example.modrpg.ai.director.MacroDirectorManager;
import com.example.modrpg.ai.feedback.SquadBarkManager;
import com.example.modrpg.ai.squad.SquadCoverManager;
import com.example.modrpg.ai.squad.SquadTacticalToken;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
        public final Set<UUID> memberUUIDs = Collections.newSetFromMap(new ConcurrentHashMap<>());

        // SPRINT 3: Gestor de coberturas espaciales del escuadrón
        private final SquadCoverManager coverManager = new SquadCoverManager();

        // SPRINT 2: Piscina de tokens tácticos con Lease Heartbeat
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
         * Reconfigura dinámicamente los tokens según el número de miembros:
         * - 1 Token de Rescate (Peel) prioritario.
         * - 1 Token de Supresión (Tirador).
         * - 1 Token de Ataque Primario por cada 3 miembros vivos (mínimo 1).
         */
        public void rebuildTokenPool() {
            tokenPool.clear();
            tokenPool.add(new SquadTacticalToken(SquadTacticalToken.TokenType.PEEL));
            tokenPool.add(new SquadTacticalToken(SquadTacticalToken.TokenType.SUPPRESSION));

            int primaryCount = Math.max(1, memberUUIDs.size() / 3);
            for (int i = 0; i < primaryCount; i++) {
                tokenPool.add(new SquadTacticalToken(SquadTacticalToken.TokenType.PRIMARY_ATTACK));
            }
        }

        public boolean isInPanic() {
            return moraleBreakTimer > 0;
        }

        /**
         * Solicita formalmente permiso para atacar. Si el escuadrón está en pánico,
         * todas las peticiones ofensivas son denegadas.
         */
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

        /**
         * Revoca INMEDIATAMENTE todos los tokens que posea este mob.
         * Se ejecuta al morir o sufrir rotura de postura (Stagger).
         */
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
            this.peelTimer = 60; // 3 segundos para que la vanguardia responda
        }

        public boolean isPeelRequested() {
            return peelRequestedBy != null && peelTimer > 0;
        }

        /**
         * Ciclo de actualización del escuadrón:
         * - Actualiza temporizadores.
         * - Tiquea el gestor de coberturas espaciales.
         * - Ejecuta el heartbeat de cada token para evitar softlocks.
         */
        public void tick(ServerLevel level) {
            if (peelTimer > 0) peelTimer--;
            else peelRequestedBy = null;

            if (moraleBreakTimer > 0) moraleBreakTimer--;

            // 1. Recopilar miembros vivos y objetivo común
            List<Mob> aliveMembers = new ArrayList<>();
            LivingEntity sharedTarget = null;

            for (UUID memberId : memberUUIDs) {
                if (level.getEntity(memberId) instanceof Mob mob && mob.isAlive()) {
                    aliveMembers.add(mob);
                    if (sharedTarget == null && mob.getTarget() != null && mob.getTarget().isAlive()) {
                        sharedTarget = mob.getTarget();
                    }
                }
            }

            // 2. Tick de coberturas (Time-Sliced)
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
         * Calcula una posición de flanqueo a 60°-90° del eje Jugador-Tanque.
         */
        public Vec3 getFlankingPosition(Mob mob, LivingEntity target) {
            Vec3 targetPos = target.position();
            Vec3 mobPos = mob.position();

            Vec3 forward = targetPos.subtract(mobPos).normalize();
            if (forward.lengthSqr() < 1e-4) return null;

            // Vector perpendicular en el plano horizontal (90 grados)
            int side = (mob.hashCode() % 2 == 0) ? 1 : -1;
            Vec3 perpendicular = new Vec3(-forward.z * side, 0, forward.x * side).normalize();

            // 10 bloques detrás del jugador, desplazado 6 bloques lateralmente
            return targetPos.subtract(forward.scale(10.0)).add(perpendicular.scale(6.0));
        }

        /**
         * Ejecuta la ruptura de moral tras la muerte del líder del escuadrón.
         */
        public void triggerMoraleBreak(ServerLevel level, Mob deadLeader) {
            this.moraleBreakTimer = 60; // 3 segundos de pánico inicial

            // Liberar forzosamente todos los tokens
            for (SquadTacticalToken token : tokenPool) {
                token.release();
            }

            // SPRINT 1: Bark sonoro de derrota
            SquadBarkManager.triggerBark(deadLeader, SquadBarkManager.BarkType.MORALE_BREAK, level);

            // SPRINT 7: Notificar al Director para forzar 80 segundos de RESPIRO al jugador
            AABB searchPlayer = deadLeader.getBoundingBox().inflate(32.0);
            List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class, searchPlayer);
            for (ServerPlayer player : nearbyPlayers) {
                MacroDirectorManager.forceTriggerReprieve(player.getUUID(), 1600);
                player.displayClientMessage(
                        Component.literal("§a§l🕊 [RESPIRO CONCEDIDO] §7El escuadrón retrocede. Tienes 80s de tregua."),
                        true
                );
            }

            // Desorientación física de los sobrevivientes
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

    /**
     * Asigna al mob a un escuadrón cercano en 16 metros o funda uno nuevo.
     */
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
            ACTIVE_SQUADS.put(squad.squadId, squad);
        }

        squad.memberUUIDs.add(mob.getUUID());
        MOB_SQUAD_MAP.put(mob.getUUID(), squad);
        squad.rebuildTokenPool();
    }

    /**
     * Limpieza atómica cuando cualquier mob hostil muere.
     */
    public static void onMobDeath(Mob deadMob) {
        SquadBarkManager.clearMobMemory(deadMob.getUUID());

        Squad squad = MOB_SQUAD_MAP.remove(deadMob.getUUID());
        if (squad != null) {
            squad.forceReleaseAllTokens(deadMob.getUUID());
            squad.getCoverManager().releaseCover(deadMob.getUUID());
            squad.memberUUIDs.remove(deadMob.getUUID());
            squad.rebuildTokenPool();

            if (deadMob.level() instanceof ServerLevel level) {
                if (Objects.equals(squad.leaderUUID, deadMob.getUUID())) {
                    squad.triggerMoraleBreak(level, deadMob);
                }
            }
            if (squad.memberUUIDs.isEmpty()) {
                ACTIVE_SQUADS.remove(squad.squadId);
            }
        }
    }

    /**
     * Tick maestro ejecutado desde ModEvents cada 5 ticks.
     */
    public static void tickSquads(ServerLevel level) {
        for (Squad squad : ACTIVE_SQUADS.values()) {
            squad.tick(level);
        }
    }
}