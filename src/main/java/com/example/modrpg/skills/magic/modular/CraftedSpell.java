package com.example.modrpg.skills.magic.modular;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CraftedSpell {

    private String name;
    private SpellElement element;
    private SpellShape shape;
    private SpellTiming timing;
    private int powerLevel;

    public CraftedSpell(String name, SpellElement element, SpellShape shape, SpellTiming timing, int powerLevel) {
        this.name = name;
        this.element = element;
        this.shape = shape;
        this.timing = timing;
        this.powerLevel = Math.max(1, powerLevel);
    }

    public String getName() { return name; }
    public SpellElement getElement() { return element; }
    public SpellShape getShape() { return shape; }
    public SpellTiming getTiming() { return timing; }
    public int getPowerLevel() { return powerLevel; }

    public float calculateDamage(LivingEntity caster) {
        float base = element.getBaseDamage();
        float shapeMod = shape.getDamageMultiplier();
        float timeMod = timing.getDamageMultiplier();
        float powerMod = 1.0f + (powerLevel - 1) * 0.15f;
        return base * shapeMod * timeMod * powerMod;
    }

    public float calculateManaCost() {
        float base = element.getBaseManaCost();
        float shapeMod = shape.getManaMultiplier();
        float timeMod = timing.getManaMultiplier();
        return base * shapeMod * timeMod;
    }

    public int calculateCooldownTicks() {
        float base = element.getBaseCooldownTicks();
        float shapeMod = shape.getCooldownMultiplier();
        float timeMod = timing.getCooldownMultiplier();
        return Math.max(5, (int) (base * shapeMod * timeMod));
    }

    public void cast(LivingEntity caster, Vec3 look) {
        ServerLevel level = (ServerLevel) caster.level();
        float damage = calculateDamage(caster);
        DamageSource magicSource = caster.damageSources().indirectMagic(caster, caster);

        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                element.getCastSound(), SoundSource.PLAYERS, 1.0f, 1.2f);

        switch (shape) {
            case PROJECTILE -> {
                MagicProjectileEntity proj = new MagicProjectileEntity(level, caster, element, damage);
                proj.shoot(look.x, look.y, look.z, 1.8f, 1.0f);
                level.addFreshEntity(proj);
            }

            // =========================================================================
            // SPRINT 3 FIX (Bug C-07): Raycast contra bloques sólidos
            // El rayo ya no atraviesa paredes, montañas ni coberturas de escuadrones
            // =========================================================================
            case BEAM -> {
                Vec3 eyePos = caster.getEyePosition();
                double maxDistance = 16.0;
                Vec3 endPos = eyePos.add(look.scale(maxDistance));

                // 1. Comprobar colisión contra bloques del mundo
                HitResult blockHit = level.clip(new ClipContext(
                        eyePos, endPos,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster
                ));

                double actualDistance = maxDistance;
                if (blockHit.getType() == HitResult.Type.BLOCK) {
                    actualDistance = eyePos.distanceTo(blockHit.getLocation());
                    // Partículas de impacto en la pared
                    level.sendParticles(element.getParticle(),
                            blockHit.getLocation().x, blockHit.getLocation().y, blockHit.getLocation().z,
                            12, 0.15, 0.15, 0.15, 0.05);
                }

                // 2. Trazar el haz visual y aplicar daño sin traspasar el punto de impacto
                Set<LivingEntity> hitEnemies = new HashSet<>();
                int steps = (int) Math.ceil(actualDistance);

                for (int i = 1; i <= steps; i++) {
                    double currentDist = Math.min((double) i, actualDistance);
                    Vec3 point = eyePos.add(look.scale(currentDist));
                    level.sendParticles(element.getParticle(), point.x, point.y, point.z, 3, 0.1, 0.1, 0.1, 0.02);

                    AABB box = new AABB(point.x - 0.7, point.y - 0.7, point.z - 0.7,
                            point.x + 0.7, point.y + 0.7, point.z + 0.7);

                    List<LivingEntity> enemies = level.getEntitiesOfClass(
                            LivingEntity.class, box,
                            e -> e != caster && e.isAlive() && !e.isAlliedTo(caster) && !hitEnemies.contains(e)
                    );

                    for (LivingEntity e : enemies) {
                        hitEnemies.add(e);
                        e.hurt(magicSource, damage);
                        element.applyOnHitEffect(caster, e, damage);
                    }
                }
            }

            case GROUND_AOE -> {
                Vec3 groundPos = caster.position().add(look.scale(5.0));
                AABB area = new AABB(groundPos.x - 3.0, groundPos.y - 1.0, groundPos.z - 3.0,
                        groundPos.x + 3.0, groundPos.y + 2.0, groundPos.z + 3.0);

                level.sendParticles(element.getParticle(), groundPos.x, groundPos.y + 0.2, groundPos.z, 40, 2.0, 0.2, 2.0, 0.05);

                List<LivingEntity> targets = level.getEntitiesOfClass(
                        LivingEntity.class, area,
                        e -> e != caster && e.isAlive() && !e.isAlliedTo(caster)
                );
                for (LivingEntity t : targets) {
                    t.hurt(magicSource, damage);
                    element.applyOnHitEffect(caster, t, damage);
                }
            }

            case SELF_AURA -> {
                AABB auraBox = caster.getBoundingBox().inflate(4.0);
                level.sendParticles(element.getParticle(), caster.getX(), caster.getY() + 1.0, caster.getZ(), 50, 2.5, 0.5, 2.5, 0.1);

                if (element == SpellElement.HOLY) {
                    caster.heal(damage * 0.8f);
                }

                List<LivingEntity> nearby = level.getEntitiesOfClass(
                        LivingEntity.class, auraBox,
                        e -> e != caster && e.isAlive() && !e.isAlliedTo(caster)
                );
                for (LivingEntity e : nearby) {
                    e.hurt(magicSource, damage);
                    element.applyOnHitEffect(caster, e, damage);
                }
            }

            case TOUCH -> {
                Vec3 front = caster.getEyePosition().add(look.scale(2.5));
                AABB touchBox = new AABB(front.x - 1.2, front.y - 1.2, front.z - 1.2, front.x + 1.2, front.y + 1.2, front.z + 1.2);
                level.sendParticles(element.getParticle(), front.x, front.y, front.z, 20, 0.3, 0.3, 0.3, 0.1);

                List<LivingEntity> hit = level.getEntitiesOfClass(
                        LivingEntity.class, touchBox,
                        e -> e != caster && e.isAlive() && !e.isAlliedTo(caster)
                );
                if (!hit.isEmpty()) {
                    LivingEntity victim = hit.get(0);
                    victim.hurt(magicSource, damage);
                    element.applyOnHitEffect(caster, victim, damage);
                }
            }
        }

        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(
                    Component.literal(element.getColorCode() + "✨ ¡" + name + "! §7(-" + (int) calculateManaCost() + " Maná)"),
                    true
            );
        }
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putString("Element", element.name());
        tag.putString("Shape", shape.name());
        tag.putString("Timing", timing.name());
        tag.putInt("Power", powerLevel);
        return tag;
    }

    public static CraftedSpell fromNBT(CompoundTag tag) {
        String name = tag.getString("Name");
        SpellElement el = SpellElement.valueOf(tag.getString("Element"));
        SpellShape sh = SpellShape.valueOf(tag.getString("Shape"));
        SpellTiming tm = SpellTiming.valueOf(tag.getString("Timing"));
        int pwr = tag.getInt("Power");
        return new CraftedSpell(name, el, sh, tm, pwr);
    }
}