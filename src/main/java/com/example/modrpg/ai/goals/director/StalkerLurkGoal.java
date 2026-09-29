package com.example.modrpg.ai.goals.director;

import com.example.modrpg.ai.EnemyArchetype;
import com.example.modrpg.ai.SquadCoordinator;
import com.example.modrpg.ai.director.DirectorState;
import com.example.modrpg.ai.director.DirectorWhisper;
import com.example.modrpg.ai.director.MacroDirectorManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Meta de Acecho (Inspirada en Alien: Isolation):
 * Durante la fase BUILD_UP, los monstruos siguen las pistas del Director por zonas oscuras.
 * Si el jugador se da la vuelta y los mira fijamente por 1.5s, huyen a esconderse detrás de coberturas.
 */
public class StalkerLurkGoal extends Goal {

    private final Mob mob;
    private final EnemyArchetype archetype;
    private int staringTicks = 0;
    private int retreatTicks = 0;

    public StalkerLurkGoal(Mob mob, EnemyArchetype archetype) {
        this.mob = mob;
        this.archetype = archetype;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() instanceof ServerPlayer player) {
            var pacing = MacroDirectorManager.getPacingData(player.getUUID());
            return pacing.getState() == DirectorState.BUILD_UP;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() || retreatTicks > 0;
    }

    @Override
    public void start() {
        this.staringTicks = 0;
        this.retreatTicks = 0;
    }

    @Override
    public void tick() {
        if (!(mob.getTarget() instanceof ServerPlayer player)) return;

        // Si está en maniobra de escape tras ser descubierto
        if (retreatTicks > 0) {
            retreatTicks--;
            return;
        }

        // =========================================================================
        // 1. REGLA DE LA MIRADA FIJA (El monstruo se intimida si lo miras de frente)
        // =========================================================================
        Vec3 playerLook = player.getLookAngle().normalize();
        Vec3 toMob = mob.getEyePosition().subtract(player.getEyePosition()).normalize();
        double dotProduct = playerLook.dot(toMob);

        boolean isPlayerLookingDirectly = dotProduct > 0.70 && mob.getSensing().hasLineOfSight(player);

        if (isPlayerLookingDirectly) {
            staringTicks++;

            // Si el jugador lo mantiene en la mira durante 1.5s (30 ticks): HUYE A COBERTURA
            if (staringTicks >= 30) {
                staringTicks = 0;
                retreatTicks = 60; // 3 segundos de retirada hacia la oscuridad

                SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
                if (squad != null) {
                    Vec3 coverPos = squad.getCoverManager().findAndClaimCover(mob, player, 16.0);
                    if (coverPos != null) {
                        mob.getNavigation().moveTo(coverPos.x, coverPos.y, coverPos.z, 1.30);
                    }
                } else {
                    // Retroceso en vector inverso al jugador
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(8.0);
                    mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.25);
                }

                if (mob.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + 0.5, mob.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
                }
                return;
            }
        } else {
            staringTicks = Math.max(0, staringTicks - 1);
        }

        // =========================================================================
        // 2. SEGUIMIENTO DE SUSURROS DEL DIRECTOR (Waypoints indirectos)
        // =========================================================================
        DirectorWhisper whisper = MacroDirectorManager.getWhisperForPlayer(player.getUUID());
        if (whisper != null) {
            Vec3 targetWaypoint = whisper.waypoint();
            double distSq = mob.position().distanceToSqr(targetWaypoint);

            if (distSq > 4.0) {
                mob.getNavigation().moveTo(targetWaypoint.x, targetWaypoint.y, targetWaypoint.z, 0.95);
            } else {
                mob.getNavigation().stop();
                mob.getLookControl().setLookAt(player, 20.0f, 20.0f);
            }
        }
    }
}