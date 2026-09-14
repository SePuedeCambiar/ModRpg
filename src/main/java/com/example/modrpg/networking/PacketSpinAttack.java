package com.example.modrpg.networking;

import com.example.modrpg.skills.PlayerSkillsProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public class PacketSpinAttack {
    public PacketSpinAttack() {}

    public static void encode(PacketSpinAttack msg, FriendlyByteBuf buf) {}

    public static PacketSpinAttack decode(FriendlyByteBuf buf) {
        return new PacketSpinAttack();
    }

    public static void handle(PacketSpinAttack msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                // 1. Requisito de desbloqueo (Nivel 20 Melee o habilidad activa)
                if (skills.getMeleeLevel() < 20 && !skills.hasSpinAttack()) {
                    player.displayClientMessage(
                            Component.literal("§c🔒 Requiere Nivel 20 de Melee para usar el Ataque Giratorio."),
                            true
                    );
                    return;
                }

                // 2. Verificar Cooldown
                if (skills.getSpinCooldown() > 0) {
                    int seg = (skills.getSpinCooldown() / 20) + 1;
                    player.displayClientMessage(
                            Component.literal("§c⏳ Ataque Giratorio en enfriamiento: §e" + seg + "s"),
                            true
                    );
                    return;
                }

                // 3. Activar Cooldown de 6 segundos (120 ticks)
                skills.setSpinCooldown(120);

                ServerLevel level = (ServerLevel) player.level();
                double radius = 4.0;

                // Animación de brazo en el jugador
                player.swing(InteractionHand.MAIN_HAND, true);

                // 4. Buscar enemigos en el área alrededor del jugador
                AABB box = player.getBoundingBox().inflate(radius, 1.5, radius);
                List<LivingEntity> targets = level.getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        entity -> entity != player && entity.isAlive() && !entity.isAlliedTo(player)
                );

                // Daño base escalado según el nivel de Melee del mod
                float baseDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
                float skillBonus = 1.0f + (skills.getMeleeLevel() * 0.015f); // +1.5% daño extra por nivel
                float totalDamage = baseDamage * skillBonus;

                for (LivingEntity target : targets) {
                    // Dañar al objetivo
                    target.hurt(player.damageSources().playerAttack(player), totalDamage);

                    // Empuje radial (hacia afuera en 360 grados)
                    double dx = target.getX() - player.getX();
                    double dz = target.getZ() - player.getZ();
                    target.knockback(0.7, -dx, -dz);
                }

                // 5. EFECTOS VISUALES 360° (Anillo de tajos con trigonometría)
                int points = 16;
                for (int i = 0; i < points; i++) {
                    double angle = i * (2 * Math.PI / points);
                    double px = player.getX() + (Math.cos(angle) * 2.2);
                    double pz = player.getZ() + (Math.sin(angle) * 2.2);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, px, player.getY() + 0.8, pz, 1, 0, 0, 0, 0);
                    level.sendParticles(ParticleTypes.CRIT, px, player.getY() + 0.8, pz, 2, 0.1, 0.1, 0.1, 0.05);
                }

                // 6. Sonido de torbellino
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.6f, 0.8f);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.8f, 1.4f);

                player.displayClientMessage(
                        Component.literal("§b§l🌀 ¡ATAQUE GIRATORIO! §fObjetivos alcanzados: §e" + targets.size()),
                        true
                );
            });
        });
        ctx.get().setPacketHandled(true);
    }
}