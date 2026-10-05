package com.example.modrpg.ai.goals;

import com.example.modrpg.ai.EnemyArchetype;
import com.example.modrpg.ai.SquadCoordinator;
import com.example.modrpg.ai.feedback.SquadBarkManager;
import com.example.modrpg.ai.squad.SquadTacticalToken;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Maniobra de Supresión F.E.A.R.:
 * Mientras la vanguardia acorta distancias, el tirador de apoyo adquiere el SuppressionToken
 * para saturar la posición del jugador con ráfagas rápidas de 3 disparos,
 * obligándolo a resguardarse detrás de muros.
 */
public class TacticalBoundingGoal extends Goal {

    private final Mob mob;
    private final EnemyArchetype archetype;

    // SPRINT 3 FIX: En lugar de un contador que nunca decrementaba en idle, usamos GameTime
    private long lastSuppressionGameTime = -1000L;
    private int burstShotsRemaining = 0;
    private int burstDelayTicks = 0;

    public TacticalBoundingGoal(Mob mob, EnemyArchetype archetype) {
        this.mob = mob;
        this.archetype = archetype;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (archetype.isAggressiveRush() || mob.isPassenger()) return false;

        // No suprimir si el mob está aturdido
        if (mob.getTags().contains("modrpg_staggered")) return false;

        // Cooldown de 6 segundos (120 ticks) entre ráfagas completas
        long currentTick = mob.level().getGameTime();
        if (currentTick - lastSuppressionGameTime < 120L) return false;

        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        if (squad == null) return false;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || !mob.getSensing().hasLineOfSight(target)) {
            return false;
        }

        // Rango táctico de supresión: entre 6 y 18 bloques
        double distSq = mob.distanceToSqr(target);
        if (distSq < 36.0 || distSq > 324.0) return false;

        // Solicitar el token de supresión al escuadrón (lease de 4 segundos)
        return squad.requestToken(mob, SquadTacticalToken.TokenType.SUPPRESSION, 80);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        return burstShotsRemaining > 0
                && target != null
                && target.isAlive()
                && !mob.getTags().contains("modrpg_staggered")
                && mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.burstShotsRemaining = 3; // Ráfaga de 3 disparos
        this.burstDelayTicks = 4;     // Tiempo de apuntado inicial antes del primer tiro

        if (mob.level() instanceof ServerLevel level) {
            SquadBarkManager.triggerBark(mob, SquadBarkManager.BarkType.SUPPRESSION_CALL, level);
        }
    }

    /**
     * SPRINT 3 FIX: Limpieza atómica del token y sellado de cooldown al finalizar o abortar la ráfaga.
     */
    @Override
    public void stop() {
        this.burstShotsRemaining = 0;
        this.burstDelayTicks = 0;
        this.lastSuppressionGameTime = mob.level().getGameTime();

        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        if (squad != null) {
            squad.releaseToken(mob, SquadTacticalToken.TokenType.SUPPRESSION);
        }
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null || burstShotsRemaining <= 0) return;

        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);

        if (burstDelayTicks > 0) {
            burstDelayTicks--;
            return;
        }

        if (mob.level() instanceof ServerLevel level) {
            // Disparar proyectil táctico de hostigamiento
            Vec3 toTarget = target.getEyePosition().subtract(mob.getEyePosition()).normalize();

            Arrow arrow = new Arrow(level, mob);
            // Disparo rápido con dispersión media para obligar a cubrirse
            arrow.shoot(toTarget.x, toTarget.y + 0.08, toTarget.z, 2.2f, 5.0f);
            arrow.setBaseDamage(3.0);
            // Evitar que el jugador farmee flechas infinitas del suelo
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;

            level.addFreshEntity(arrow);

            level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getEyeY(), mob.getZ(), 5, 0.1, 0.1, 0.1, 0.02);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.0f, 1.6f);

            burstShotsRemaining--;
            burstDelayTicks = 8; // Intervalo de 8 ticks (0.4s) entre cada flecha de la ráfaga
        }
    }
}