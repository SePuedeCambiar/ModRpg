package com.example.modrpg.skills;

import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class PlayerSkills {

    public static final int MAX_LOADOUT_SLOTS = 8;

    // =========================================================================
    // ESTRUCTURAS DE DATOS
    // =========================================================================
    private final Map<ResourceLocation, Integer> branchLevels = new HashMap<>();
    private final Set<ResourceLocation> unlockedNodes = new HashSet<>();
    private final Map<ResourceLocation, Integer> practiceCounters = new HashMap<>();
    private final Map<ResourceLocation, Integer> cooldowns = new HashMap<>();

    // Ranuras activas de combate (Loadout)
    private final List<ResourceLocation> equippedSkills = new ArrayList<>();

    // Estados transitorios de combate
    private boolean ultimateCharged = false;
    private int dashIFrameTicks = 0;

    public PlayerSkills() {}

    // =========================================================================
    // GESTIÓN DE RAMAS Y NIVELES
    // =========================================================================
    public int getBranchLevel(ResourceLocation branchId) {
        return branchLevels.getOrDefault(branchId, 0);
    }

    public void setBranchLevel(ResourceLocation branchId, int level) {
        branchLevels.put(branchId, Math.max(0, Math.min(level, 100)));
    }

    public void addBranchLevel(ResourceLocation branchId, int amount) {
        setBranchLevel(branchId, getBranchLevel(branchId) + amount);
    }

    public Map<ResourceLocation, Integer> getAllBranchLevels() {
        return Collections.unmodifiableMap(branchLevels);
    }

    // =========================================================================
    // GESTIÓN DE NODOS Y DESBLOQUEOS
    // =========================================================================
    public boolean isNodeUnlocked(ResourceLocation nodeId) {
        return unlockedNodes.contains(nodeId);
    }

    public void unlockNode(ResourceLocation nodeId) {
        unlockedNodes.add(nodeId);
        // Si hay espacio en el loadout y es activa, equiparla automáticamente
        if (equippedSkills.size() < MAX_LOADOUT_SLOTS && !equippedSkills.contains(nodeId)) {
            equippedSkills.add(nodeId);
        }
    }

    public void lockNode(ResourceLocation nodeId) {
        unlockedNodes.remove(nodeId);
        equippedSkills.remove(nodeId);
    }

    public Set<ResourceLocation> getUnlockedNodes() {
        return Collections.unmodifiableSet(unlockedNodes);
    }

    // =========================================================================
    // GESTIÓN DE LOADOUT (HABILIDADES EQUIPADAS)
    // =========================================================================
    public List<ResourceLocation> getEquippedSkills() {
        return Collections.unmodifiableList(equippedSkills);
    }

    public boolean equipSkill(int slot, ResourceLocation skillId) {
        if (!isNodeUnlocked(skillId)) return false;
        if (slot < 0 || slot >= MAX_LOADOUT_SLOTS) return false;

        // Evitar duplicados
        equippedSkills.remove(skillId);

        if (slot < equippedSkills.size()) {
            equippedSkills.set(slot, skillId);
        } else {
            equippedSkills.add(skillId);
        }
        return true;
    }

    public void unequipSkill(int slot) {
        if (slot >= 0 && slot < equippedSkills.size()) {
            equippedSkills.remove(slot);
        }
    }

    public boolean isSkillEquipped(ResourceLocation skillId) {
        return equippedSkills.contains(skillId);
    }

    public void setEquippedSkills(List<ResourceLocation> skills) {
        this.equippedSkills.clear();
        for (ResourceLocation rl : skills) {
            if (this.equippedSkills.size() >= MAX_LOADOUT_SLOTS) break;
            if (rl != null && !this.equippedSkills.contains(rl)) {
                this.equippedSkills.add(rl);
            }
        }
    }

    // =========================================================================
    // CONTADORES DE PRÁCTICA
    // =========================================================================
    public int getPractice(ResourceLocation counterId) {
        return practiceCounters.getOrDefault(counterId, 0);
    }

    public void addPractice(ResourceLocation counterId, int amount) {
        practiceCounters.put(counterId, getPractice(counterId) + amount);
    }

    public void setPractice(ResourceLocation counterId, int amount) {
        practiceCounters.put(counterId, Math.max(0, amount));
    }

    public Map<ResourceLocation, Integer> getAllPracticeCounters() {
        return Collections.unmodifiableMap(practiceCounters);
    }

    // =========================================================================
    // COOLDOWNS / ENFRIAMIENTOS
    // =========================================================================
    public int getCooldown(ResourceLocation skillId) {
        return cooldowns.getOrDefault(skillId, 0);
    }

    public void setCooldown(ResourceLocation skillId, int ticks) {
        if (ticks > 0) {
            cooldowns.put(skillId, ticks);
        } else {
            cooldowns.remove(skillId);
        }
    }

    public boolean hasCooldown(ResourceLocation skillId) {
        return getCooldown(skillId) > 0;
    }

    public Map<ResourceLocation, Integer> getAllCooldowns() {
        return Collections.unmodifiableMap(cooldowns);
    }

    public void tickCooldowns() {
        if (cooldowns.isEmpty()) return;
        cooldowns.entrySet().removeIf(entry -> {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) return true;
            entry.setValue(remaining);
            return false;
        });
    }

    // =========================================================================
    // ESTADOS ESPECIALES (I-Frames y Definitiva)
    // =========================================================================
    public boolean isUltimateCharged() { return ultimateCharged; }
    public void setUltimateCharged(boolean charged) { this.ultimateCharged = charged; }

    public boolean hasDashIFrames() { return dashIFrameTicks > 0; }
    public void setDashIFrames(int ticks) { this.dashIFrameTicks = Math.max(0, ticks); }
    public void tickIFrames() {
        if (dashIFrameTicks > 0) dashIFrameTicks--;
    }

    // =========================================================================
    // SINCRONIZACIÓN Y CLONACIÓN
    // =========================================================================
    public void replaceAll(Map<ResourceLocation, Integer> branches,
                           Set<ResourceLocation> nodes,
                           Map<ResourceLocation, Integer> counters,
                           Map<ResourceLocation, Integer> cds,
                           boolean ultCharged,
                           List<ResourceLocation> equipped) {
        this.branchLevels.clear();
        this.branchLevels.putAll(branches);

        this.unlockedNodes.clear();
        this.unlockedNodes.addAll(nodes);

        this.practiceCounters.clear();
        this.practiceCounters.putAll(counters);

        this.cooldowns.clear();
        this.cooldowns.putAll(cds);

        this.ultimateCharged = ultCharged;

        this.equippedSkills.clear();
        this.equippedSkills.addAll(equipped);
    }

    public void copyFrom(PlayerSkills source) {
        this.branchLevels.clear();
        this.branchLevels.putAll(source.branchLevels);

        this.unlockedNodes.clear();
        this.unlockedNodes.addAll(source.unlockedNodes);

        this.practiceCounters.clear();
        this.practiceCounters.putAll(source.practiceCounters);

        this.cooldowns.clear();
        this.cooldowns.putAll(source.cooldowns);

        this.equippedSkills.clear();
        this.equippedSkills.addAll(source.equippedSkills);

        this.ultimateCharged = source.ultimateCharged;
        this.dashIFrameTicks = 0;
    }

    // =========================================================================
    // SERIALIZACIÓN NBT
    // =========================================================================
    public void saveNBTData(CompoundTag nbt) {
        CompoundTag branchesTag = new CompoundTag();
        branchLevels.forEach((id, lvl) -> branchesTag.putInt(id.toString(), lvl));
        nbt.put("BranchLevels", branchesTag);

        ListTag nodesTag = new ListTag();
        unlockedNodes.forEach(id -> nodesTag.add(StringTag.valueOf(id.toString())));
        nbt.put("UnlockedNodes", nodesTag);

        CompoundTag practiceTag = new CompoundTag();
        practiceCounters.forEach((id, count) -> practiceTag.putInt(id.toString(), count));
        nbt.put("PracticeCounters", practiceTag);

        CompoundTag cdTag = new CompoundTag();
        cooldowns.forEach((id, cd) -> cdTag.putInt(id.toString(), cd));
        nbt.put("Cooldowns", cdTag);

        ListTag loadoutTag = new ListTag();
        equippedSkills.forEach(id -> loadoutTag.add(StringTag.valueOf(id.toString())));
        nbt.put("EquippedSkills", loadoutTag);

        nbt.putBoolean("UltimateCharged", ultimateCharged);
    }

    public void loadNBTData(CompoundTag nbt) {
        branchLevels.clear();
        if (nbt.contains("BranchLevels", Tag.TAG_COMPOUND)) {
            CompoundTag branchesTag = nbt.getCompound("BranchLevels");
            for (String key : branchesTag.getAllKeys()) {
                ResourceLocation rl = ResourceLocation.tryParse(key);
                if (rl != null) branchLevels.put(rl, branchesTag.getInt(key));
            }
        }

        unlockedNodes.clear();
        if (nbt.contains("UnlockedNodes", Tag.TAG_LIST)) {
            ListTag nodesTag = nbt.getList("UnlockedNodes", Tag.TAG_STRING);
            for (int i = 0; i < nodesTag.size(); i++) {
                ResourceLocation rl = ResourceLocation.tryParse(nodesTag.getString(i));
                if (rl != null) unlockedNodes.add(rl);
            }
        }

        practiceCounters.clear();
        if (nbt.contains("PracticeCounters", Tag.TAG_COMPOUND)) {
            CompoundTag practiceTag = nbt.getCompound("PracticeCounters");
            for (String key : practiceTag.getAllKeys()) {
                ResourceLocation rl = ResourceLocation.tryParse(key);
                if (rl != null) practiceCounters.put(rl, practiceTag.getInt(key));
            }
        }

        cooldowns.clear();
        if (nbt.contains("Cooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag cdTag = nbt.getCompound("Cooldowns");
            for (String key : cdTag.getAllKeys()) {
                ResourceLocation rl = ResourceLocation.tryParse(key);
                if (rl != null) cooldowns.put(rl, cdTag.getInt(key));
            }
        }

        equippedSkills.clear();
        if (nbt.contains("EquippedSkills", Tag.TAG_LIST)) {
            ListTag loadoutTag = nbt.getList("EquippedSkills", Tag.TAG_STRING);
            for (int i = 0; i < loadoutTag.size(); i++) {
                ResourceLocation rl = ResourceLocation.tryParse(loadoutTag.getString(i));
                if (rl != null && !equippedSkills.contains(rl)) {
                    equippedSkills.add(rl);
                }
            }
        }

        this.ultimateCharged = nbt.getBoolean("UltimateCharged");
        this.dashIFrameTicks = 0;
    }
}