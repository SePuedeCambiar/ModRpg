package com.example.modrpg.ai.nemesis;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda y carga el "Cuadro de Honor Némesis" en el archivo data/modrpg_nemesis.dat del mundo.
 */
public class NemesisSavedData extends SavedData {

    private static final String DATA_NAME = "modrpg_nemesis";
    private final Map<UUID, NemesisCaptain> captains = new ConcurrentHashMap<>();

    public static NemesisSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                NemesisSavedData::load,
                NemesisSavedData::new,
                DATA_NAME
        );
    }

    public void addOrUpdateCaptain(NemesisCaptain captain) {
        captains.put(captain.getCaptainUUID(), captain);
        setDirty();
    }

    public NemesisCaptain getCaptain(UUID uuid) {
        return captains.get(uuid);
    }

    public Collection<NemesisCaptain> getAllCaptains() {
        return Collections.unmodifiableCollection(captains.values());
    }

    public List<NemesisCaptain> getCaptainsWaitingRevenge() {
        List<NemesisCaptain> result = new ArrayList<>();
        for (NemesisCaptain c : captains.values()) {
            if (c.getStatus() == NemesisCaptain.Status.WAITING_REVENGE) {
                result.add(c);
            }
        }
        return result;
    }

    public int getActiveCaptainCount() {
        int count = 0;
        for (NemesisCaptain c : captains.values()) {
            if (c.getStatus() == NemesisCaptain.Status.STALKING) count++;
        }
        return count;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (NemesisCaptain c : captains.values()) {
            list.add(c.toNBT());
        }
        tag.put("Captains", list);
        return tag;
    }

    public static NemesisSavedData load(CompoundTag tag) {
        NemesisSavedData data = new NemesisSavedData();
        if (tag.contains("Captains", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Captains", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                NemesisCaptain captain = NemesisCaptain.fromNBT(list.getCompound(i));
                data.captains.put(captain.getCaptainUUID(), captain);
            }
        }
        return data;
    }
}