package com.example.modrpg.skills;

import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class PlayerSkills {

    // =========================================================================
    // ESTRUCTURAS DINÁMICAS (Mapas y Conjuntos)
    // =========================================================================
    private final Map<ResourceLocation, Integer> branchLevels = new HashMap<>();
    private final Set<ResourceLocation> unlockedNodes = new HashSet<>();
    private final Map<ResourceLocation, Integer> practiceCounters = new HashMap<>();
    private final Map<ResourceLocation, Integer> cooldowns = new HashMap<>();

    // Estado para el golpe cargado de la definitiva
    private boolean ultimateCharged = false;

    // Contador de ticks de invulnerabilidad (i-frames del Dash)
    private int dashIFrameTicks = 0;

    public PlayerSkills() {}

    // =========================================================================
    // GESTIÓN DE RAMAS Y NIVELES (Genérico)
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
    // GESTIÓN DE NODOS Y HABILIDADES (Genérico)
    // =========================================================================

    public boolean isNodeUnlocked(ResourceLocation nodeId) {
        return unlockedNodes.contains(nodeId);
    }

    public void unlockNode(ResourceLocation nodeId) {
        unlockedNodes.add(nodeId);
    }

    public void lockNode(ResourceLocation nodeId) {
        unlockedNodes.remove(nodeId);
    }

    public Set<ResourceLocation> getUnlockedNodes() {
        return Collections.unmodifiableSet(unlockedNodes);
    }

    // =========================================================================
    // CONTADORES DE PRÁCTICA Y BAJAS (Genérico)
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
    // GESTIÓN DE COOLDOWNS / ENFRIAMIENTOS (Genérico)
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

    /**
     * Decrementa los enfriamientos activos cada tick del juego.
     */
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
    // ESTADOS ESPECIALES DE COMBATE (I-Frames y Definitiva)
    // =========================================================================

    public boolean isUltimateCharged() { return ultimateCharged; }
    public void setUltimateCharged(boolean charged) { this.ultimateCharged = charged; }

    public boolean hasDashIFrames() {
        return dashIFrameTicks > 0;
    }

    public void setDashIFrames(int ticks) {
        this.dashIFrameTicks = Math.max(0, ticks);
    }

    public void tickIFrames() {
        if (dashIFrameTicks > 0) {
            dashIFrameTicks--;
        }
    }

    // =========================================================================
    // SINCRONIZACIÓN Y CLONACIÓN (Seguro y Atómico)
    // =========================================================================

    /**
     * Reemplazo limpio y seguro de todos los datos recibidos desde el servidor.
     * Evita UnsupportedOperationException en colecciones unmodifiable.
     */
    public void replaceAll(Map<ResourceLocation, Integer> branches,
                           Set<ResourceLocation> nodes,
                           Map<ResourceLocation, Integer> counters,
                           Map<ResourceLocation, Integer> cds,
                           boolean ultCharged) {
        this.branchLevels.clear();
        this.branchLevels.putAll(branches);

        this.unlockedNodes.clear();
        this.unlockedNodes.addAll(nodes);

        this.practiceCounters.clear();
        this.practiceCounters.putAll(counters);

        this.cooldowns.clear();
        this.cooldowns.putAll(cds);

        this.ultimateCharged = ultCharged;
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

        this.ultimateCharged = source.ultimateCharged;
        this.dashIFrameTicks = 0; // Se reinicia al morir
    }

    // =========================================================================
    // SERIALIZACIÓN NBT (Guardado en el mundo)
    // =========================================================================

    public void saveNBTData(CompoundTag nbt) {
        // 1. Guardar ramas
        CompoundTag branchesTag = new CompoundTag();
        branchLevels.forEach((id, lvl) -> branchesTag.putInt(id.toString(), lvl));
        nbt.put("BranchLevels", branchesTag);

        // 2. Guardar habilidades desbloqueadas
        ListTag nodesTag = new ListTag();
        unlockedNodes.forEach(id -> nodesTag.add(StringTag.valueOf(id.toString())));
        nbt.put("UnlockedNodes", nodesTag);

        // 3. Guardar práctica
        CompoundTag practiceTag = new CompoundTag();
        practiceCounters.forEach((id, count) -> practiceTag.putInt(id.toString(), count));
        nbt.put("PracticeCounters", practiceTag);

        // 4. Guardar cooldowns
        CompoundTag cdTag = new CompoundTag();
        cooldowns.forEach((id, cd) -> cdTag.putInt(id.toString(), cd));
        nbt.put("Cooldowns", cdTag);

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

        this.ultimateCharged = nbt.getBoolean("UltimateCharged");
        this.dashIFrameTicks = 0;
    }

    // =========================================================================
    // MÉTODOS PUENTE TEMPORALES (Compatibilidad con GUI actual y Eventos)
    // =========================================================================

    public int getMeleeLevel() { return getBranchLevel(SkillRegistry.BRANCH_MELEE); }
    public void setMeleeLevel(int lvl) { setBranchLevel(SkillRegistry.BRANCH_MELEE, lvl); }
    public void addMeleeLevel(int amt) { addBranchLevel(SkillRegistry.BRANCH_MELEE, amt); }

    public int getRangedLevel() { return getBranchLevel(SkillRegistry.BRANCH_RANGED); }
    public void setRangedLevel(int lvl) { setBranchLevel(SkillRegistry.BRANCH_RANGED, lvl); }
    public void addRangedLevel(int amt) { addBranchLevel(SkillRegistry.BRANCH_RANGED, amt); }

    public int getMobilityLevel() { return getBranchLevel(SkillRegistry.BRANCH_MOBILITY); }
    public void setMobilityLevel(int lvl) { setBranchLevel(SkillRegistry.BRANCH_MOBILITY, lvl); }

    public int getMeleeKills() { return getPractice(SkillRegistry.COUNTER_MELEE_KILLS); }
    public void setMeleeKills(int k) { setPractice(SkillRegistry.COUNTER_MELEE_KILLS, k); }
    public void addMeleeKill() { addPractice(SkillRegistry.COUNTER_MELEE_KILLS, 1); }

    public int getRangedKills() { return getPractice(SkillRegistry.COUNTER_RANGED_KILLS); }
    public void setRangedKills(int k) { setPractice(SkillRegistry.COUNTER_RANGED_KILLS, k); }
    public void addRangedKill() { addPractice(SkillRegistry.COUNTER_RANGED_KILLS, 1); }

    public boolean hasDoubleAttack() { return isNodeUnlocked(SkillRegistry.NODE_DOUBLE_ATTACK) || getMeleeLevel() >= 4; }
    public void setDoubleAttack(boolean u) { if (u) unlockNode(SkillRegistry.NODE_DOUBLE_ATTACK); else lockNode(SkillRegistry.NODE_DOUBLE_ATTACK); }

    public boolean hasSpinAttack() { return isNodeUnlocked(SkillRegistry.NODE_HEAVY_TORNADO) || getMeleeLevel() >= 10; }
    public void setSpinAttack(boolean u) { if (u) unlockNode(SkillRegistry.NODE_HEAVY_TORNADO); else lockNode(SkillRegistry.NODE_HEAVY_TORNADO); }

    public boolean hasCapstoneMelee() { return isNodeUnlocked(SkillRegistry.NODE_ULTRACUT) || getMeleeLevel() >= 100; }
    public void setCapstoneMelee(boolean u) { if (u) unlockNode(SkillRegistry.NODE_ULTRACUT); else lockNode(SkillRegistry.NODE_ULTRACUT); }

    public boolean hasTailwind() { return isNodeUnlocked(SkillRegistry.NODE_TAILWIND) || getRangedLevel() >= 5; }
    public void setTailwind(boolean u) { if (u) unlockNode(SkillRegistry.NODE_TAILWIND); else lockNode(SkillRegistry.NODE_TAILWIND); }

    public boolean hasHypersonicArrow() { return isNodeUnlocked(SkillRegistry.NODE_HYPERSONIC) || getRangedLevel() >= 50; }
    public void setHypersonicArrow(boolean u) { if (u) unlockNode(SkillRegistry.NODE_HYPERSONIC); else lockNode(SkillRegistry.NODE_HYPERSONIC); }

    public boolean hasHybridRangedMelee() { return isNodeUnlocked(SkillRegistry.NODE_HYBRID_HUNTER) || (getMeleeLevel() >= 25 && getRangedLevel() >= 25); }
    public void setHybridRangedMelee(boolean u) { if (u) unlockNode(SkillRegistry.NODE_HYBRID_HUNTER); else lockNode(SkillRegistry.NODE_HYBRID_HUNTER); }

    public int getSpinCooldown() { return getCooldown(SkillRegistry.NODE_HEAVY_TORNADO); }
    public void setSpinCooldown(int cd) { setCooldown(SkillRegistry.NODE_HEAVY_TORNADO, cd); }

    public int getUltimateCooldown() { return getCooldown(SkillRegistry.NODE_ULTRACUT); }
    public void setUltimateCooldown(int cd) { setCooldown(SkillRegistry.NODE_ULTRACUT, cd); }

    public void tickCooldown() { tickCooldowns(); }
}