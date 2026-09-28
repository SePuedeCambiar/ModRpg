package com.example.modrpg.ai;

import com.example.modrpg.skills.magic.modular.CraftedSpell;
import com.example.modrpg.skills.magic.modular.SpellTiming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class TacticalCasterGoal extends Goal {

    private final Mob mob;
    private final EnemyArchetype archetype;
    private final CraftedSpell spell;

    private int cooldownTicks = 0;
    private int chargeTicks = 0;
    private boolean isCharging = false;
    private int strafeDirection = 1;
    private int strafeTimer = 0;

    // Mecánicas especiales por arquetipo
    private int specialSkillCooldownTicks = 0;

    public TacticalCasterGoal(Mob mob, EnemyArchetype archetype, CraftedSpell spell) {
        this.mob = mob;
        this.archetype = archetype;
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
        this.specialSkillCooldownTicks = 60; // 3 segundos de gracia antes de usar trucos
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        double distanceSq = mob.distanceToSqr(target);
        boolean hasLineOfSight = mob.getSensing().hasLineOfSight(target);

        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);

        if (cooldownTicks > 0) cooldownTicks--;
        if (specialSkillCooldownTicks > 0) specialSkillCooldownTicks--;

        // =========================================================================
        // MECÁNICA ESPECIAL 1: Curación de Emergencia (Bruja / Hechicera de Tormentas)
        // =========================================================================
        if (archetype == EnemyArchetype.STORM_EVOKER && specialSkillCooldownTicks <= 0) {
            if (mob.getHealth() < (mob.getMaxHealth() * 0.40f)) {
                mob.heal(8.0f);
                specialSkillCooldownTicks = 240; // 12s de recarga
                if (mob.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + 1.2, mob.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
                    level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.0f, 1.6f);
                }
            }
        }

        // =========================================================================
        // MECÁNICA ESPECIAL 2: Invocación de carne de cañón (Nigromante de Cripta)
        // =========================================================================
        if (archetype == EnemyArchetype.CRYPT_NECROMANCER && specialSkillCooldownTicks <= 0) {
            // Si el jugador se abalanza cuerpo a cuerpo (< 6 bloques), invoca un sirviente que intercepte
            if (distanceSq < 36.0 && mob.level() instanceof ServerLevel level) {
                specialSkillCooldownTicks = 300; // 15s de cooldown
                spawnNecroMinion(level, target);
                // Retroceso de pánico inmediato
                Vec3 awayFromTarget = mob.position().subtract(target.position()).normalize().scale(1.2);
                mob.setDeltaMovement(new Vec3(awayFromTarget.x, 0.3, awayFromTarget.z));
            }
        }

        // =========================================================================
        // TELEGRAFIADO DE HECHIZOS PESADOS
        // =========================================================================
        if (isCharging) {
            chargeTicks++;
            if (!archetype.isAggressiveRush()) {
                mob.getNavigation().stop(); // Se queda quieto canalizando si es caster a distancia
            } else {
                mob.getNavigation().moveTo(target, 1.30); // El bruto sigue corriendo hacia ti mientras carga
            }

            if (mob.level() instanceof ServerLevel level) {
                level.sendParticles(spell.getElement().getParticle(),
                        mob.getX(), mob.getEyeY() + 0.3, mob.getZ(),
                        5, 0.25, 0.25, 0.25, 0.05);
            }

            if (chargeTicks >= 20) { // 1 segundo de telegrafiado completado
                isCharging = false;
                chargeTicks = 0;
                executeCast(target);
            }
            return;
        }

        // =========================================================================
        // MOVIMIENTO Y POSICIONAMIENTO TÁCTICO SEGÚN ARQUETIPO
        // =========================================================================
        double desiredDistance = archetype.getPreferredDistance();
        double kitingDistance = archetype.getKitingThresholdDistance();

        if (archetype.isAggressiveRush()) {
            // ARQUETIPO BRUTO: Carga incesante hacia el jugador
            mob.getNavigation().moveTo(target, 1.25);
        } else {
            // ARQUETIPO A DISTANCIA: Kiting, cobertura y strafing
            if (cooldownTicks > 40 && hasLineOfSight) {
                Vec3 coverPos = findTacticalCover(target);
                if (coverPos != null) {
                    mob.getNavigation().moveTo(coverPos.x, coverPos.y, coverPos.z, 1.25);
                    return;
                }
            }

            if (distanceSq < (kitingDistance * kitingDistance)) {
                // Jugador muy cerca: paso atrás + paso lateral
                mob.getNavigation().stop();
                mob.getMoveControl().strafe(-0.6f, 0.4f * strafeDirection);
            } else if (distanceSq > (desiredDistance * desiredDistance)) {
                // Muy lejos: avanzar hasta el rango óptimo
                mob.getNavigation().moveTo(target, 1.05);
            } else {
                // Distancia perfecta: baile lateral (strafing)
                mob.getNavigation().stop();
                mob.getMoveControl().strafe(0.0f, 0.5f * strafeDirection);
            }

            strafeTimer++;
            if (strafeTimer >= 60) {
                strafeTimer = 0;
                strafeDirection = -strafeDirection;
            }
        }

        // =========================================================================
        // DISPARO / EJECUCIÓN DEL HECHIZO
        // =========================================================================
        double maxCastDistSq = (archetype.isAggressiveRush()) ? 16.0 : 400.0; // Bruto solo a < 4 bloques

        if (cooldownTicks <= 0 && hasLineOfSight && distanceSq <= maxCastDistSq) {
            if (spell.getTiming() == SpellTiming.HEAVY_BURST) {
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
        spell.cast(mob, toTarget);
        this.cooldownTicks = spell.calculateCooldownTicks();
    }

    private void spawnNecroMinion(ServerLevel level, LivingEntity target) {
        Zombie minion = EntityType.ZOMBIE.create(level);
        if (minion == null) return;

        Vec3 spawnPos = mob.position().add((Math.random() - 0.5) * 2.0, 0, (Math.random() - 0.5) * 2.0);
        minion.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, mob.getYRot(), 0.0f);
        minion.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        minion.setDropChance(EquipmentSlot.HEAD, 0.0f);
        minion.setCustomName(net.minecraft.network.chat.Component.literal("§5Sirviente de Cripta"));
        minion.setCustomNameVisible(false);
        minion.setTarget(target);

        level.addFreshEntity(minion);
        level.sendParticles(ParticleTypes.SOUL, spawnPos.x, spawnPos.y + 0.5, spawnPos.z, 15, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 1.8f);
    }

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

                if (result.getType() == HitResult.Type.BLOCK) {
                    return candidate;
                }
            }
        }
        return null;
    }
}