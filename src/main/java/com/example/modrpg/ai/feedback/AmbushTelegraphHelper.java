package com.example.modrpg.ai.feedback;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gestiona el pre-aviso sensorial de las emboscadas y la regla de piedad (Fail-Safe)
 * para garantizar que el combate sea aterrador pero 100% justo.
 */
public class AmbushTelegraphHelper {

    /**
     * LEY 1: Pre-Aviso de 1.5 segundos.
     * Genera polvo de grava cayendo del techo y un crujido sutil que alerta al jugador.
     */
    public static void triggerAmbushPreCue(ServerLevel level, ServerPlayer player) {
        Vec3 pos = player.position();

        // 1. Sonido de grava quebrándose sobre su cabeza y siseo lejano
        level.playSound(null, pos.x, pos.y + 2.0, pos.z,
                SoundEvents.GRAVEL_BREAK, SoundSource.AMBIENT, 1.2f, 0.75f);
        level.playSound(null, pos.x, pos.y + 1.0, pos.z,
                SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.4f, 1.8f);

        // 2. Partículas de polvo de roca desmoronándose del techo
        BlockParticleOption gravelDust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.defaultBlockState());
        level.sendParticles(gravelDust, pos.x, pos.y + 2.4, pos.z, 25, 0.6, 0.1, 0.6, 0.05);

        // 3. Aviso sutil en el Action Bar
        player.displayClientMessage(
                Component.literal("§8§o[Crujido sutil en el techo... Algo se aproxima]"),
                true
        );
    }

    /**
     * LEY 3: Regla de Piedad (Fail-Safe).
     * Si la vida del jugador cae a <= 3 corazones (6.0 HP), los enemigos pausan su ofensiva
     * 2 segundos para retroceder y burlarse, evitando la muerte injusta instantánea.
     */
    public static void triggerMercyTaunt(ServerLevel level, Mob mob, ServerPlayer player) {
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.VINDICATOR_CELEBRATE, SoundSource.HOSTILE, 1.0f, 1.2f);

        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 3, 0.2, 0.2, 0.2, 0.0);

        player.displayClientMessage(
                Component.literal("§c[Hostil] §e«¡Míralo sangrar! ¡Acorraladlo!» §7(Ventana de escape)"),
                true
        );
    }
}