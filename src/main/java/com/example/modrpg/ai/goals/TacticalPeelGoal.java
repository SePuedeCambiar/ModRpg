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

import java.util.EnumSet;

/**
 * Maniobra de Vanguardia (Peeling F.E.A.R.):
 * Si un aliado frágil a distancia pide auxilio, un único tanque de vanguardia interrumpe
 * su labor, adquiere el PeelToken de forma atómica y embiste para repeler la amenaza.
 *
 * - SPRINT 1 FIX (A1): requiresUpdateEveryTick() = true.
 * - SPRINT 3 FIX (B3): Rescate operado 100% mediante token PEEL.
 * - SPRINT 3 FIX (A3): Throttling de recálculo de rutas a 10 ticks para proteger los TPS.
 */
public class TacticalPeelGoal extends Goal {

    private final Mob mob;
    private final EnemyArchetype archetype;
    private int interceptTicks = 0;
    private boolean hasInterrupted = false;

    // A3 FIX: Throttling de recálculo de pathfinding
    private int repathDelay = 0;

    public TacticalPeelGoal(Mob mob, EnemyArchetype archetype) {
        this.mob = mob;
        this.archetype = archetype;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (mob == null) return false;

        // Solo tanques frontales libres pueden rescatar aliados
        if (archetype == null || !archetype.isAggressiveRush() || mob.isPassenger()) {
            return false;
        }

        // No actuar si está aturdido por rompe-postura
        if (mob.getTags().contains("modrpg_staggered")) {
            return false;
        }

        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        if (squad == null || !squad.isPeelRequested()) {
            return false;
        }

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }

        // B3 FIX: Reclamar el token PEEL directamente en la evaluación.
        // Si otro tanque del escuadrón ya lo adquirió, requestToken devolverá false y este mob mantendrá su puesto.
        return squad.requestToken(mob, SquadTacticalToken.TokenType.PEEL, 80);
    }

    @Override
    public boolean canContinueToUse() {
        if (mob == null) return false;

        // Si el rescate ya se completó o el tanque fue aturdido, finalizar
        if (hasInterrupted || mob.getTags().contains("modrpg_staggered")) {
            return false;
        }

        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        LivingEntity target = mob.getTarget();

        return squad != null
                && squad.isPeelRequested()
                && target != null
                && target.isAlive()
                && interceptTicks < 100;
    }

    @Override
    public void start() {
        this.interceptTicks = 0;
        this.hasInterrupted = false;
        this.repathDelay = 0;

        if (mob != null && mob.level() instanceof ServerLevel level) {
            SquadBarkManager.triggerBark(mob, SquadBarkManager.BarkType.VANGUARD_INTERCEPT, level);
        }
    }

    @Override
    public void stop() {
        this.interceptTicks = 0;
        this.hasInterrupted = false;
        this.repathDelay = 0;

        if (this.mob != null) {
            this.mob.getNavigation().stop();
            // B3 FIX: Liberar formalmente el token táctico al concluir o abortar la maniobra
            SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
            if (squad != null) {
                squad.releaseToken(mob, SquadTacticalToken.TokenType.PEEL);
            }
        }
    }

    @Override
    public void tick() {
        if (mob == null) return;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            this.hasInterrupted = true;
            return;
        }

        interceptTicks++;
        mob.getLookControl().setLookAt(target, 40.0f, 40.0f);

        // A3 FIX: Throttling a 10 ticks en vez de llamar a moveTo cada tick
        if (--repathDelay <= 0) {
            mob.getNavigation().moveTo(target, 1.45);
            repathDelay = 10;
        }

        // Failsafe: Si lleva más de 3 segundos intentando llegar y la ruta está bloqueada por obstáculos
        if (interceptTicks > 60 && !mob.getNavigation().isInProgress()) {
            this.hasInterrupted = true;
            return;
        }

        double distSq = mob.distanceToSqr(target);

        // Choque frontal violento contra el agresor
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

            // B3 FIX: Desactivar la solicitud en el escuadrón para que ningún otro tanque re-embista
            SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
            if (squad != null) {
                squad.clearPeelRequest();
            }

            // Marca la interrupción como concluida para que canContinueToUse() active stop() limpiamente
            this.hasInterrupted = true;
        }
    }
}