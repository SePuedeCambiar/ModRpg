package com.example.modrpg.ai.goals.director;

import com.example.modrpg.ai.EnemyArchetype;
import com.example.modrpg.ai.director.DirectorState;
import com.example.modrpg.ai.director.DirectorWhisper;
import com.example.modrpg.ai.director.MacroDirectorManager;
import com.example.modrpg.ai.feedback.AmbushTelegraphHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Meta de Emboscada Coordinada:
 * Se activa cuando el Director pasa a AMBUSH_READY o CLIMAX.
 * Emite el pre-aviso de 1.5s y aplica la Regla de Piedad si el jugador está moribundo.
 */
public class AmbushAssaultGoal extends Goal {

    private final Mob mob;
    private final EnemyArchetype archetype;

    private int strikeDelayTicks = 30; // 1.5 segundos de telegrafiado antes de golpear
    private boolean preCueExecuted = false;
    private int mercyCooldown = 0;
    private int mercyPauseTicks = 0;

    public AmbushAssaultGoal(Mob mob, EnemyArchetype archetype) {
        this.mob = mob;
        this.archetype = archetype;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() instanceof ServerPlayer player) {
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
    }

    @Override
    public void tick() {
        if (!(mob.getTarget() instanceof ServerPlayer player)) return;
        ServerLevel level = (ServerLevel) mob.level();

        // =========================================================================
        // LEY 1: PRE-AVISO DE 1.5 SEGUNDOS (Crujido y polvo antes de embestir)
        // =========================================================================
        if (strikeDelayTicks > 0) {
            if (!preCueExecuted) {
                AmbushTelegraphHelper.triggerAmbushPreCue(level, player);
                preCueExecuted = true;
            }

            strikeDelayTicks--;

            // Si el Director ordenó cortar la salida de un túnel, la vanguardia corre a la puerta
            DirectorWhisper whisper = MacroDirectorManager.getWhisperForPlayer(player.getUUID());
            if (whisper != null && whisper.type() == DirectorWhisper.WhisperType.CUTOFF_CHOKE && archetype.isAggressiveRush()) {
                Vec3 exitBlock = whisper.waypoint();
                mob.getNavigation().moveTo(exitBlock.x, exitBlock.y, exitBlock.z, 1.35);
            } else {
                mob.getLookControl().setLookAt(player, 30.0f, 30.0f);
            }
            return; // No ataca hasta que expire el pre-aviso
        }

        // =========================================================================
        // LEY 3: REGLA DE PIEDAD / FAIL-SAFE (Vida <= 3 corazones)
        // =========================================================================
        if (player.getHealth() <= 6.0f && mercyCooldown <= 0 && mercyPauseTicks <= 0) {
            mercyPauseTicks = 40; // 2 segundos de pausa y reagrupación
            mercyCooldown = 240;  // 12 segundos antes de que pueda volver a pausarse
            AmbushTelegraphHelper.triggerMercyTaunt(level, mob, player);

            // Retroceder 2 bloques para darle espacio al jugador
            Vec3 away = mob.position().subtract(player.position()).normalize().scale(2.5);
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
        // ASALTO FÍSICO ACTIVO
        // =========================================================================
        mob.getLookControl().setLookAt(player, 30.0f, 30.0f);
        double distSq = mob.distanceToSqr(player);

        if (archetype.isAggressiveRush()) {
            mob.getNavigation().moveTo(player, 1.30);
            if (distSq <= 4.0) {
                mob.doHurtTarget(player);
            }
        }
    }
}