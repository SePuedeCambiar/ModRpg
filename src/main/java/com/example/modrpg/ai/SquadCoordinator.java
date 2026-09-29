package com.example.modrpg.ai;

import com.example.modrpg.ai.feedback.SquadBarkManager;
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

public class SquadCoordinator {

    public static class Squad {
        public final UUID squadId = UUID.randomUUID();
        public UUID leaderUUID = null;
        public final Set<UUID> memberUUIDs = Collections.newSetFromMap(new ConcurrentHashMap<>());

        // Piscina de tokens concurrentes
        private final List<SquadTacticalToken> tokenPool = new CopyOnWriteArrayList<>();

        // Señal de Peeling
        private UUID peelRequestedBy = null;
        private int peelTimer = 0;

        // Máquina de Moral (Pánico tras la muerte del líder)
        private int moraleBreakTimer = 0;

        public Squad() {
            rebuildTokenPool();
        }

        /**
         * Reconfigura la capacidad de tokens según la cantidad de miembros vivos.
         * Regla: 1 Token de Rescate (Peel), 1 Token de Supresión, y 1 Token de Ataque Primario por cada 3 miembros.
         */
        public void rebuildTokenPool() {
            tokenPool.clear();

            // 1 Token de Peeling (Prioridad 100)
            tokenPool.add(new SquadTacticalToken(SquadTacticalToken.TokenType.PEEL));

            // 1 Token de Supresión (Prioridad 10)
            tokenPool.add(new SquadTacticalToken(SquadTacticalToken.TokenType.SUPPRESSION));

            // Tokens de Ataque Primario escalados (1 cada 3 miembros, mínimo 1)
            int primaryCount = Math.max(1, memberUUIDs.size() / 3);
            for (int i = 0; i < primaryCount; i++) {
                tokenPool.add(new SquadTacticalToken(SquadTacticalToken.TokenType.PRIMARY_ATTACK));
            }
        }

        public boolean isInPanic() {
            return moraleBreakTimer > 0;
        }

        /**
         * Solicita un token táctico específico. Si el escuadrón está en pánico,
         * todas las peticiones ofensivas son rechazadas.
         */
        public boolean requestToken(Mob mob, SquadTacticalToken.TokenType type, int leaseTicks) {
            if (mob == null || !mob.isAlive()) return false;

            // En pánico por muerte del líder, se prohíbe el ataque coordinado
            if (isInPanic() && type != SquadTacticalToken.TokenType.PEEL) {
                return false;
            }

            // Busca un token disponible del tipo solicitado
            for (SquadTacticalToken token : tokenPool) {
                if (token.getType() == type && (token.isAvailable() || Objects.equals(token.getHolderUUID(), mob.getUUID()))) {
                    return token.claim(mob, leaseTicks);
                }
            }
            return false;
        }

        /**
         * Libera un token específico que poseía el mob.
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
         * Revoca INMEDIATAMENTE todos los tokens que posea este mob.
         * Se ejecuta al morir, sufrir aturdimiento (Rompe-Postura) o caer en pánico.
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

        public void tick(ServerLevel level) {
            if (peelTimer > 0) peelTimer--;
            else peelRequestedBy = null;

            if (moraleBreakTimer > 0) moraleBreakTimer--;

            // Heartbeat de cada token: verifica que sus portadores sigan activos y sanos
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
            this.moraleBreakTimer = 60; // 3 segundos de ruptura de moral

            // Liberar forzosamente todos los tokens del escuadrón
            for (SquadTacticalToken token : tokenPool) {
                token.release();
            }

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
            ACTIVE_SQUADS.put(squad.squadId, squad);
        }

        squad.memberUUIDs.add(mob.getUUID());
        MOB_SQUAD_MAP.put(mob.getUUID(), squad);
        squad.rebuildTokenPool(); // Recalcula la piscina de tokens con el nuevo tamaño
    }

    public static void onMobDeath(Mob deadMob) {
        SquadBarkManager.clearMobMemory(deadMob.getUUID());

        Squad squad = MOB_SQUAD_MAP.remove(deadMob.getUUID());
        if (squad != null) {
            squad.forceReleaseAllTokens(deadMob.getUUID());
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

    public static void tickSquads(ServerLevel level) {
        for (Squad squad : ACTIVE_SQUADS.values()) {
            squad.tick(level);
        }
    }
}