package com.example.modrpg.ai.goals;

import com.example.modrpg.ai.EnemyArchetype;
import com.example.modrpg.ai.SquadCoordinator;
import com.example.modrpg.ai.feedback.SquadBarkManager;
import com.example.modrpg.ai.squad.SquadTacticalToken;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Maniobra de Vanguardia: Si un aliado frágil a distancia pide auxilio,
 * el tanque interrumpe su labor, reclama el PeelToken y embiste furiosamente
 * para interponerse físicamente entre el agresor y el aliado.
 */
public class TacticalPeelGoal extends Goal {

    private final Mob mob;
    private final EnemyArchetype archetype;
    private int interceptTicks = 0;

    public TacticalPeelGoal(Mob mob, EnemyArchetype archetype) {
        this.mob = mob;
        this.archetype = archetype;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!archetype.isAggressiveRush()) return false;

        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        if (squad == null || !squad.isPeelRequested()) return false;

        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        LivingEntity target = mob.getTarget();
        return squad != null && squad.isPeelRequested() && target != null && target.isAlive() && interceptTicks < 100;
    }

    @Override
    public void start() {
        this.interceptTicks = 0;
        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        if (squad != null) {
            // Reclamar el token de prioridad absoluta (100)
            squad.requestToken(mob, SquadTacticalToken.TokenType.PEEL, 80);
        }

        if (mob.level() instanceof ServerLevel level) {
            SquadBarkManager.triggerBark(mob, SquadBarkManager.BarkType.VANGUARD_INTERCEPT, level);
        }
    }

    @Override
    public void stop() {
        this.interceptTicks = 0;
        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        if (squad != null) {
            squad.releaseToken(mob, SquadTacticalToken.TokenType.PEEL);
        }
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        interceptTicks++;
        mob.getLookControl().setLookAt(target, 40.0f, 40.0f);

        // Sprint forzado a velocidad x1.45
        mob.getNavigation().moveTo(target, 1.45);

        double distSq = mob.distanceToSqr(target);

        // Choque frontal violento contra el jugador
        if (distSq <= 6.0) {
            mob.swing(InteractionHand.MAIN_HAND, true);
            mob.doHurtTarget(target);

            // Empuje sísmico que expulsa al jugador lejos del hechicero
            double dx = target.getX() - mob.getX();
            double dz = target.getZ() - mob.getZ();
            target.knockback(1.6, -dx, -dz);

            if (mob.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8f, 1.5f);
            }

            // Rescate completado
            SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
            if (squad != null) {
                squad.releaseToken(mob, SquadTacticalToken.TokenType.PEEL);
            }
            this.stop();
        }
    }
}