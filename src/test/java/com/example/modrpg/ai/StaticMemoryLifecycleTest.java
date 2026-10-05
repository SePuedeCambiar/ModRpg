package com.example.modrpg.ai;

import com.example.modrpg.ai.director.AudioFootprintTracker;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StaticMemoryLifecycleTest {

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("A4: SquadCoordinator y AudioFootprintTracker deben poder purgarse en ServerStoppedEvent")
    void testStaticDataMustBeCleanableOnServerShutdown() throws Exception {
        UUID mobId = UUID.randomUUID();

        // Acceder a los mapas internos estáticos mediante reflexión
        Field activeSquadsField = SquadCoordinator.class.getDeclaredField("ACTIVE_SQUADS");
        activeSquadsField.setAccessible(true);
        Map<UUID, Object> activeSquads = (Map<UUID, Object>) activeSquadsField.get(null);

        Field mobSquadMapField = SquadCoordinator.class.getDeclaredField("MOB_SQUAD_MAP");
        mobSquadMapField.setAccessible(true);
        Map<UUID, Object> mobSquadMap = (Map<UUID, Object>) mobSquadMapField.get(null);

        Field activePingsField = AudioFootprintTracker.class.getDeclaredField("ACTIVE_PINGS");
        activePingsField.setAccessible(true);
        Queue<Object> activePings = (Queue<Object>) activePingsField.get(null);

        // Insertar datos de prueba simulando una partida anterior
        SquadCoordinator.Squad dummySquad = new SquadCoordinator.Squad();
        dummySquad.memberUUIDs.add(mobId);
        activeSquads.put(dummySquad.squadId, dummySquad);
        mobSquadMap.put(mobId, dummySquad);

        AudioFootprintTracker.AcousticPing dummyPing = new AudioFootprintTracker.AcousticPing(
                UUID.randomUUID(), new Vec3(0, 0, 0), AudioFootprintTracker.NoiseCategory.EXPLOSION, 10
        );
        activePings.add(dummyPing);

        assertFalse(activeSquads.isEmpty(), "Precondición: Debe haber escuadrones cargados");
        assertFalse(activePings.isEmpty(), "Precondición: Debe haber pings de audio registrados");

        // Comprobar si existen métodos para limpiar la memoria estática entre mundos
        boolean hasSquadClearMethod = false;
        try {
            SquadCoordinator.class.getMethod("clearAll");
            hasSquadClearMethod = true;
        } catch (NoSuchMethodException ignored) {}

        boolean hasAudioClearMethod = false;
        try {
            AudioFootprintTracker.class.getMethod("clearAll");
            hasAudioClearMethod = true;
        } catch (NoSuchMethodException ignored) {}

        assertTrue(
                hasSquadClearMethod && hasAudioClearMethod,
                "FALLO (A4): SquadCoordinator y/o AudioFootprintTracker no tienen un método público clearAll() " +
                        "para ser invocado en ServerStoppedEvent. En Singleplayer, los escuadrones y pings del mundo anterior quedan en la JVM."
        );
    }
}