package com.example.modrpg.ai;

import com.example.modrpg.skills.magic.modular.CraftedSpell;
import com.example.modrpg.skills.magic.modular.SpellTiming;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class TacticalCasterGoal extends Goal {

    private final Mob mob;
    private final CraftedSpell spell;
    private int cooldownTicks = 0;
    private int chargeTicks = 0;
    private boolean isCharging = false;
    private int strafeDirection = 1;
    private int strafeTimer = 0;

    public TacticalCasterGoal(Mob mob, CraftedSpell spell) {
        this.mob = mob;
        this.spell = spell;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.cooldownTicks = 20; // 1 segundo antes del primer ataque
        this.isCharging = false;
        this.chargeTicks = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        double distanceSq = mob.distanceToSqr(target);
        double desiredDistance = 10.0;
        boolean hasLineOfSight = mob.getSensing().hasLineOfSight(target);

        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);

        // 1. Contador de Enfriamiento (Cooldown)
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        // =========================================================================
        // COMPORTAMIENTO F.E.A.R. 1: TELEGRAFIADO DE HECHIZOS PESADOS
        // =========================================================================
        if (isCharging) {
            chargeTicks++;
            mob.getNavigation().stop(); // Se queda inmóvil mientras carga

            // Partículas de advertencia alrededor de la cabeza del mob
            if (mob.level() instanceof ServerLevel level) {
                level.sendParticles(spell.getElement().getParticle(),
                        mob.getX(), mob.getEyeY() + 0.3, mob.getZ(),
                        4, 0.25, 0.25, 0.25, 0.05);
            }

            // Al cumplirse 1 segundo (20 ticks) de carga, detona el hechizo
            if (chargeTicks >= 20) {
                isCharging = false;
                chargeTicks = 0;
                executeCast(target);
            }
            return;
        }

        // =========================================================================
        // COMPORTAMIENTO F.E.A.R. 2: KITING Y BÚSQUEDA DE COBERTURA
        // =========================================================================
        // Si el hechizo está en enfriamiento largo (> 2s) y el mob está vulnerable, busca cobertura
        if (cooldownTicks > 40 && hasLineOfSight) {
            Vec3 coverPos = findTacticalCover(target);
            if (coverPos != null) {
                mob.getNavigation().moveTo(coverPos.x, coverPos.y, coverPos.z, 1.25);
                return;
            }
        }

        // Kiting: Si el jugador se acerca demasiado (< 7 bloques), retrocede
        if (distanceSq < 49.0) {
            mob.getNavigation().stop();
            // Desplazamiento táctico: Atrás + Paso lateral (Strafing)
            mob.getMoveControl().strafe(-0.6f, 0.4f * strafeDirection);
        } else if (distanceSq > (desiredDistance * desiredDistance)) {
            // Si está muy lejos, avanza hasta rango
            mob.getNavigation().moveTo(target, 1.05);
        } else {
            // A distancia óptima: Bailoteo lateral continuo (Strafing)
            mob.getNavigation().stop();
            mob.getMoveControl().strafe(0.0f, 0.5f * strafeDirection);
        }

        // Alternar dirección de strafing cada 3 segundos (60 ticks)
        strafeTimer++;
        if (strafeTimer >= 60) {
            strafeTimer = 0;
            strafeDirection = -strafeDirection;
        }

        // =========================================================================
        // COMPORTAMIENTO F.E.A.R. 3: DECISIÓN DE DISPARO
        // =========================================================================
        if (cooldownTicks <= 0 && hasLineOfSight && distanceSq <= 400.0) { // Hasta 20 bloques
            if (spell.getTiming() == SpellTiming.HEAVY_BURST) {
                // Iniciar telegrafiado previo
                isCharging = true;
                chargeTicks = 0;
                mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.2f, 1.4f);
            } else {
                executeCast(target);
            }
        }
    }

    private void executeCast(LivingEntity target) {
        Vec3 toTarget = target.getEyePosition().subtract(mob.getEyePosition()).normalize();

        // Ejecutar el casteo polimórfico del CraftedSpell
        spell.cast(mob, toTarget);

        // Asignar cooldown según la cadencia del hechizo
        this.cooldownTicks = spell.calculateCooldownTicks();
    }

    /**
     * F.E.A.R. Occlusion Check: Escanea 8 posiciones radiales alrededor del mob
     * buscando una casilla sólida que rompa la línea de visión con el jugador.
     */
    private Vec3 findTacticalCover(LivingEntity player) {
        Vec3 mobPos = mob.position();
        Vec3 playerEye = player.getEyePosition();

        for (int i = 0; i < 8; i++) {
            double angle = (2 * Math.PI / 8) * i;
            double cx = mobPos.x + Math.cos(angle) * 6.0;
            double cz = mobPos.z + Math.sin(angle) * 6.0;
            BlockPos checkPos = BlockPos.containing(cx, mobPos.y, cz);

            if (mob.level().getBlockState(checkPos).isAir() && mob.level().getBlockState(checkPos.below()).isSolid()) {
                Vec3 candidate = new Vec3(cx, mobPos.y, cz);
                HitResult result = mob.level().clip(new ClipContext(
                        playerEye,
                        candidate.add(0, 1.5, 0),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        mob
                ));

                // Si hay colisión de bloques entre el jugador y ese punto, es una cobertura válida
                if (result.getType() == HitResult.Type.BLOCK) {
                    return candidate;
                }
            }
        }
        return null;
    }
}