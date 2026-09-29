package com.example.modrpg.ai;

import com.example.modrpg.ai.feedback.SquadBarkManager;
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

public class SquadCoordinator {

    public static class Squad {
        public final UUID squadId = UUID.randomUUID();
        public UUID leaderUUID = null;
        public final Set<UUID> memberUUIDs = Collections.newSetFromMap(new ConcurrentHashMap<>());

        private UUID castingTokenHolder = null;
        private int castingTokenTimer = 0;

        private UUID peelRequestedBy = null;
        private int peelTimer = 0;

        public boolean requestCastingToken(Mob mob) {
            if (castingTokenHolder == null || castingTokenHolder.equals(mob.getUUID()) || castingTokenTimer <= 0) {
                this.castingTokenHolder = mob.getUUID();
                this.castingTokenTimer = 40;
                return true;
            }
            return false;
        }

        public void releaseCastingToken(Mob mob) {
            if (Objects.equals(this.castingTokenHolder, mob.getUUID())) {
                this.castingTokenHolder = null;
                this.castingTokenTimer = 0;
            }
        }

        public void requestPeel(Mob caller, LivingEntity threat) {
            this.peelRequestedBy = caller.getUUID();
            this.peelTimer = 60;
        }

        public boolean isPeelRequested() {
            return peelRequestedBy != null && peelTimer > 0;
        }

        public void tick() {
            if (castingTokenTimer > 0) castingTokenTimer--;
            else castingTokenHolder = null;

            if (peelTimer > 0) peelTimer--;
            else peelRequestedBy = null;
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
            // Se utiliza el gestor táctico para transmitir la caída de moral
            SquadBarkManager.triggerBark(deadLeader, SquadBarkManager.BarkType.MORALE_BREAK, level);

            Vec3 deathPos = deadLeader.position();
            AABB area = new AABB(deathPos.x - 20, deathPos.y - 8, deathPos.z - 20, deathPos.x + 20, deathPos.y + 8, deathPos.z + 20);
            List<Mob> nearby = level.getEntitiesOfClass(Mob.class, area, m -> memberUUIDs.contains(m.getUUID()) && m.isAlive());

            for (Mob survivor : nearby) {
                survivor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                survivor.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
                survivor.getNavigation().stop();
                level.sendParticles(ParticleTypes.SMOKE, survivor.getX(), survivor.getEyeY() + 0.3, survivor.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    private static final Map<UUID, Squad> ACTIVE_SQUADS = new ConcurrentHashMap<>();
    private static final Map<UUID, Squad> MOB_SQUAD_MAP = new ConcurrentHashMap<>();

    public static Squad getSquadFor(Mob mob) {
        return MOB_SQUAD_MAP.get(mob.getUUID());
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
    }

    public static void onMobDeath(Mob deadMob) {
        SquadBarkManager.clearMobMemory(deadMob.getUUID());

        Squad squad = MOB_SQUAD_MAP.remove(deadMob.getUUID());
        if (squad != null) {
            squad.memberUUIDs.remove(deadMob.getUUID());
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

    public static void tickSquads() {
        for (Squad squad : ACTIVE_SQUADS.values()) {
            squad.tick();
        }
    }
}