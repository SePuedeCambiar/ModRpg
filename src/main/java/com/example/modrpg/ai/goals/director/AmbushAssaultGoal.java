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
 * Meta de Asalto en Emboscada Coordinada (Director Alien):
 * Se activa para los especialistas de vanguardia cuando el Director pasa a AMBUSH_READY o CLIMAX.
 * Emite el pre-aviso sensorial de 1.5s, corta salidas de túneles y respeta la Regla de Piedad.
 */
public class AmbushAssaultGoal extends Goal {

    private final Mob mob;
    private final EnemyArchetype archetype;

    private int strikeDelayTicks = 30; // 1.5 segundos de telegrafiado antes de embestir
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
        // SPRINT 3 FIX (Bug M-06): Esta meta de choque físico frontal solo debe ejecutarse por vanguardia pesada.
        // Si se ejecutaba en arqueros o casters, los congelaba completamente al robar sus flags MOVE/LOOK sin tener código de ataque a distancia.
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
    public boolean requiresUpdateEveryTick() {
        return true;
    }
    @Override
    public void start() {
        this.strikeDelayTicks = 30; // 1.5 segundos de gracia con ruido de advertencia
        this.preCueExecuted = false;
        this.mercyPauseTicks = 0;
    }

    @Override
    public void stop() {
        this.strikeDelayTicks = 30;
        this.preCueExecuted = false;
        this.mercyPauseTicks = 0;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(mob.getTarget() instanceof ServerPlayer player) || !player.isAlive()) {
            this.stop();
            return;
        }

        // SPRINT 3 FIX: Detener avance físico inmediato si el mob sufre aturdimiento
        if (mob.getTags().contains("modrpg_staggered")) {
            mob.getNavigation().stop();
            return;
        }

        ServerLevel level = (ServerLevel) mob.level();

        // =========================================================================
        // LEY 1: PRE-AVISO DE 1.5 SEGUNDOS (Crujido y polvo antes de embestir)
        // =========================================================================
        if (strikeDelayTicks > 0) {
            if (!preCueExecuted) {
                // SPRINT 3 FIX: Debounce anti-spam para que 3 tanques no solapen el sonido en el mismo tick
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

            // SPRINT 3 FIX: Cálculo seguro de vector de retroceso sin división por cero
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
        // ASALTO FÍSICO ACTIVO DE VANGUARDIA
        // =========================================================================
        mob.getLookControl().setLookAt(player, 30.0f, 30.0f);
        double distSq = mob.distanceToSqr(player);

        mob.getNavigation().moveTo(player, 1.30);
        if (distSq <= 4.0) {
            mob.doHurtTarget(player);
        }
    }
}