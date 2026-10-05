package com.example.modrpg.ai;

import com.example.modrpg.ai.feedback.SquadBarkManager;
import com.example.modrpg.ai.feedback.TelegraphVisualHelper;
import com.example.modrpg.ai.squad.SquadTacticalToken;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class TacticalCasterGoal extends Goal {

    public static final String TAG_INTERRUPTIBLE = "modrpg_interruptible_charge";
    public static final String TAG_STAGGERED     = "modrpg_staggered";
    public static final String TAG_RED_UNBLOCKABLE = "modrpg_red_unblockable";

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
    private int peelRequestCooldown = 0;

    // A3 FIX: Throttling de pathfinding
    private int repathDelay = 0;
    private Vec3 lastTargetPos = null;

    // C8 FIX: Regla de piedad en hechiceros (pausa a <= 6 HP)
    private int mercyCooldown = 0;
    private int mercyPauseTicks = 0;

    public TacticalCasterGoal(Mob mob, EnemyArchetype archetype, CraftedSpell spell) {
        this.mob = mob;
        this.archetype = archetype;
        this.spell = spell;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (mob == null) return false;
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        // B6 FIX: Preservar cooldowns si ya están corriendo en lugar de reiniciarlos
        this.cooldownTicks = Math.max(this.cooldownTicks, 20);
        this.isCharging = false;
        this.chargeTicks = 0;
        this.specialSkillCooldownTicks = Math.max(this.specialSkillCooldownTicks, 60);
        this.repathDelay = 0;
        this.lastTargetPos = null;
        this.mercyPauseTicks = 0;
    }

    @Override
    public void stop() {
        this.isCharging = false;
        this.chargeTicks = 0;
        if (this.mob != null) {
            this.mob.removeTag(TAG_INTERRUPTIBLE);
            this.mob.removeTag(TAG_RED_UNBLOCKABLE);
            SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
            if (squad != null && spell != null) {
                SquadTacticalToken.TokenType tokenType = (spell.getTiming() == SpellTiming.RAPID_FIRE)
                        ? SquadTacticalToken.TokenType.SUPPRESSION
                        : SquadTacticalToken.TokenType.PRIMARY_ATTACK;
                squad.releaseToken(mob, tokenType);
                squad.getCoverManager().releaseCover(mob.getUUID());
            }
        }
    }

    @Override
    public void tick() {
        if (mob == null || mob.level().isClientSide()) return;

        int staggerTimer = mob.getPersistentData().getInt("modrpg_stagger_timer");
        if (staggerTimer > 0 || mob.getTags().contains(TAG_STAGGERED)) {
            mob.getNavigation().stop();

            if (this.isCharging) {
                this.isCharging = false;
                this.chargeTicks = 0;
                this.isRedUnblockable = false;
                this.mob.removeTag(TAG_INTERRUPTIBLE);
                this.mob.removeTag(TAG_RED_UNBLOCKABLE);
                this.cooldownTicks = Math.max(this.cooldownTicks, 40);
            }

            if (staggerTimer > 0) {
                mob.getPersistentData().putInt("modrpg_stagger_timer", staggerTimer - 1);
            }

            if (mob.level() instanceof ServerLevel level) {
                TelegraphVisualHelper.renderStaggerLoop(level, mob);
            }

            if (staggerTimer <= 1) {
                mob.removeTag(TAG_STAGGERED);
                mob.getPersistentData().remove("modrpg_stagger_timer");
            }
            return;
        }

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            this.stop();
            return;
        }

        // =========================================================================
        // C8 FIX: REGLA DE PIEDAD (Pausa de 2s si el jugador cae a <= 3 corazones / 6.0 HP)
        // =========================================================================
        if (target instanceof ServerPlayer player && player.getHealth() <= 6.0f && mercyCooldown <= 0 && mercyPauseTicks <= 0) {
            mercyPauseTicks = 40; // 2 segundos de pausa de ataque
            mercyCooldown = 240;  // 12 segundos de enfriamiento para volver a activarse
            this.isCharging = false;
            this.chargeTicks = 0;
            mob.removeTag(TAG_INTERRUPTIBLE);
            mob.removeTag(TAG_RED_UNBLOCKABLE);
            return;
        }

        if (mercyPauseTicks > 0) {
            mercyPauseTicks--;
            return;
        }

        if (mercyCooldown > 0) {
            mercyCooldown--;
        }

        double distanceSq = mob.distanceToSqr(target);
        boolean hasLineOfSight = mob.getSensing().hasLineOfSight(target);

        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);

        if (cooldownTicks > 0) cooldownTicks--;
        if (specialSkillCooldownTicks > 0) specialSkillCooldownTicks--;
        if (peelRequestCooldown > 0) peelRequestCooldown--;

        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);

        // B3 FIX: Petición de rescate controlada (solo 1 vez cada 3 segundos); el rescate lo ejecuta TacticalPeelGoal
        if (squad != null && mob.level() instanceof ServerLevel level) {
            if (archetype != null && !archetype.isAggressiveRush() && distanceSq < 25.0 && peelRequestCooldown <= 0) {
                squad.requestPeel(mob, target);
                SquadBarkManager.triggerBark(mob, SquadBarkManager.BarkType.PEEL_REQUEST, level);
                peelRequestCooldown = 60;
            }
        }

        handleArchetypeSpecials(target, distanceSq);

        if (isCharging) {
            chargeTicks++;

            if (archetype != null && !archetype.isAggressiveRush()) {
                mob.getNavigation().stop();
            } else {
                throttledMoveTo(target.position(), 1.30);
            }

            int requiredCharge = isRedUnblockable ? 26 : 20;

            if (mob.level() instanceof ServerLevel level) {
                if (isRedUnblockable) {
                    TelegraphVisualHelper.renderRedUnblockable(level, mob, chargeTicks, requiredCharge);
                } else {
                    TelegraphVisualHelper.renderYellowInterruptible(level, mob, chargeTicks, requiredCharge);
                }
            }

            if (chargeTicks >= requiredCharge) {
                isCharging = false;
                chargeTicks = 0;
                mob.removeTag(TAG_INTERRUPTIBLE);
                mob.removeTag(TAG_RED_UNBLOCKABLE);

                executeCast(target);

                if (squad != null && spell != null) {
                    SquadTacticalToken.TokenType tokenType = (spell.getTiming() == SpellTiming.RAPID_FIRE)
                            ? SquadTacticalToken.TokenType.SUPPRESSION
                            : SquadTacticalToken.TokenType.PRIMARY_ATTACK;
                    squad.releaseToken(mob, tokenType);
                }
            }
            return;
        }

        handleMovement(target, distanceSq, hasLineOfSight, squad);

        double maxCastDistSq = (archetype != null && archetype.isAggressiveRush()) ? 16.0 : 400.0;

        if (cooldownTicks <= 0 && hasLineOfSight && distanceSq <= maxCastDistSq && spell != null) {
            SquadTacticalToken.TokenType neededToken = (spell.getTiming() == SpellTiming.RAPID_FIRE)
                    ? SquadTacticalToken.TokenType.SUPPRESSION
                    : SquadTacticalToken.TokenType.PRIMARY_ATTACK;

            int chargeRequired = (spell.getTiming() == SpellTiming.HEAVY_BURST || spell.getShape() == SpellShape.GROUND_AOE) ? 26 : 20;

            boolean canCast = (squad == null) || squad.requestToken(mob, neededToken, chargeRequired + 10);

            if (canCast) {
                isCharging = true;
                chargeTicks = 0;

                boolean isHeavyOrAoE = spell.getTiming() == SpellTiming.HEAVY_BURST || spell.getShape() == SpellShape.GROUND_AOE;
                this.isRedUnblockable = isHeavyOrAoE;

                if (isRedUnblockable) {
                    mob.removeTag(TAG_INTERRUPTIBLE);
                    mob.addTag(TAG_RED_UNBLOCKABLE);
                } else {
                    mob.addTag(TAG_INTERRUPTIBLE);
                    mob.removeTag(TAG_RED_UNBLOCKABLE);
                }

                if (squad != null) {
                    squad.getCoverManager().releaseCover(mob.getUUID());
                }
            }
        }
    }

    public static void interruptCaster(Mob mob, ServerPlayer player) {
        mob.removeTag(TAG_INTERRUPTIBLE);
        mob.removeTag(TAG_RED_UNBLOCKABLE);
        mob.addTag(TAG_STAGGERED);
        mob.getPersistentData().putInt("modrpg_stagger_timer", 45);

        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);
        if (squad != null) {
            squad.forceReleaseAllTokens(mob.getUUID());
            squad.getCoverManager().releaseCover(mob.getUUID());
        }

        ServerLevel level = (ServerLevel) mob.level();
        TelegraphVisualHelper.renderStaggerBurst(level, mob);

        player.displayClientMessage(Component.literal("§e§l⚡ ¡POSTURA ROTA! §c(+30% Daño Crítico por 2s)"), true);
    }

    private void executeCast(LivingEntity target) {
        if (spell == null) return;
        Vec3 toTarget = target.getEyePosition().subtract(mob.getEyePosition()).normalize();
        spell.cast(mob, toTarget);
        this.cooldownTicks = spell.calculateCooldownTicks();
    }

    private void handleMovement(LivingEntity target, double distanceSq, boolean hasLineOfSight, SquadCoordinator.Squad squad) {
        if (archetype == null) return;
        double desiredDistance = archetype.getPreferredDistance();
        double kitingDistance = archetype.getKitingThresholdDistance();

        if (archetype.isAggressiveRush()) {
            throttledMoveTo(target.position(), 1.25);
        } else {
            // B5 FIX: Solo flanquear si es VOID_WEAVER
            if (archetype == EnemyArchetype.VOID_WEAVER && squad != null && distanceSq > 36.0 && mob.level() instanceof ServerLevel level) {
                Vec3 flankPos = squad.getFlankingPosition(mob, target);
                if (flankPos != null) {
                    throttledMoveTo(flankPos, 1.15);
                    SquadBarkManager.triggerBark(mob, SquadBarkManager.BarkType.FLANKING, level);
                    return;
                }
            }

            if (cooldownTicks > 40 && hasLineOfSight) {
                Vec3 coverPos = findTacticalCover(target, squad);
                if (coverPos != null) {
                    throttledMoveTo(coverPos, 1.25);
                    return;
                }
            }

            if (distanceSq < (kitingDistance * kitingDistance)) {
                mob.getNavigation().stop();
                mob.getMoveControl().strafe(-0.6f, 0.4f * strafeDirection);
            } else if (distanceSq > (desiredDistance * desiredDistance)) {
                throttledMoveTo(target.position(), 1.05);
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

    // A3 FIX: Throttling de pathfinding a 10-12 ticks
    private void throttledMoveTo(Vec3 dest, double speed) {
        if (lastTargetPos == null || dest.distanceToSqr(lastTargetPos) > 4.0 || --repathDelay <= 0) {
            mob.getNavigation().moveTo(dest.x, dest.y, dest.z, speed);
            lastTargetPos = dest;
            repathDelay = 12;
        }
    }

    private Vec3 findTacticalCover(LivingEntity player, SquadCoordinator.Squad squad) {
        if (squad != null) {
            return squad.getCoverManager().findAndClaimCover(mob, player, 12.0);
        }

        Vec3 mobPos = mob.position();
        Vec3 toPlayer = player.position().subtract(mobPos).normalize();
        BlockPos behindPos = BlockPos.containing(mobPos.subtract(toPlayer.scale(4.0)));

        if (mob.level().getBlockState(behindPos).isAir() && mob.level().getBlockState(behindPos.below()).isSolid()) {
            HitResult hit = mob.level().clip(new ClipContext(
                    player.getEyePosition(),
                    new Vec3(behindPos.getX() + 0.5, behindPos.getY() + 1.5, behindPos.getZ() + 0.5),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob
            ));
            if (hit.getType() == HitResult.Type.BLOCK) {
                return new Vec3(behindPos.getX() + 0.5, behindPos.getY(), behindPos.getZ() + 0.5);
            }
        }
        return null;
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
                // B6 FIX: Limitar a un máximo de 3 esbirros por Nigromante
                AABB checkZone = mob.getBoundingBox().inflate(16.0);
                List<Zombie> existingMinions = level.getEntitiesOfClass(Zombie.class, checkZone,
                        z -> z.getTags().contains("modrpg_necro_minion_" + mob.getUUID()));

                if (existingMinions.size() < 3) {
                    specialSkillCooldownTicks = 300;
                    spawnNecroMinion(level, target);
                    Vec3 awayFromTarget = mob.position().subtract(target.position()).normalize().scale(1.2);
                    mob.setDeltaMovement(new Vec3(awayFromTarget.x, 0.3, awayFromTarget.z));
                }
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

        minion.addTag(EnemyRpgManager.TAG_INITIALIZED);
        minion.addTag("modrpg_necro_minion_" + mob.getUUID());

        level.addFreshEntity(minion);
        level.sendParticles(ParticleTypes.SOUL, spawnPos.x, spawnPos.y + 0.5, spawnPos.z, 15, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 1.8f);
    }
}