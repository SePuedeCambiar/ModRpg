package com.example.modrpg.ai;

import com.example.modrpg.skills.magic.modular.CraftedSpell;
import com.example.modrpg.skills.magic.modular.SpellShape;
import com.example.modrpg.skills.magic.modular.SpellTiming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    public static final String TAG_INTERRUPTIBLE = "modrpg_interruptible_charge";
    public static final String TAG_STAGGERED     = "modrpg_staggered";

    private final Mob mob;
    private final EnemyArchetype archetype;
    private final CraftedSpell spell;

    private int cooldownTicks = 0;
    private int chargeTicks = 0;
    private boolean isCharging = false;
    private boolean isRedUnblockable = false;

    private int strafeDirection = 1;
    private int strafeTimer = 0;
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
        this.cooldownTicks = 20;
        this.isCharging = false;
        this.chargeTicks = 0;
        this.specialSkillCooldownTicks = 60;
    }

    @Override
    public void tick() {
        // 1. Estado de Aturdimiento / Rompe-Postura
        int staggerTimer = mob.getPersistentData().getInt("modrpg_stagger_timer");
        if (staggerTimer > 0) {
            mob.getNavigation().stop();
            mob.getPersistentData().putInt("modrpg_stagger_timer", staggerTimer - 1);
            if (mob.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getEyeY() + 0.4, mob.getZ(), 2, 0.2, 0.1, 0.2, 0.05);
            }
            if (staggerTimer - 1 <= 0) {
                mob.removeTag(TAG_STAGGERED);
            }
            return;
        }

        LivingEntity target = mob.getTarget();
        if (target == null) return;

        double distanceSq = mob.distanceToSqr(target);
        boolean hasLineOfSight = mob.getSensing().hasLineOfSight(target);

        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);

        if (cooldownTicks > 0) cooldownTicks--;
        if (specialSkillCooldownTicks > 0) specialSkillCooldownTicks--;

        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);

        // =========================================================================
        // COMPORTAMIENTO F.E.A.R.: PEELING (RESCATE MUTUO)
        // =========================================================================
        if (squad != null) {
            // A) Si soy un caster frágil a distancia y el jugador se acerca a < 5 bloques, pido auxilio
            if (!archetype.isAggressiveRush() && distanceSq < 25.0) {
                squad.requestPeel(mob, target);
            }

            // B) Si soy la Vanguardia frontal y un aliado pidió auxilio, intercepto de inmediato
            if (archetype.isAggressiveRush() && squad.isPeelRequested()) {
                mob.getNavigation().moveTo(target, 1.40); // Carga furiosa para obligar al jugador a mirarme
                if (mob.level() instanceof ServerLevel level && mob.tickCount % 40 == 0) {
                    level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0f, 1.6f);
                }
            }
        }

        // Habilidades secundarias de arquetipos
        handleArchetypeSpecials(target, distanceSq);

        // =========================================================================
        // TELEGRAFIADO DE CANALIZACIÓN
        // =========================================================================
        if (isCharging) {
            chargeTicks++;

            if (!archetype.isAggressiveRush()) {
                mob.getNavigation().stop();
            } else {
                mob.getNavigation().moveTo(target, 1.30);
            }

            if (mob.level() instanceof ServerLevel level) {
                if (isRedUnblockable) {
                    level.sendParticles(ParticleTypes.FLAME, mob.getX(), mob.getY() + 0.1, mob.getZ(), 6, 0.4, 0.1, 0.4, 0.02);
                    level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + 0.1, target.getZ(), 4, 0.3, 0.1, 0.3, 0.01);
                } else {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 5, 0.25, 0.25, 0.25, 0.08);
                    level.sendParticles(ParticleTypes.WAX_ON, mob.getX(), mob.getEyeY() + 0.2, mob.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
                }
            }

            int requiredCharge = isRedUnblockable ? 25 : 20;
            if (chargeTicks >= requiredCharge) {
                isCharging = false;
                chargeTicks = 0;
                mob.removeTag(TAG_INTERRUPTIBLE);
                executeCast(target);
                if (squad != null) squad.releaseCastingToken(mob);
            }
            return;
        }

        // =========================================================================
        // MOVIMIENTO Y POSICIONAMIENTO (FLANQUEO F.E.A.R.)
        // =========================================================================
        handleMovement(target, distanceSq, hasLineOfSight, squad);

        // =========================================================================
        // DECISIÓN DE DISPARO CON TOKENS DE ESCUADRÓN
        // =========================================================================
        double maxCastDistSq = (archetype.isAggressiveRush()) ? 16.0 : 400.0;

        if (cooldownTicks <= 0 && hasLineOfSight && distanceSq <= maxCastDistSq) {
            // F.E.A.R. Token: Pedir permiso al escuadrón antes de saturar al jugador
            boolean canCast = (squad == null) || squad.requestCastingToken(mob);

            if (canCast) {
                isCharging = true;
                chargeTicks = 0;

                boolean isHeavyOrAoE = spell.getTiming() == SpellTiming.HEAVY_BURST || spell.getShape() == SpellShape.GROUND_AOE;
                this.isRedUnblockable = isHeavyOrAoE;

                if (isRedUnblockable) {
                    mob.removeTag(TAG_INTERRUPTIBLE);
                    mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                            SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.4f, 1.2f);
                } else {
                    mob.addTag(TAG_INTERRUPTIBLE);
                    mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                            SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 0.8f, 1.8f);
                }
            }
        }
    }

    public static void interruptCaster(Mob mob, ServerPlayer player) {
        mob.removeTag(TAG_INTERRUPTIBLE);
        mob.addTag(TAG_STAGGERED);
        mob.getPersistentData().putInt("modrpg_stagger_timer", 40);

        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        if (squad != null) squad.releaseCastingToken(mob);

        ServerLevel level = (ServerLevel) mob.level();
        level.sendParticles(ParticleTypes.FLASH, mob.getX(), mob.getEyeY(), mob.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getEyeY(), mob.getZ(), 20, 0.4, 0.4, 0.4, 0.2);

        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.2f, 0.8f);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7f, 1.6f);

        player.displayClientMessage(Component.literal("§e§l⚡ ¡POSTURA ROTA! §c(+30% Daño Crítico por 2s)"), true);
    }

    private void executeCast(LivingEntity target) {
        Vec3 toTarget = target.getEyePosition().subtract(mob.getEyePosition()).normalize();
        spell.cast(mob, toTarget);
        this.cooldownTicks = spell.calculateCooldownTicks();
    }

    private void handleMovement(LivingEntity target, double distanceSq, boolean hasLineOfSight, SquadCoordinator.Squad squad) {
        double desiredDistance = archetype.getPreferredDistance();
        double kitingDistance = archetype.getKitingThresholdDistance();

        if (archetype.isAggressiveRush()) {
            mob.getNavigation().moveTo(target, 1.25);
        } else {
            // F.E.A.R. FLANQUEO: Si hay escuadrón, buscar el ángulo lateral de 60°-90°
            if (squad != null && distanceSq > 36.0) {
                Vec3 flankPos = squad.getFlankingPosition(mob, target);
                if (flankPos != null) {
                    mob.getNavigation().moveTo(flankPos.x, flankPos.y, flankPos.z, 1.15);
                    return;
                }
            }

            if (cooldownTicks > 40 && hasLineOfSight) {
                Vec3 coverPos = findTacticalCover(target);
                if (coverPos != null) {
                    mob.getNavigation().moveTo(coverPos.x, coverPos.y, coverPos.z, 1.25);
                    return;
                }
            }

            if (distanceSq < (kitingDistance * kitingDistance)) {
                mob.getNavigation().stop();
                mob.getMoveControl().strafe(-0.6f, 0.4f * strafeDirection);
            } else if (distanceSq > (desiredDistance * desiredDistance)) {
                mob.getNavigation().moveTo(target, 1.05);
            } else {
                mob.getNavigation().stop();
                mob.getMoveControl().strafe(0.0f, 0.5f * strafeDirection);
            }

            strafeTimer++;
            if (strafeTimer >= 60) {
                strafeTimer = 0;
                strafeDirection = -strafeDirection;
            }
        }
    }

    private void handleArchetypeSpecials(LivingEntity target, double distanceSq) {
        if (archetype == EnemyArchetype.STORM_EVOKER && specialSkillCooldownTicks <= 0) {
            if (mob.getHealth() < (mob.getMaxHealth() * 0.40f)) {
                mob.heal(8.0f);
                specialSkillCooldownTicks = 240;
                if (mob.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + 1.2, mob.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
                    level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.0f, 1.6f);
                }
            }
        }

        if (archetype == EnemyArchetype.CRYPT_NECROMANCER && specialSkillCooldownTicks <= 0) {
            if (distanceSq < 36.0 && mob.level() instanceof ServerLevel level) {
                specialSkillCooldownTicks = 300;
                spawnNecroMinion(level, target);
                Vec3 awayFromTarget = mob.position().subtract(target.position()).normalize().scale(1.2);
                mob.setDeltaMovement(new Vec3(awayFromTarget.x, 0.3, awayFromTarget.z));
            }
        }
    }

    private void spawnNecroMinion(ServerLevel level, LivingEntity target) {
        Zombie minion = EntityType.ZOMBIE.create(level);
        if (minion == null) return;

        Vec3 spawnPos = mob.position().add((Math.random() - 0.5) * 2.0, 0, (Math.random() - 0.5) * 2.0);
        minion.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, mob.getYRot(), 0.0f);
        minion.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        minion.setDropChance(EquipmentSlot.HEAD, 0.0f);
        minion.setCustomName(Component.literal("§5Sirviente de Cripta"));
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