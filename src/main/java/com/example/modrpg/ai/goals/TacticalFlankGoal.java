package com.example.modrpg.ai.goals;

import com.example.modrpg.ai.EnemyArchetype;
import com.example.modrpg.ai.SquadCoordinator;
import com.example.modrpg.ai.feedback.SquadBarkManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Maniobra de Flanqueo: El hostigador ligero rodea la posición del jugador calculando
 * vectores perpendiculares al eje frontal. Si logra situarse en el punto ciego del jugador
 * (> 75° respecto a su mirada), desata una emboscada con daño amplificado.
 */
public class TacticalFlankGoal extends Goal {

    private final Mob mob;
    private final EnemyArchetype archetype;
    private Vec3 targetFlankPos = null;
    private int flankTimer = 0;

    public TacticalFlankGoal(Mob mob, EnemyArchetype archetype) {
        this.mob = mob;
        this.archetype = archetype;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (archetype.isAggressiveRush()) return false;

        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        if (squad == null) return false;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        double distSq = mob.distanceToSqr(target);
        return distSq > 36.0 && distSq < 400.0;
    }

    @Override
    public void start() {
        this.flankTimer = 0;
        recalculateFlankVector();

        if (mob.level() instanceof ServerLevel level) {
            SquadBarkManager.triggerBark(mob, SquadBarkManager.BarkType.FLANKING, level);
        }
    }

    private void recalculateFlankVector() {
        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        LivingEntity target = mob.getTarget();
        if (squad != null && target != null) {
            this.targetFlankPos = squad.getFlankingPosition(mob, target);
        }
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        flankTimer++;
        if (flankTimer % 20 == 0) {
            recalculateFlankVector();
        }

        if (targetFlankPos != null) {
            mob.getNavigation().moveTo(targetFlankPos.x, targetFlankPos.y, targetFlankPos.z, 1.20);
        }

        // Detección de Punto Ciego: ¿El jugador está mirando hacia otro lado?
        Vec3 playerLook = target.getLookAngle().normalize();
        Vec3 toMob = mob.position().subtract(target.position()).normalize();
        double dotProduct = playerLook.dot(toMob);

        // Si dotProduct < 0.25, el jugador le da la espalda en un ángulo > 75°
        if (dotProduct < 0.25 && mob.distanceToSqr(target) < 64.0 && mob.level() instanceof ServerLevel level) {
            // El flanqueador aprovecha el punto ciego para emboscar
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, mob.getX(), mob.getEyeY(), mob.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0f, 1.4f);
        }
    }
}