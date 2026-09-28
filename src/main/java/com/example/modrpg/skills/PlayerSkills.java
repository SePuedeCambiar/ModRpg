package com.example.modrpg.skills;

import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import com.example.modrpg.skills.magic.modular.CraftedSpell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class PlayerSkills {

    public static final int MAX_LOADOUT_SLOTS = 8;
    public static final int MAX_SPELL_MEMORY = 4;
    public static final float BASE_MANA_REGEN_PER_SEC = 2.0f;

    // Límites de Especialización (Sprint 4)
    public static final int CAP_BASE = 20;
    public static final int CAP_SECONDARY = 50;
    public static final int CAP_PRIMARY = 100;

    // =========================================================================
    // ESTRUCTURAS DE DATOS
    // =========================================================================
    private final Map<ResourceLocation, Integer> branchLevels = new HashMap<>();
    private final Set<ResourceLocation> unlockedNodes = new HashSet<>();
    private final Map<ResourceLocation, Integer> practiceCounters = new HashMap<>();
    private final Map<ResourceLocation, Integer> cooldowns = new HashMap<>();
    private final List<ResourceLocation> equippedSkills = new ArrayList<>();

    // Especializaciones elegidas
    private ResourceLocation primaryBranch = null;   // Maestría hasta Nvl 100
    private ResourceLocation secondaryBranch = null; // Sub-rama hasta Nvl 50

    // Pasivas conmutables y Hechizos
    private final Set<ResourceLocation> activeToggles = new HashSet<>();
    private boolean togglesForceDeactivated = false;
    private final CraftedSpell[] spellMemory = new CraftedSpell[MAX_SPELL_MEMORY];
    private ResourceLocation selectedSkill = null;

    // Sistema de Maná
    private float currentMana = 100.0f;
    private float maxMana = 100.0f;

    // Estados transitorios
    private boolean ultimateCharged = false;
    private int dashIFrameTicks = 0;

    public PlayerSkills() {}

    // =========================================================================
    // ESPECIALIZACIONES Y LÍMITES DE NIVEL (Sprint 4)
    // =========================================================================
    public ResourceLocation getPrimaryBranch() {
        return primaryBranch;
    }

    public void setPrimaryBranch(ResourceLocation branch) {
        this.primaryBranch = branch;
    }

    public ResourceLocation getSecondaryBranch() {
        return secondaryBranch;
    }

    public void setSecondaryBranch(ResourceLocation branch) {
        this.secondaryBranch = branch;
    }

    public int getMaxLevelForBranch(ResourceLocation branchId) {
        if (Objects.equals(this.primaryBranch, branchId)) {
            return CAP_PRIMARY; // 100
        }
        if (Objects.equals(this.secondaryBranch, branchId)) {
            return CAP_SECONDARY; // 50
        }
        return CAP_BASE; // 20
    }

    public void respecSpecializations() {
        this.primaryBranch = null;
        this.secondaryBranch = null;

        // Limita todas las ramas al tope base de 20
        for (Map.Entry<ResourceLocation, Integer> entry : branchLevels.entrySet()) {
            if (entry.getValue() > CAP_BASE) {
                entry.setValue(CAP_BASE);
            }
        }
    }

    // =========================================================================
    // MEMORIA DE HECHIZOS MODULARES
    // =========================================================================
    public CraftedSpell getSpell(int slot) {
        if (slot >= 0 && slot < MAX_SPELL_MEMORY) return spellMemory[slot];
        return null;
    }

    public void setSpell(int slot, CraftedSpell spell) {
        if (slot >= 0 && slot < MAX_SPELL_MEMORY) spellMemory[slot] = spell;
    }

    public CraftedSpell[] getAllSpells() {
        return spellMemory;
    }

    // =========================================================================
    // GESTIÓN DE PASIVAS CONMUTABLES
    // =========================================================================
    public boolean isToggleActive(ResourceLocation skillId) {
        return activeToggles.contains(skillId);
    }

    public void setToggleActive(ResourceLocation skillId, boolean active) {
        if (!isNodeUnlocked(skillId)) return;
        if (active) activeToggles.add(skillId);
        else activeToggles.remove(skillId);
    }

    public boolean toggleState(ResourceLocation skillId) {
        if (!isNodeUnlocked(skillId)) return false;
        if (isToggleActive(skillId)) {
            activeToggles.remove(skillId);
            return false;
        } else {
            activeToggles.add(skillId);
            return true;
        }
    }

    public Set<ResourceLocation> getActiveToggles() {
        return Collections.unmodifiableSet(activeToggles);
    }

    public boolean consumeTogglesForceDeactivated() {
        if (togglesForceDeactivated) {
            togglesForceDeactivated = false;
            return true;
        }
        return false;
    }

    // =========================================================================
    // HABILIDAD ACTIVA SELECCIONADA
    // =========================================================================
    public ResourceLocation getSelectedSkill() {
        if (selectedSkill != null && (!isNodeUnlocked(selectedSkill) || !isSkillEquipped(selectedSkill))) {
            selectedSkill = null;
        }
        if (selectedSkill == null && !equippedSkills.isEmpty()) {
            selectedSkill = equippedSkills.get(0);
        }
        return selectedSkill;
    }

    public void setSelectedSkill(ResourceLocation skillId) {
        this.selectedSkill = skillId;
    }

    // =========================================================================
    // SISTEMA DE MANÁ Y REGENERACIÓN
    // =========================================================================
    public float getCurrentMana() { return currentMana; }

    public float getMaxMana() {
        int magicLevel = getBranchLevel(SkillRegistry.BRANCH_MAGIC);
        return 100.0f + (magicLevel * 2.0f);
    }

    public void setMana(float mana) {
        this.currentMana = Math.max(0.0f, Math.min(mana, getMaxMana()));
    }

    public void restoreMana(float amount) {
        setMana(this.currentMana + amount);
    }

    public boolean consumeMana(float cost) {
        if (cost <= 0.0f) return true;
        if (this.currentMana >= cost) {
            this.currentMana -= cost;
            return true;
        }
        return false;
    }

    public float getManaRegenPerSecond() {
        int magicLevel = getBranchLevel(SkillRegistry.BRANCH_MAGIC);
        float multiplier = 1.0f + (magicLevel * 0.05f);
        return BASE_MANA_REGEN_PER_SEC * multiplier;
    }

    public void tickServerSide() {
        tickCooldowns();
        tickIFrames();

        if (!activeToggles.isEmpty()) {
            float totalDrainPerTick = 0.0f;
            for (ResourceLocation toggleId : activeToggles) {
                SkillNode node = SkillRegistry.get(toggleId);
                if (node != null && node.getSustainManaCost() > 0.0f) {
                    totalDrainPerTick += (node.getSustainManaCost() / 20.0f);
                }
            }

            if (totalDrainPerTick > 0.0f) {
                if (this.currentMana >= totalDrainPerTick) {
                    this.currentMana -= totalDrainPerTick;
                } else {
                    this.currentMana = 0.0f;
                    activeToggles.removeIf(id -> {
                        SkillNode node = SkillRegistry.get(id);
                        return node != null && node.getSustainManaCost() > 0.0f;
                    });
                    this.togglesForceDeactivated = true;
                }
            }
        }

        float regenPerTick = getManaRegenPerSecond() / 20.0f;
        restoreMana(regenPerTick);
    }

    // =========================================================================
    // GESTIÓN DE RAMAS Y NIVELES
    // =========================================================================
    public int getBranchLevel(ResourceLocation branchId) {
        return branchLevels.getOrDefault(branchId, 0);
    }

    public void setBranchLevel(ResourceLocation branchId, int level) {
        // La capability garantiza el límite físico absoluto entre 0 y 100
        branchLevels.put(branchId, Math.max(0, Math.min(level, CAP_PRIMARY)));
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
        if (equippedSkills.size() < MAX_LOADOUT_SLOTS && !equippedSkills.contains(nodeId)) {
            equippedSkills.add(nodeId);
            if (selectedSkill == null) selectedSkill = nodeId;
        }
    }

    public void lockNode(ResourceLocation nodeId) {
        unlockedNodes.remove(nodeId);
        equippedSkills.remove(nodeId);
        activeToggles.remove(nodeId);
        if (Objects.equals(selectedSkill, nodeId)) selectedSkill = null;
    }

    public Set<ResourceLocation> getUnlockedNodes() {
        return Collections.unmodifiableSet(unlockedNodes);
    }

    // =========================================================================
    // GESTIÓN DE LOADOUT
    // =========================================================================
    public List<ResourceLocation> getEquippedSkills() {
        return Collections.unmodifiableList(equippedSkills);
    }

    public boolean equipSkill(int slot, ResourceLocation skillId) {
        if (!isNodeUnlocked(skillId)) return false;
        if (slot < 0 || slot >= MAX_LOADOUT_SLOTS) return false;

        equippedSkills.remove(skillId);
        if (slot < equippedSkills.size()) {
            equippedSkills.set(slot, skillId);
        } else {
            equippedSkills.add(skillId);
        }
        if (selectedSkill == null) selectedSkill = skillId;
        return true;
    }

    public void unequipSkill(int slot) {
        if (slot >= 0 && slot < equippedSkills.size()) {
            ResourceLocation removed = equippedSkills.remove(slot);
            if (Objects.equals(selectedSkill, removed)) selectedSkill = null;
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
    // COOLDOWNS
    // =========================================================================
    public int getCooldown(ResourceLocation skillId) {
        return cooldowns.getOrDefault(skillId, 0);
    }

    public void setCooldown(ResourceLocation skillId, int ticks) {
        if (ticks > 0) cooldowns.put(skillId, ticks);
        else cooldowns.remove(skillId);
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
    // ESTADOS ESPECIALES
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
                           List<ResourceLocation> equipped,
                           float curMana,
                           float mXpMana,
                           ResourceLocation selected,
                           Set<ResourceLocation> toggles,
                           CraftedSpell[] spells,
                           ResourceLocation primary,
                           ResourceLocation secondary) {
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

        this.maxMana = mXpMana;
        this.currentMana = Math.max(0.0f, Math.min(curMana, mXpMana));
        this.selectedSkill = selected;

        this.activeToggles.clear();
        this.activeToggles.addAll(toggles);

        if (spells != null) {
            for (int i = 0; i < MAX_SPELL_MEMORY; i++) {
                this.spellMemory[i] = (i < spells.length) ? spells[i] : null;
            }
        }

        this.primaryBranch = primary;
        this.secondaryBranch = secondary;
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

        this.activeToggles.clear();
        this.activeToggles.addAll(source.activeToggles);

        for (int i = 0; i < MAX_SPELL_MEMORY; i++) {
            this.spellMemory[i] = source.spellMemory[i];
        }

        this.currentMana = source.currentMana;
        this.maxMana = source.maxMana;
        this.ultimateCharged = source.ultimateCharged;
        this.selectedSkill = source.selectedSkill;
        this.dashIFrameTicks = 0;

        this.primaryBranch = source.primaryBranch;
        this.secondaryBranch = source.secondaryBranch;
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

        ListTag togglesTag = new ListTag();
        activeToggles.forEach(id -> togglesTag.add(StringTag.valueOf(id.toString())));
        nbt.put("ActiveToggles", togglesTag);

        ListTag spellsTag = new ListTag();
        for (int i = 0; i < MAX_SPELL_MEMORY; i++) {
            if (spellMemory[i] != null) {
                CompoundTag spellEntry = spellMemory[i].toNBT();
                spellEntry.putInt("Slot", i);
                spellsTag.add(spellEntry);
            }
        }
        nbt.put("SpellMemory", spellsTag);

        if (selectedSkill != null) nbt.putString("SelectedSkill", selectedSkill.toString());
        if (primaryBranch != null) nbt.putString("PrimaryBranch", primaryBranch.toString());
        if (secondaryBranch != null) nbt.putString("SecondaryBranch", secondaryBranch.toString());

        nbt.putFloat("CurrentMana", currentMana);
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
                if (rl != null && !equippedSkills.contains(rl)) equippedSkills.add(rl);
            }
        }

        activeToggles.clear();
        if (nbt.contains("ActiveToggles", Tag.TAG_LIST)) {
            ListTag togglesTag = nbt.getList("ActiveToggles", Tag.TAG_STRING);
            for (int i = 0; i < togglesTag.size(); i++) {
                ResourceLocation rl = ResourceLocation.tryParse(togglesTag.getString(i));
                if (rl != null && isNodeUnlocked(rl)) activeToggles.add(rl);
            }
        }

        for (int i = 0; i < MAX_SPELL_MEMORY; i++) spellMemory[i] = null;
        if (nbt.contains("SpellMemory", Tag.TAG_LIST)) {
            ListTag spellsTag = nbt.getList("SpellMemory", Tag.TAG_COMPOUND);
            for (int i = 0; i < spellsTag.size(); i++) {
                CompoundTag spellEntry = spellsTag.getCompound(i);
                int slot = spellEntry.getInt("Slot");
                if (slot >= 0 && slot < MAX_SPELL_MEMORY) {
                    spellMemory[slot] = CraftedSpell.fromNBT(spellEntry);
                }
            }
        }

        if (nbt.contains("SelectedSkill", Tag.TAG_STRING)) {
            this.selectedSkill = ResourceLocation.tryParse(nbt.getString("SelectedSkill"));
        } else this.selectedSkill = null;

        if (nbt.contains("PrimaryBranch", Tag.TAG_STRING)) {
            this.primaryBranch = ResourceLocation.tryParse(nbt.getString("PrimaryBranch"));
        } else this.primaryBranch = null;

        if (nbt.contains("SecondaryBranch", Tag.TAG_STRING)) {
            this.secondaryBranch = ResourceLocation.tryParse(nbt.getString("SecondaryBranch"));
        } else this.secondaryBranch = null;

        if (nbt.contains("CurrentMana", Tag.TAG_FLOAT)) this.currentMana = nbt.getFloat("CurrentMana");
        else this.currentMana = 100.0f;

        this.ultimateCharged = nbt.getBoolean("UltimateCharged");
        this.dashIFrameTicks = 0;
    }
}