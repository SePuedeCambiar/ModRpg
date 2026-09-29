package com.example.modrpg.ai.director;

import com.example.modrpg.skills.PlayerSkillsProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestiona el Medidor de Estrés S(t) in-memory para cada jugador en el servidor.
 * Se actualiza 1 vez cada 20 ticks (1 segundo) para garantizar cero lag.
 */
public class PlayerStressTracker {

    public enum StressTier {
        CALM("§a[CALMA]", 0.0f, 0.30f),
        ALERT("§e[ALERTA]", 0.30f, 0.70f),
        STRESSED("§6[ESTRÉS ELEVADO]", 0.70f, 0.90f),
        CRITICAL_CLIMAX("§4§l[CLÍMAX CRÍTICO]", 0.90f, 1.0f);

        private final String badge;
        private final float min;
        private final float max;

        StressTier(String badge, float min, float max) {
            this.badge = badge;
            this.min = min;
            this.max = max;
        }

        public String getBadge() { return badge; }
    }

    public static class StressData {
        private float currentStress = 0.10f; // Empieza con una tensión base de 10%
        private int ticksSinceLastDamage = 200;

        public float getStress() { return currentStress; }

        public void setStress(float value) {
            this.currentStress = Math.max(0.0f, Math.min(1.0f, value));
        }

        public void modify(float delta) {
            setStress(this.currentStress + delta);
        }

        public void recordDamage() {
            this.ticksSinceLastDamage = 0;
            modify(StressStimulus.BURST_DAMAGE.getDeltaPerSecond());
        }

        public void tickDamageTimer() {
            if (ticksSinceLastDamage < 600) {
                ticksSinceLastDamage += 20;
            }
        }
    }

    private static final Map<UUID, StressData> PLAYER_STRESS_MAP = new ConcurrentHashMap<>();

    public static StressData getData(UUID playerUUID) {
        return PLAYER_STRESS_MAP.computeIfAbsent(playerUUID, k -> new StressData());
    }

    public static float getStress(ServerPlayer player) {
        return (player != null) ? getData(player.getUUID()).getStress() : 0.0f;
    }

    public static StressTier getStressTier(float stress) {
        if (stress < 0.30f) return StressTier.CALM;
        if (stress < 0.70f) return StressTier.ALERT;
        if (stress < 0.90f) return StressTier.STRESSED;
        return StressTier.CRITICAL_CLIMAX;
    }

    public static void onPlayerDamaged(ServerPlayer player, float damageAmount) {
        if (player == null || player.level().isClientSide()) return;
        getData(player.getUUID()).recordDamage();
    }

    /**
     * Ciclo de evaluación ejecutado cada segundo (20 ticks).
     */
    public static void tick(ServerPlayer player) {
        if (player == null || !player.isAlive()) return;

        StressData data = getData(player.getUUID());
        data.tickDamageTimer();

        float deltaThisSecond = 0.0f;

        // 1. Salud comprometida (< 40%)
        float hpRatio = player.getHealth() / player.getMaxHealth();
        if (hpRatio < 0.40f) {
            deltaThisSecond += StressStimulus.LOW_HEALTH.getDeltaPerSecond();
        }

        // 2. Reserva de Maná agotada (< 20%)
        var skillsOpt = player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).resolve();
        if (skillsOpt.isPresent()) {
            var skills = skillsOpt.get();
            float manaRatio = skills.getCurrentMana() / skills.getMaxMana();
            if (manaRatio < 0.20f) {
                deltaThisSecond += StressStimulus.LOW_MANA.getDeltaPerSecond();
            }
        }

        // 3. Nivel de luz y profundidad opresiva
        BlockPos playerPos = player.blockPosition();
        int lightLevel = player.level().getRawBrightness(playerPos, 0);

        if (lightLevel <= 4) {
            deltaThisSecond += StressStimulus.DARKNESS.getDeltaPerSecond();
        }
        if (player.getY() < 0) {
            deltaThisSecond += StressStimulus.DEEP_CAVE.getDeltaPerSecond();
        }

        // 4. Proximidad de Monstruos Hostiles
        AABB scanZone = player.getBoundingBox().inflate(15.0);
        List<Monster> nearbyMonsters = player.serverLevel().getEntitiesOfClass(
                Monster.class, scanZone, Monster::isAlive
        );

        boolean hasCloseHostile = false;
        for (Monster monster : nearbyMonsters) {
            double distSq = player.distanceToSqr(monster);
            if (distSq < 36.0) { // Menos de 6 bloques
                hasCloseHostile = true;
                break;
            }
        }

        if (hasCloseHostile) {
            deltaThisSecond += StressStimulus.HOSTILE_PROXIMITY_CLOSE.getDeltaPerSecond();
        } else if (!nearbyMonsters.isEmpty()) {
            deltaThisSecond += StressStimulus.HOSTILE_PROXIMITY_FAR.getDeltaPerSecond();
        }

        // 5. Factores de Alivio y Relajación (Decay)
        if (nearbyMonsters.isEmpty() && data.ticksSinceLastDamage >= 100) {
            if (lightLevel >= 12) {
                // Zona segura iluminada / Base
                deltaThisSecond += StressStimulus.DECAY_SAFE_ZONE.getDeltaPerSecond();
            } else {
                // Relajación natural fuera de peligro
                deltaThisSecond += StressStimulus.DECAY_PEACEFUL.getDeltaPerSecond();
            }
        }

        // Aplicar cambio
        data.modify(deltaThisSecond);
    }

    public static void clearPlayer(UUID playerUUID) {
        PLAYER_STRESS_MAP.remove(playerUUID);
    }
}