package com.example.modrpg.ai.goals.director;

import com.example.modrpg.ai.EnemyArchetype;
import com.example.modrpg.ai.director.DirectorState;
import com.example.modrpg.ai.director.DirectorWhisper;
import com.example.modrpg.ai.director.MacroDirectorManager;
import com.example.modrpg.ai.feedback.AmbushTelegraphHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Meta de Asalto en Emboscada Coordinada (Director Alien):
 * Se activa para los especialistas de vanguardia cuando el Director pasa a AMBUSH_READY o CLIMAX.
 * Emite el pre-aviso sensorial de 1.5s, corta salidas de túneles y respeta la Regla de Piedad.
 * - SPRINT 1 FIX (A1): requiresUpdateEveryTick() = true.
 * - SPRINT 3 FIX (B4): Cooldown de ataque de 20 ticks, animación de swing y verificación de línea de visión (LoS).
 * - SPRINT 3 FIX (A3): Throttling de pathfinding para evitar saturación de cálculos A*.
 */
public class AmbushAssaultGoal extends Goal {

    private final Mob mob;
    private final EnemyArchetype archetype;

    private int strikeDelayTicks = 30; // 1.5 segundos de telegrafiado antes de embestir
    private boolean preCueExecuted = false;
    private int mercyCooldown = 0;
    private int mercyPauseTicks = 0;

    // B4 FIX: Cooldown de ataque para no golpear a 20 Hz
    private int attackCooldownTicks = 0;

    // A3 FIX: Throttling de pathfinding
    private int repathDelay = 0;

    public AmbushAssaultGoal(Mob mob, EnemyArchetype archetype) {
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
        if (mob == null || archetype == null) return false;

        // Esta meta de choque físico frontal solo debe ejecutarse por vanguardia pesada
        if (!archetype.isAggressiveRush() || mob.isPassenger()) {
            return false;
        }

        // Si está aturdido por postura rota, no puede iniciar emboscada
        if (mob.getTags().contains("modrpg_staggered")) {
            return false;
        }

        if (mob.getTarget() instanceof ServerPlayer player && player.isAlive()) {
            var pacing = MacroDirectorManager.getPacingData(player.getUUID());
            return pacing.getState() == DirectorState.AMBUSH_READY || pacing.getState() == DirectorState.CLIMAX;
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.strikeDelayTicks = 30; // 1.5 segundos de gracia con ruido de advertencia
        this.preCueExecuted = false;
        this.mercyPauseTicks = 0;
        this.attackCooldownTicks = 0;
        this.repathDelay = 0;
    }

    @Override
    public void stop() {
        this.strikeDelayTicks = 30;
        this.preCueExecuted = false;
        this.mercyPauseTicks = 0;
        this.attackCooldownTicks = 0;
        this.repathDelay = 0;
        if (this.mob != null) {
            this.mob.getNavigation().stop();
        }
    }

    @Override
    public void tick() {
        if (mob == null || !(mob.getTarget() instanceof ServerPlayer player) || !player.isAlive()) {
            this.stop();
            return;
        }

        // Detener avance físico inmediato si el mob sufre aturdimiento
        if (mob.getTags().contains("modrpg_staggered")) {
            mob.getNavigation().stop();
            return;
        }

        ServerLevel level = (ServerLevel) mob.level();

        if (attackCooldownTicks > 0) {
            attackCooldownTicks--;
        }

        // =========================================================================
        // LEY 1: PRE-AVISO DE 1.5 SEGUNDOS (Crujido y polvo antes de embestir)
        // =========================================================================
        if (strikeDelayTicks > 0) {
            if (!preCueExecuted) {
                long currentTick = level.getGameTime();
                long lastCue = player.getPersistentData().getLong("modrpg_last_ambush_cue");

                if (currentTick - lastCue > 60L) {
                    player.getPersistentData().putLong("modrpg_last_ambush_cue", currentTick);
                    AmbushTelegraphHelper.triggerAmbushPreCue(level, player);
                }
                preCueExecuted = true;
            }

            strikeDelayTicks--;

            // Si el Director ordenó cortar la salida de un túnel, la vanguardia corre a tapar la puerta
            DirectorWhisper whisper = MacroDirectorManager.getWhisperForPlayer(player.getUUID());
            if (whisper != null && whisper.type() == DirectorWhisper.WhisperType.CUTOFF_CHOKE) {
                Vec3 exitBlock = whisper.waypoint();
                mob.getNavigation().moveTo(exitBlock.x, exitBlock.y, exitBlock.z, 1.35);
            } else {
                mob.getLookControl().setLookAt(player, 30.0f, 30.0f);
            }
            return; // No ataca hasta que expire el pre-aviso
        }

        // =========================================================================
        // LEY 3: REGLA DE PIEDAD / FAIL-SAFE (Vida <= 3 corazones = 6.0 HP)
        // =========================================================================
        if (player.getHealth() <= 6.0f && mercyCooldown <= 0 && mercyPauseTicks <= 0) {
            mercyPauseTicks = 40; // 2 segundos de pausa y reagrupación
            mercyCooldown = 240;  // 12 segundos antes de que pueda volver a activarse
            AmbushTelegraphHelper.triggerMercyTaunt(level, mob, player);

            Vec3 diff = mob.position().subtract(player.position());
            Vec3 away = (diff.lengthSqr() > 1e-4) ? diff.normalize().scale(2.5) : new Vec3(0, 0, 2.5);

            mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.10);
            return;
        }

        if (mercyPauseTicks > 0) {
            mercyPauseTicks--;
            mob.getLookControl().setLookAt(player, 20.0f, 20.0f);
            return;
        }

        if (mercyCooldown > 0) {
            mercyCooldown--;
        }

        // =========================================================================
        // ASALTO FÍSICO ACTIVO DE VANGUARDIA (Con throttling, swing y LoS)
        // =========================================================================
        mob.getLookControl().setLookAt(player, 30.0f, 30.0f);
        double distSq = mob.distanceToSqr(player);

        // A3 FIX: Throttling a 12 ticks para no recalcular A* cada tick
        if (--repathDelay <= 0) {
            mob.getNavigation().moveTo(player, 1.30);
            repathDelay = 12;
        }

        // B4 FIX: Solo dañar si pasaron al menos 20 ticks (1s cooldown), hay línea de visión (sin paredes) y con animación swing
        if (distSq <= 4.0 && attackCooldownTicks <= 0 && mob.getSensing().hasLineOfSight(player)) {
            mob.swing(InteractionHand.MAIN_HAND, true);
            mob.doHurtTarget(player);
            attackCooldownTicks = 20; // 1 segundo de cooldown entre golpes
        }
    }
}