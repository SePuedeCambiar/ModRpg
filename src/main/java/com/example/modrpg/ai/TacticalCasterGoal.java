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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * IA Táctica de Combate inspirada en F.E.A.R.
 * - Sprint 1: Telegrafiado sensorial (Halo amarillo interrumpible / Aura roja imbloqueable).
 * - Sprint 2: Tokens con Lease Heartbeat (Primary / Suppression / Peel) y caída por aturdimiento.
 * - Sprint 3: Búsqueda de coberturas coordinadas con Time-Slicing y reserva exclusiva.
 */
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
        // =========================================================================
        // 1. ESTADO DE ATURDIMIENTO / ROMPE-POSTURA (STAGGER)
        // =========================================================================
        int staggerTimer = mob.getPersistentData().getInt("modrpg_stagger_timer");
        if (staggerTimer > 0) {
            mob.getNavigation().stop();
            mob.getPersistentData().putInt("modrpg_stagger_timer", staggerTimer - 1);

            if (mob.level() instanceof ServerLevel level) {
                TelegraphVisualHelper.renderStaggerLoop(level, mob);
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
        // 2. MANIOBRA F.E.A.R.: PEELING Y RESCATE REACTIVO
        // =========================================================================
        if (squad != null && mob.level() instanceof ServerLevel level) {
            // A) Caster frágil acorralado a menos de 5 bloques: Pide auxilio
            if (!archetype.isAggressiveRush() && distanceSq < 25.0) {
                squad.requestPeel(mob, target);
                SquadBarkManager.triggerBark(mob, SquadBarkManager.BarkType.PEEL_REQUEST, level);
            }

            // B) Vanguardia frontal acude de inmediato a interceptar
            if (archetype.isAggressiveRush() && squad.isPeelRequested()) {
                mob.getNavigation().moveTo(target, 1.45);
                SquadBarkManager.triggerBark(mob, SquadBarkManager.BarkType.VANGUARD_INTERCEPT, level);
            }
        }

        // Habilidades especiales por arquetipo (curación de tormenta / invocación nigromante)
        handleArchetypeSpecials(target, distanceSq);

        // =========================================================================
        // 3. TELEGRAFIADO SENSORIAL DE CANALIZACIÓN (SPRINT 1)
        // =========================================================================
        if (isCharging) {
            chargeTicks++;

            if (!archetype.isAggressiveRush()) {
                mob.getNavigation().stop();
            } else {
                mob.getNavigation().moveTo(target, 1.30);
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

                executeCast(target);

                // Liberar el token al disparar (Sprint 2)
                if (squad != null) {
                    SquadTacticalToken.TokenType tokenType = (spell.getTiming() == SpellTiming.RAPID_FIRE)
                            ? SquadTacticalToken.TokenType.SUPPRESSION
                            : SquadTacticalToken.TokenType.PRIMARY_ATTACK;
                    squad.releaseToken(mob, tokenType);
                }
            }
            return;
        }

        // =========================================================================
        // 4. MOVIMIENTO Y COBERTURAS OPTIMIZADAS (SPRINT 3)
        // =========================================================================
        handleMovement(target, distanceSq, hasLineOfSight, squad);

        // =========================================================================
        // 5. SOLICITUD DE TOKENS CON LEASE HEARTBEAT (SPRINT 2)
        // =========================================================================
        double maxCastDistSq = (archetype.isAggressiveRush()) ? 16.0 : 400.0;

        if (cooldownTicks <= 0 && hasLineOfSight && distanceSq <= maxCastDistSq) {
            SquadTacticalToken.TokenType neededToken = (spell.getTiming() == SpellTiming.RAPID_FIRE)
                    ? SquadTacticalToken.TokenType.SUPPRESSION
                    : SquadTacticalToken.TokenType.PRIMARY_ATTACK;

            int chargeRequired = (spell.getTiming() == SpellTiming.HEAVY_BURST || spell.getShape() == SpellShape.GROUND_AOE) ? 26 : 20;

            // Solicitar formalmente el token con lease limitado para evitar bloqueos
            boolean canCast = (squad == null) || squad.requestToken(mob, neededToken, chargeRequired + 10);

            if (canCast) {
                isCharging = true;
                chargeTicks = 0;

                boolean isHeavyOrAoE = spell.getTiming() == SpellTiming.HEAVY_BURST || spell.getShape() == SpellShape.GROUND_AOE;
                this.isRedUnblockable = isHeavyOrAoE;

                if (isRedUnblockable) {
                    mob.removeTag(TAG_INTERRUPTIBLE);
                } else {
                    mob.addTag(TAG_INTERRUPTIBLE);
                }

                // Liberar cualquier cobertura que estuviese reservando al momento de iniciar ataque
                if (squad != null) {
                    squad.getCoverManager().releaseCover(mob.getUUID());
                }
            }
        }
    }

    /**
     * Interrumpe el casteo cuando el jugador acierta un golpe con recarga >= 85%.
     * Provoca rotura de postura inmediata y revoca todos los tokens del mob en el acto.
     */
    public static void interruptCaster(Mob mob, ServerPlayer player) {
        mob.removeTag(TAG_INTERRUPTIBLE);
        mob.addTag(TAG_STAGGERED);
        mob.getPersistentData().putInt("modrpg_stagger_timer", 45); // 2.25 segundos

        // CRÍTICO SPRINT 2: Liberación forzada de tokens en el mismo tick
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
        Vec3 toTarget = target.getEyePosition().subtract(mob.getEyePosition()).normalize();
        spell.cast(mob, toTarget);
        this.cooldownTicks = spell.calculateCooldownTicks();
    }

    /**
     * Gestiona el desplazamiento táctico:
     * - Flanqueo a 60°-90° si hay escuadrón.
     * - Retirada hacia nodos de cobertura compartidos (Sprint 3) si está en cooldown.
     * - Kiting y ametrallamiento lateral (strafing) para mantener la distancia ideal.
     */
    private void handleMovement(LivingEntity target, double distanceSq, boolean hasLineOfSight, SquadCoordinator.Squad squad) {
        double desiredDistance = archetype.getPreferredDistance();
        double kitingDistance = archetype.getKitingThresholdDistance();

        if (archetype.isAggressiveRush()) {
            mob.getNavigation().moveTo(target, 1.25);
        } else {
            // A) Flanqueo angular coordinado
            if (squad != null && distanceSq > 36.0 && mob.level() instanceof ServerLevel level) {
                Vec3 flankPos = squad.getFlankingPosition(mob, target);
                if (flankPos != null) {
                    mob.getNavigation().moveTo(flankPos.x, flankPos.y, flankPos.z, 1.15);
                    SquadBarkManager.triggerBark(mob, SquadBarkManager.BarkType.FLANKING, level);
                    return;
                }
            }

            // B) Búsqueda de cobertura optimizada si está en cooldown largo (> 2s)
            if (cooldownTicks > 40 && hasLineOfSight) {
                Vec3 coverPos = findTacticalCover(target, squad);
                if (coverPos != null) {
                    mob.getNavigation().moveTo(coverPos.x, coverPos.y, coverPos.z, 1.25);
                    return;
                }
            }

            // C) Kiting y Strafing clásico
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

    /**
     * Búsqueda de cobertura:
     * - Si pertenece a un escuadrón: consulta al SquadCoverManager (Sprint 3) con time-slicing (0 lag).
     * - Si es un mob solitario: usa una comprobación única en la sombra del muro (sin bucles de 8 raycasts).
     */
    private Vec3 findTacticalCover(LivingEntity player, SquadCoordinator.Squad squad) {
        if (squad != null) {
            return squad.getCoverManager().findAndClaimCover(mob, player, 12.0);
        }

        // Fallback ligero para mobs sin escuadrón
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
}