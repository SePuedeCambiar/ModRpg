package com.example.modrpg.ai.feedback;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Motor de Radio-Táctica F.E.A.R. (Enriquecido):
 * Proyecta barks aleatorios y contextuales con audio direccional.
 */
public class SquadBarkManager {

    public enum BarkType {
        FLANKING(
                new String[]{
                        "§6[Escuadrón] §e¡Rodeando por el flanco!",
                        "§6[Escuadrón] §e¡Buscando su punto ciego, no lo dejen girar!",
                        "§6[Escuadrón] §e¡Me muevo por el lateral, cubran!",
                        "§6[Escuadrón] §e¡Flanco despejado, cerrando ángulo!"
                },
                1.75f,
                80
        ),
        SUPPRESSION_CALL(
                new String[]{
                        "§c[Vanguardia] §f¡Fijen al blanco! ¡No lo dejen asomar!",
                        "§c[Tirador] §f¡Fuego de contención! ¡Mantengan la cabeza abajo!",
                        "§c[Tirador] §f¡Saturando su posición, avancen ahora!",
                        "§c[Escuadrón] §f¡Que no respire! ¡Descarguen ráfagas!"
                },
                1.10f,
                100
        ),
        PEEL_REQUEST(
                new String[]{
                        "§d[Hechicero] §c¡Me tienen acorralado! ¡A mí!",
                        "§d[Hechicero] §c¡El objetivo está encima mío! ¡Sáquenmelo!",
                        "§d[Soporte] §c¡Auxilio en retaguardia! ¡Intervengan!",
                        "§d[Hechicero] §c¡Rompió la distancia, auxilio!"
                },
                1.55f,
                60
        ),
        VANGUARD_INTERCEPT(
                new String[]{
                        "§6[Vanguardia] §4¡Atrás, insecto! ¡Enfócame a mí!",
                        "§6[Vanguardia] §4¡No tocarás al taumaturgo! ¡A través de mí!",
                        "§6[Vanguardia] §4¡Impacto de choque! ¡Retrocede!",
                        "§6[Vanguardia] §4¡Línea frontal reforzada! ¡Atrás!"
                },
                0.80f,
                70
        ),
        MORALE_BREAK(
                new String[]{
                        "§4§l¡LÍDER CAÍDO! §7¡El escuadrón entra en pánico!",
                        "§4§l¡EL COMANDANTE CAYÓ! §7¡Rompan formación, dispersión!",
                        "§4§l¡NOS ESTÁ MASACRANDO! §7¡Retrocedan a las sombras!",
                        "§4§l¡LÍDER ELIMINADO! §7¡Cada uno por su cuenta!"
                },
                0.60f,
                120
        ),
        SEARCHING(
                new String[]{
                        "§7[Escuadrón] §8¿A dónde se fue? ¡Revisen las esquinas!",
                        "§7[Escuadrón] §8¡Perdimos visual! ¡Atentos al techo y sombras!",
                        "§7[Escuadrón] §8El rastro se enfrió... Cuidado con emboscadas.",
                        "§7[Escuadrón] §8¡Silencio! Huelo su magia cerca..."
                },
                0.95f,
                90
        ),
        STAGGER_REACTION(
                new String[]{
                        "§e[Escuadrón] §c¡Le rompieron la postura al aliado! ¡Protéjanlo!",
                        "§e[Escuadrón] §c¡Caster aturdido! ¡Cierren filas!",
                        "§e[Escuadrón] §c¡Corte limpio del enemigo! ¡No lo dejen rematar!"
                },
                1.40f,
                90
        ),
        CONTACT(
                new String[]{
                        "§c[Escuadrón] §e¡Contacto visual! ¡Inicien protocolo de asedio!",
                        "§c[Escuadrón] §e¡Blanco localizado! ¡Abran fuego coordinado!",
                        "§c[Escuadrón] §e¡Ahí está! ¡Que no llegue a cobertura!"
                },
                1.25f,
                120
        );

        private final String[] messages;
        private final float soundPitch;
        private final int cooldownTicks;

        BarkType(String[] messages, float soundPitch, int cooldownTicks) {
            this.messages = messages;
            this.soundPitch = soundPitch;
            this.cooldownTicks = cooldownTicks;
        }

        public String getRandomMessage(RandomSource random) {
            return messages[random.nextInt(messages.length)];
        }

        public float getSoundPitch() { return soundPitch; }
        public int getCooldownTicks() { return cooldownTicks; }
    }

    private static final Map<UUID, Map<BarkType, Long>> COOLDOWNS = new ConcurrentHashMap<>();

    public static void triggerBark(Mob emitter, BarkType type, ServerLevel level) {
        if (emitter == null || level.isClientSide()) return;
        if (type != BarkType.MORALE_BREAK && !emitter.isAlive()) return;

        long currentTick = level.getGameTime();
        UUID mobId = emitter.getUUID();

        COOLDOWNS.putIfAbsent(mobId, new ConcurrentHashMap<>());
        Map<BarkType, Long> mobCooldowns = COOLDOWNS.get(mobId);

        long lastTrigger = mobCooldowns.getOrDefault(type, -10000L);
        if (currentTick - lastTrigger < type.getCooldownTicks()) {
            return;
        }

        mobCooldowns.put(type, currentTick);

        if (COOLDOWNS.size() > 150) {
            COOLDOWNS.entrySet().removeIf(entry -> {
                Map<BarkType, Long> map = entry.getValue();
                return map.values().stream().allMatch(t -> (currentTick - t) > 600L);
            });
        }

        playBarkSound(emitter, type, level);

        AABB audienceZone = emitter.getBoundingBox().inflate(20.0);
        var nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class, audienceZone);

        // Mensaje aleatorio del pool
        String chosenMessage = type.getRandomMessage(emitter.getRandom());

        for (ServerPlayer player : nearbyPlayers) {
            // Se envía a Action Bar con prioridad elegante
            player.displayClientMessage(Component.literal(chosenMessage), true);
        }
    }

    private static void playBarkSound(Mob emitter, BarkType type, ServerLevel level) {
        double x = emitter.getX();
        double y = emitter.getY() + 1.2;
        double z = emitter.getZ();

        switch (type) {
            case FLANKING -> {
                level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_SNARE.get(), SoundSource.HOSTILE, 1.2f, type.getSoundPitch());
                level.playSound(null, x, y, z, SoundEvents.BAT_TAKEOFF, SoundSource.HOSTILE, 0.8f, 1.4f);
            }
            case SUPPRESSION_CALL -> {
                level.playSound(null, x, y, z, SoundEvents.RAID_HORN.get(), SoundSource.HOSTILE, 0.9f, type.getSoundPitch());
            }
            case PEEL_REQUEST -> {
                level.playSound(null, x, y, z, SoundEvents.GHAST_HURT, SoundSource.HOSTILE, 1.0f, type.getSoundPitch());
            }
            case VANGUARD_INTERCEPT -> {
                level.playSound(null, x, y, z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.2f, type.getSoundPitch());
                level.playSound(null, x, y, z, SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0f, 1.2f);
            }
            case MORALE_BREAK -> {
                level.playSound(null, x, y, z, SoundEvents.RAID_HORN.get(), SoundSource.HOSTILE, 1.4f, type.getSoundPitch());
                level.playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0f, 0.7f);
            }
            case SEARCHING -> {
                level.playSound(null, x, y, z, SoundEvents.VILLAGER_NO, SoundSource.HOSTILE, 0.8f, type.getSoundPitch());
            }
            case STAGGER_REACTION -> {
                level.playSound(null, x, y, z, SoundEvents.BLAZE_HURT, SoundSource.HOSTILE, 1.0f, type.getSoundPitch());
            }
            case CONTACT -> {
                level.playSound(null, x, y, z, SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.HOSTILE, 1.2f, 1.5f);
            }
        }
    }

    public static void clearMobMemory(UUID mobId) {
        if (mobId != null) {
            COOLDOWNS.remove(mobId);
        }
    }
}