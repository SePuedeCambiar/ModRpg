package com.example.modrpg.skills.magic.modular;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.network.NetworkHooks;

public class MagicProjectileEntity extends ThrowableItemProjectile {

    private SpellElement element = SpellElement.FIRE;
    private float damage = 8.0f;

    public MagicProjectileEntity(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    public MagicProjectileEntity(Level level, LivingEntity shooter, SpellElement element, float damage) {
        super(ModEntities.MAGIC_PROJECTILE.get(), shooter, level);
        this.element = element;
        this.damage = damage;
        this.setItem(new ItemStack(element.getIconItem()));
    }

    @Override
    protected Item getDefaultItem() {
        return element.getIconItem();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.level().addParticle(element.getParticle(), this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!this.level().isClientSide && result.getEntity() instanceof LivingEntity victim) {
            LivingEntity shooter = (LivingEntity) this.getOwner();
            if (shooter != null && victim.isAlliedTo(shooter)) return;

            // ATRIBUCIÓN CORRECTA: Daño atribuido al dueño del proyectil
            DamageSource damageSource = shooter != null
                    ? this.damageSources().indirectMagic(this, shooter)
                    : this.damageSources().magic();

            victim.hurt(damageSource, this.damage);

            if (shooter != null) {
                element.applyOnHitEffect(shooter, victim, this.damage);
            }

            ServerLevel level = (ServerLevel) this.level();
            level.sendParticles(element.getParticle(), victim.getX(), victim.getY() + 1.0, victim.getZ(), 15, 0.3, 0.3, 0.3, 0.1);
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            ServerLevel level = (ServerLevel) this.level();
            level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY(), this.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
            level.sendParticles(element.getParticle(), this.getX(), this.getY(), this.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
            this.discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Element", element.name());
        tag.putFloat("Damage", damage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Element")) {
            this.element = SpellElement.valueOf(tag.getString("Element"));
        }
        if (tag.contains("Damage")) {
            this.damage = tag.getFloat("Damage");
        }
        this.setItem(new ItemStack(element.getIconItem()));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}