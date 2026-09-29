package com.example.modrpg.ai;

import com.example.modrpg.ai.feedback.SquadBarkManager;
import com.example.modrpg.ai.squad.SquadCoverManager;
import com.example.modrpg.ai.squad.SquadTacticalToken;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
 * Cerebro Táctico de Escuadrón estilo F.E.A.R.
 * Coordina la concurrencia de ataques (Tokens), maniobras de rescate (Peeling),
 * flanqueos en cono, rupturas de moral por baja de líderes y grafo de coberturas espaciales.
 */
public class SquadCoordinator {

    public static class Squad {
        public final UUID squadId = UUID.randomUUID();
        public UUID leaderUUID = null;
        public final Set<UUID> memberUUIDs = Collections.newSetFromMap(new ConcurrentHashMap<>());

        // SPRINT 3: Grafo y memoria de coberturas espaciales del escuadrón
        private final SquadCoverManager coverManager = new SquadCoverManager();

        // SPRINT 2: Piscina de tokens concurrentes clasificados
        private final List<SquadTacticalToken> tokenPool = new CopyOnWriteArrayList<>();

        // Maniobra de Peeling (Rescate Reactivo)
        private UUID peelRequestedBy = null;
        private int peelTimer = 0;

        // Máquina de Moral (Pánico tras la muerte del líder)
        private int moraleBreakTimer = 0;

        public Squad() {
            rebuildTokenPool();
        }

        public SquadCoverManager getCoverManager() {
            return coverManager;
        }

        /**
         * Reconstruye dinámicamente la piscina de tokens según los miembros vivos.
         * Regla: 1 Token de Rescate (Peel), 1 Token de Supresión y 1 Token Primario cada 3 miembros.
         */
        public void rebuildTokenPool() {
            tokenPool.clear();

            // 1 Token de Rescate (Prioridad 100)
            tokenPool.add(new SquadTacticalToken(SquadTacticalToken.TokenType.PEEL));

            // 1 Token de Supresión / Hostigamiento (Prioridad 10)
            tokenPool.add(new SquadTacticalToken(SquadTacticalToken.TokenType.SUPPRESSION));

            // Tokens de Ataque Primario / Pesado (1 cada 3 miembros, mínimo 1)
            int primaryCount = Math.max(1, memberUUIDs.size() / 3);
            for (int i = 0; i < primaryCount; i++) {
                tokenPool.add(new SquadTacticalToken(SquadTacticalToken.TokenType.PRIMARY_ATTACK));
            }
        }

        public boolean isInPanic() {
            return moraleBreakTimer > 0;
        }

        /**
         * Solicita un token táctico específico. Si el grupo está en pánico por la muerte del líder,
         * se deniegan las peticiones ofensivas.
         */
        public boolean requestToken(Mob mob, SquadTacticalToken.TokenType type, int leaseTicks) {
            if (mob == null || !mob.isAlive()) return false;

            // Durante la ruptura de moral, los ataques ofensivos están bloqueados
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

        /**
         * Libera formalmente un token cuando la acción concluye exitosamente.
         */
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
         * Revoca INMEDIATAMENTE todos los tokens del portador.
         * Se invoca en caso de muerte, aturdimiento (Rompe-Postura) o caída en pánico.
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
            this.peelTimer = 60; // 3 segundos de ventana de rescate
        }

        public boolean isPeelRequested() {
            return peelRequestedBy != null && peelTimer > 0;
        }

        public UUID getPeelRequestedBy() {
            return peelRequestedBy;
        }

        /**
         * Actualización en cada ciclo del escuadrón.
         */
        public void tick(ServerLevel level) {
            if (peelTimer > 0) peelTimer--;
            else peelRequestedBy = null;

            if (moraleBreakTimer > 0) moraleBreakTimer--;

            // 1. Identificar miembros activos y objetivo compartido
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

            // 2. SPRINT 3: Actualizar el grafo de coberturas espaciales (Time-Sliced a 10 ticks)
            coverManager.tick(level, sharedTarget, aliveMembers);

            // 3. SPRINT 2: Heartbeat de tokens (auto-revocación si el portador muere o se aturde)
            for (SquadTacticalToken token : tokenPool) {
                if (token.getState() == SquadTacticalToken.TokenState.ACTIVE) {
                    Mob holder = (token.getHolderUUID() != null) ? (Mob) level.getEntity(token.getHolderUUID()) : null;
                    token.tick(holder);
                }
            }
        }

        /**
         * F.E.A.R. Crossfire: Calcula una posición lateral a 60°-90° del eje Jugador-Tanque.
         */
        public Vec3 getFlankingPosition(Mob mob, LivingEntity target) {
            Vec3 targetPos = target.position();
            Vec3 mobPos = mob.position();

            Vec3 forward = targetPos.subtract(mobPos).normalize();
            if (forward.lengthSqr() < 1e-4) return null;

            // Alternancia determinista de lado según el UUID del mob
            int side = (mob.getUUID().hashCode() % 2 == 0) ? 1 : -1;
            Vec3 perpendicular = new Vec3(-forward.z * side, 0, forward.x * side).normalize();

            // Vector a 10 bloques de distancia con 6 bloques de separación lateral
            return targetPos.subtract(forward.scale(10.0)).add(perpendicular.scale(6.0));
        }

        /**
         * SPRINT 1 & 2: Desencadena la ruptura de moral al morir el líder.
         */
        public void triggerMoraleBreak(ServerLevel level, Mob deadLeader) {
            this.moraleBreakTimer = 60; // 3 segundos de pánico

            // Se revocan todos los tokens ofensivos
            for (SquadTacticalToken token : tokenPool) {
                token.release();
            }

            // Aviso táctico sonoro y en Action Bar
            SquadBarkManager.triggerBark(deadLeader, SquadBarkManager.BarkType.MORALE_BREAK, level);

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

    // =========================================================================
    // REGISTRO GLOBAL DE ESCUADRONES EN MEMORIA
    // =========================================================================
    private static final Map<UUID, Squad> ACTIVE_SQUADS = new ConcurrentHashMap<>();
    private static final Map<UUID, Squad> MOB_SQUAD_MAP = new ConcurrentHashMap<>();

    public static Squad getSquadFor(Mob mob) {
        return (mob != null) ? MOB_SQUAD_MAP.get(mob.getUUID()) : null;
    }

    /**
     * Vincula al mob a un escuadrón cercano (radio 16m) o funda uno nuevo.
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
     * Limpieza determinista al morir un integrante: libera tokens, limpia coberturas y verifica si era líder.
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
     * Bucle central de actualización de escuadrones ejecutado desde ModEvents.
     */
    public static void tickSquads(ServerLevel level) {
        for (Squad squad : ACTIVE_SQUADS.values()) {
            squad.tick(level);
        }
    }
}