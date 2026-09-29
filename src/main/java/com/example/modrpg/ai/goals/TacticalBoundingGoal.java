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
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Maniobra de Supresión: Mientras la vanguardia acorta distancias, el tirador de apoyo
 * adquiere el SuppressionToken para saturar la posición del jugador con disparos rápidos,
 * obligándolo a resguardarse detrás de muros.
 */
public class TacticalBoundingGoal extends Goal {

    private final Mob mob;
    private final EnemyArchetype archetype;
    private int suppressionCooldown = 0;
    private int burstShotsRemaining = 0;

    public TacticalBoundingGoal(Mob mob, EnemyArchetype archetype) {
        this.mob = mob;
        this.archetype = archetype;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (archetype.isAggressiveRush()) return false;

        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        if (squad == null) return false;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || !mob.getSensing().hasLineOfSight(target)) return false;

        double distSq = mob.distanceToSqr(target);
        return distSq >= 36.0 && distSq <= 300.0 && suppressionCooldown <= 0;
    }

    @Override
    public void start() {
        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        if (squad != null && squad.requestToken(mob, SquadTacticalToken.TokenType.SUPPRESSION, 60)) {
            this.burstShotsRemaining = 3; // Ráfaga de 3 disparos de cobertura
            if (mob.level() instanceof ServerLevel level) {
                SquadBarkManager.triggerBark(mob, SquadBarkManager.BarkType.SUPPRESSION_CALL, level);
            }
        } else {
            this.suppressionCooldown = 40;
        }
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null || burstShotsRemaining <= 0) return;

        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);

        if (mob.tickCount % 8 == 0 && mob.level() instanceof ServerLevel level) {
            // Disparar proyectil ligero de hostigamiento
            Vec3 toTarget = target.getEyePosition().subtract(mob.getEyePosition()).normalize();

            Arrow arrow = new Arrow(level, mob);
            arrow.shoot(toTarget.x, toTarget.y + 0.1, toTarget.z, 2.0f, 6.0f); // Disparo con dispersión táctica
            arrow.setBaseDamage(3.0);
            level.addFreshEntity(arrow);

            level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getEyeY(), mob.getZ(), 5, 0.1, 0.1, 0.1, 0.02);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.0f, 1.6f);

            burstShotsRemaining--;
            if (burstShotsRemaining <= 0) {
                this.suppressionCooldown = 120; // 6 segundos antes de volver a suprimir
                SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
                if (squad != null) {
                    squad.releaseToken(mob, SquadTacticalToken.TokenType.SUPPRESSION);
                }
            }
        }
    }
}