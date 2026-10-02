package com.example.modrpg.ai.feedback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Gestiona el pre-aviso sensorial de las emboscadas y la regla de piedad (Fail-Safe).
 * SPRINT 4 FIX: Detección vertical 3D de techo para adaptar el telegrafiado
 * dinámicamente entre cuevas/interiores y campos abiertos/llanuras.
 */
public class AmbushTelegraphHelper {

    /**
     * LEY 1: Pre-Aviso de 1.5 segundos.
     * Si hay techo, genera desprendimiento del material real del techo.
     * Si es campo abierto, genera vibración sónica y polvo a ras de suelo.
     */
    public static void triggerAmbushPreCue(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null || !player.isAlive()) return;

        Vec3 pos = player.position();
        BlockPos headPos = player.blockPosition().above();

        // 1. Raycast vertical hacia arriba (hasta 12 bloques) para buscar techo físico
        BlockPos ceilingPos = null;
        BlockState ceilingState = null;

        for (int dy = 1; dy <= 12; dy++) {
            BlockPos check = headPos.above(dy);
            BlockState state = level.getBlockState(check);
            if (state.blocksMotion() && !state.isAir()) {
                ceilingPos = check;
                ceilingState = state;
                break;
            }
        }

        if (ceilingPos != null && ceilingState != null) {
            // =========================================================================
            // CASO A: CUEVAS, MINAS E INTERIORES (Techo físico detectado)
            // =========================================================================
            double ceilingY = ceilingPos.getY() - 0.1;

            // Sonido de quebrazón usando el material real del bloque superior (roca, deepslate, madera...)
            level.playSound(null, ceilingPos.getX() + 0.5, ceilingY, ceilingPos.getZ() + 0.5,
                    ceilingState.getSoundType().getBreakSound(), SoundSource.AMBIENT, 1.2f, 0.75f);
            level.playSound(null, pos.x, pos.y + 1.0, pos.z,
                    SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.35f, 1.8f);

            // Partículas desmoronándose directamente desde la cara inferior del techo
            BlockParticleOption ceilingDust = new BlockParticleOption(ParticleTypes.FALLING_DUST, ceilingState);
            level.sendParticles(ceilingDust, pos.x, ceilingY, pos.z, 28, 0.8, 0.1, 0.8, 0.05);

            player.displayClientMessage(
                    Component.literal("§8§o[Crujido sutil en el techo... Algo se aproxima]"),
                    true
            );
        } else {
            // =========================================================================
            // CASO B: LLANURAS, DESIERTOS Y MONTANYAS ABIERTAS (Sin techo / Cielo libre)
            // =========================================================================
            // Sonido de temblor terrestre y vibración de suelo
            level.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ROOTED_DIRT_BREAK, SoundSource.AMBIENT, 1.4f, 0.6f);
            level.playSound(null, pos.x, pos.y + 0.5, pos.z,
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 1.0f, 1.5f);

            // Partículas de niebla y polvo a ras de suelo simulando aproximación oculta
            level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.1, pos.z, 20, 1.2, 0.1, 1.2, 0.02);
            level.sendParticles(ParticleTypes.SCULK_SOUL, pos.x, pos.y + 0.1, pos.z, 6, 0.8, 0.1, 0.8, 0.02);

            player.displayClientMessage(
                    Component.literal("§8§o[Un temblor sutil recorre el suelo... El aire se congela]"),
                    true
            );
        }
    }

    /**
     * LEY 3: Regla de Piedad (Fail-Safe).
     * Si la vida del jugador cae a <= 3 corazones (6.0 HP), los enemigos pausan su ofensiva
     * 2 segundos para retroceder y burlarse, evitando la muerte injusta instantánea.
     */
    public static void triggerMercyTaunt(ServerLevel level, Mob mob, ServerPlayer player) {
        if (level == null || mob == null || player == null) return;

        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.VINDICATOR_CELEBRATE, SoundSource.HOSTILE, 1.0f, 1.2f);

        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 3, 0.2, 0.2, 0.2, 0.0);

        player.displayClientMessage(
                Component.literal("§c[Hostil] §e«¡Míralo sangrar! ¡Acorraladlo!» §7(Ventana de escape)"),
                true
        );
    }
}