package com.example.modrpg.ai;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

        // Sistema de Tokens estilo F.E.A.R.
        private UUID castingTokenHolder = null;
        private int castingTokenTimer = 0;

        // Maniobra de Peeling (Rescate)
        private UUID peelRequestedBy = null;
        private int peelTimer = 0;

        public boolean requestCastingToken(Mob mob) {
            if (castingTokenHolder == null || castingTokenHolder.equals(mob.getUUID()) || castingTokenTimer <= 0) {
                this.castingTokenHolder = mob.getUUID();
                this.castingTokenTimer = 40; // 2 segundos de posesión del token
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
            this.peelTimer = 60; // 3 segundos para que la vanguardia intercepte

            // Señal audible/visual: El caster pide auxilio
            if (caller.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, caller.getX(), caller.getEyeY() + 0.5, caller.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
            }
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

        /**
         * F.E.A.R. Crossfire: Calcula una posición lateral a 60°-90° del eje Jugador-Tanque
         */
        public Vec3 getFlankingPosition(Mob mob, LivingEntity target) {
            Vec3 targetPos = target.position();
            Vec3 mobPos = mob.position();

            Vec3 forward = targetPos.subtract(mobPos).normalize();
            if (forward.lengthSqr() < 1e-4) return null;

            // Vector perpendicular en el plano horizontal (90 grados)
            int side = (mob.hashCode() % 2 == 0) ? 1 : -1;
            Vec3 perpendicular = new Vec3(-forward.z * side, 0, forward.x * side).normalize();

            // Punto objetivo: 10 bloques de distancia respecto al jugador, desplazado lateralmente 6 bloques
            return targetPos.subtract(forward.scale(10.0)).add(perpendicular.scale(6.0));
        }

        public void triggerMoraleBreak(ServerLevel level, Vec3 deathPos) {
            // CORREGIDO: Se invoca .get() sobre el Holder.Reference para obtener la instancia SoundEvent
            level.playSound(null, deathPos.x, deathPos.y, deathPos.z, SoundEvents.RAID_HORN.get(), SoundSource.HOSTILE, 1.2f, 0.7f);

            AABB area = new AABB(deathPos.x - 20, deathPos.y - 8, deathPos.z - 20, deathPos.x + 20, deathPos.y + 8, deathPos.z + 20);
            List<Mob> nearby = level.getEntitiesOfClass(Mob.class, area, m -> memberUUIDs.contains(m.getUUID()) && m.isAlive());

            for (Mob survivor : nearby) {
                // Aturdimiento por pánico durante 3 segundos
                survivor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                survivor.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
                survivor.getNavigation().stop();
                level.sendParticles(ParticleTypes.SMOKE, survivor.getX(), survivor.getEyeY() + 0.3, survivor.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
            }

            // Avisar a jugadores cercanos
            List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, area);
            for (ServerPlayer sp : players) {
                sp.displayClientMessage(Component.literal("§c§l💥 ¡LÍDER ELIMINADO! §7El escuadrón sufre ruptura de moral."), true);
            }
        }
    }

    private static final Map<UUID, Squad> ACTIVE_SQUADS = new ConcurrentHashMap<>();
    private static final Map<UUID, Squad> MOB_SQUAD_MAP = new ConcurrentHashMap<>();

    public static Squad getSquadFor(Mob mob) {
        return MOB_SQUAD_MAP.get(mob.getUUID());
    }

    /**
     * Vincula al mob a un escuadrón cercano (radio 16m) o funda un nuevo escuadrón táctico
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
    }

    public static void onMobDeath(Mob deadMob) {
        Squad squad = MOB_SQUAD_MAP.remove(deadMob.getUUID());
        if (squad != null) {
            squad.memberUUIDs.remove(deadMob.getUUID());
            if (deadMob.level() instanceof ServerLevel level) {
                // Si el mob muerto era el Líder Comandante, romper la moral del grupo
                if (Objects.equals(squad.leaderUUID, deadMob.getUUID())) {
                    squad.triggerMoraleBreak(level, deadMob.position());
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