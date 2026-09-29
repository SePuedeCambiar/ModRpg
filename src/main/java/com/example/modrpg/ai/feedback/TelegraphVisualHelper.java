package com.example.modrpg.ai.feedback;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;

/**
 * Motor geométrico de partículas para ventanas de reacción y combate justo.
 * - Amarillo: Interrumpible con golpe fuerte (Stagger).
 * - Rojo: Ataque pesado imbloqueable (Esquiva obligatoria con Dash).
 * - Aturdimiento: Efecto de estrellas giratorias sobre la cabeza.
 */
public class TelegraphVisualHelper {

    /**
     * Dibuja un halo rotatorio dorado/eléctrico que avisa que el ataque puede cancelarse con un contraataque.
     */
    public static void renderYellowInterruptible(ServerLevel level, Mob mob, int chargeTicks, int maxTicks) {
        double headY = mob.getEyeY() + 0.35;
        double radius = 0.65;
        int points = 8;
        double rotationOffset = (mob.tickCount * 0.25);

        // Halo de partículas rotando alrededor de la cabeza
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI / points) * i + rotationOffset;
            double px = mob.getX() + Math.cos(angle) * radius;
            double pz = mob.getZ() + Math.sin(angle) * radius;

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, headY, pz, 1, 0, 0, 0, 0);
        }

        // Runas de carga concentrándose
        level.sendParticles(ParticleTypes.ENCHANT, mob.getX(), headY + 0.1, mob.getZ(), 2, 0.2, 0.1, 0.2, 0.05);

        // Sonido ascendente de telegrafiado cada 6 ticks
        if (chargeTicks % 6 == 0) {
            float pitchProgress = 1.0f + ((float) chargeTicks / maxTicks) * 0.8f; // Escala de 1.0 a 1.8
            level.playSound(null, mob.getX(), headY, mob.getZ(),
                    SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.HOSTILE, 0.6f, pitchProgress);
        }
    }

    /**
     * Dibuja un aura roja volcánica e imbloqueable. Advierte al jugador que el escudo fallará.
     */
    public static void renderRedUnblockable(ServerLevel level, Mob mob, int chargeTicks, int maxTicks) {
        double baseY = mob.getY();

        // 1. Fuego a los pies y humo pesado
        level.sendParticles(ParticleTypes.FLAME, mob.getX(), baseY + 0.1, mob.getZ(), 4, 0.35, 0.1, 0.35, 0.02);
        level.sendParticles(ParticleTypes.SMOKE, mob.getX(), baseY + 0.2, mob.getZ(), 3, 0.25, 0.1, 0.25, 0.01);
        level.sendParticles(ParticleTypes.LAVA, mob.getX(), baseY + 0.4, mob.getZ(), 1, 0.1, 0.1, 0.1, 0.0);

        // 2. Destello amenazante en los ojos
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getEyeY() + 0.2, mob.getZ(), 1, 0.15, 0.1, 0.15, 0.0);

        // 3. Pulso cardíaco ominoso cada 8 ticks
        if (chargeTicks % 8 == 0) {
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.2f, 1.4f);
        }
    }

    /**
     * Efecto masivo cuando el jugador interrumpe el casteo (Rompe-Postura exitoso).
     */
    public static void renderStaggerBurst(ServerLevel level, Mob mob) {
        level.sendParticles(ParticleTypes.FLASH, mob.getX(), mob.getEyeY(), mob.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getEyeY(), mob.getZ(), 30, 0.4, 0.4, 0.4, 0.25);
        level.sendParticles(ParticleTypes.SONIC_BOOM, mob.getX(), mob.getEyeY(), mob.getZ(), 1, 0, 0, 0, 0);

        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.4f, 0.75f);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 1.6f);
    }

    /**
     * Halo continuo de estrellas de mareo mientras el mob sufre aturdimiento.
     */
    public static void renderStaggerLoop(ServerLevel level, Mob mob) {
        double headY = mob.getEyeY() + 0.4;
        double radius = 0.45;
        double angle = mob.tickCount * 0.4;

        double px = mob.getX() + Math.cos(angle) * radius;
        double pz = mob.getZ() + Math.sin(angle) * radius;

        level.sendParticles(ParticleTypes.CRIT, px, headY, pz, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.WAX_OFF, mob.getX(), headY, mob.getZ(), 1, 0.1, 0.1, 0.1, 0.02);
    }
}