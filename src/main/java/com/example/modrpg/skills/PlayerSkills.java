package com.example.modrpg.skills;

import net.minecraft.nbt.CompoundTag;

public class PlayerSkills {

    // === NIVELES DE RAMAS ===
    private int meleeLevel = 0;
    private int rangedLevel = 0;
    private int mobilityLevel = 0;

    // === REQUISITOS DE PRÁCTICA ===
    private int meleeKills = 0;
    private int rangedKills = 0;

    // === HABILIDADES DESBLOQUEADAS (Rama 1: CaC) ===
    private boolean hasDoubleAttack = false;   // Nivel 4 CaC
    private boolean hasSpinAttack = false;     // Nivel 20 CaC
    private boolean hasCapstoneMelee = false;  // Nivel 50 CaC

    // === HABILIDADES DESBLOQUEADAS (Rama 2: Arquería) ===
    private boolean hasTailwind = false;        // Habilidad #1: Viento a Favor (Nivel 5)
    private boolean hasHypersonicArrow = false; // Habilidad Maestra: Tiro Hipersónico (Nivel 50)

    // === HÍBRIDAS ===
    private boolean hasHybridRangedMelee = false; // Nivel 25 CaC + 25 Distancia

    // === COOLDOWNS Y CARGAS ===
    private boolean ultimateCharged = false;
    private int ultimateCooldown = 0;
    private int spinCooldown = 0;

    public PlayerSkills() {}

    // --- GETTERS Y SETTERS (Cuerpo a Cuerpo) ---
    public int getMeleeLevel() { return meleeLevel; }
    public void setMeleeLevel(int level) { this.meleeLevel = Math.min(level, 100); }
    public void addMeleeLevel(int amount) { setMeleeLevel(this.meleeLevel + amount); }

    public int getMeleeKills() { return meleeKills; }
    public void setMeleeKills(int kills) { this.meleeKills = kills; }
    public void addMeleeKill() { this.meleeKills++; }

    // --- GETTERS Y SETTERS (Distancia) ---
    public int getRangedLevel() { return rangedLevel; }
    public void setRangedLevel(int level) { this.rangedLevel = Math.min(level, 100); }
    public void addRangedLevel(int amount) { setRangedLevel(this.rangedLevel + amount); }

    public int getRangedKills() { return rangedKills; }
    public void setRangedKills(int kills) { this.rangedKills = kills; }
    public void addRangedKill() { this.rangedKills++; }

    // --- GETTERS Y SETTERS (Movilidad) ---
    public int getMobilityLevel() { return mobilityLevel; }
    public void setMobilityLevel(int level) { this.mobilityLevel = Math.min(level, 100); }

    // --- ESTADO DE HABILIDADES RAMA 1 ---
    public boolean hasDoubleAttack() { return hasDoubleAttack; }
    public void setDoubleAttack(boolean unlocked) { this.hasDoubleAttack = unlocked; }

    public boolean hasSpinAttack() { return hasSpinAttack; }
    public void setSpinAttack(boolean unlocked) { this.hasSpinAttack = unlocked; }

    public boolean hasCapstoneMelee() { return hasCapstoneMelee; }
    public void setCapstoneMelee(boolean unlocked) { this.hasCapstoneMelee = unlocked; }

    // --- ESTADO DE HABILIDADES RAMA 2 ---
    public boolean hasTailwind() { return hasTailwind; }
    public void setTailwind(boolean unlocked) { this.hasTailwind = unlocked; }

    public boolean hasHypersonicArrow() { return hasHypersonicArrow; }
    public void setHypersonicArrow(boolean unlocked) { this.hasHypersonicArrow = unlocked; }

    // --- HÍBRIDAS ---
    public boolean hasHybridRangedMelee() { return hasHybridRangedMelee; }
    public void setHybridRangedMelee(boolean unlocked) { this.hasHybridRangedMelee = unlocked; }

    // --- COOLDOWNS ---
    public boolean isUltimateCharged() { return ultimateCharged; }
    public void setUltimateCharged(boolean charged) { this.ultimateCharged = charged; }

    public int getUltimateCooldown() { return ultimateCooldown; }
    public void setUltimateCooldown(int cooldown) { this.ultimateCooldown = cooldown; }

    public int getSpinCooldown() { return spinCooldown; }
    public void setSpinCooldown(int cooldown) { this.spinCooldown = cooldown; }

    public void tickCooldown() {
        if (this.ultimateCooldown > 0) this.ultimateCooldown--;
        if (this.spinCooldown > 0) this.spinCooldown--;
    }

    // --- COPIAR DATOS ---
    public void copyFrom(PlayerSkills source) {
        this.meleeLevel = source.meleeLevel;
        this.rangedLevel = source.rangedLevel;
        this.mobilityLevel = source.mobilityLevel;
        this.meleeKills = source.meleeKills;
        this.rangedKills = source.rangedKills;
        this.hasDoubleAttack = source.hasDoubleAttack;
        this.hasSpinAttack = source.hasSpinAttack;
        this.hasCapstoneMelee = source.hasCapstoneMelee;
        this.hasTailwind = source.hasTailwind;
        this.hasHypersonicArrow = source.hasHypersonicArrow;
        this.hasHybridRangedMelee = source.hasHybridRangedMelee;
        this.ultimateCooldown = source.ultimateCooldown;
        this.spinCooldown = source.spinCooldown;
    }

    // --- GUARDAR NBT ---
    public void saveNBTData(CompoundTag nbt) {
        nbt.putInt("meleeLevel", meleeLevel);
        nbt.putInt("rangedLevel", rangedLevel);
        nbt.putInt("mobilityLevel", mobilityLevel);
        nbt.putInt("meleeKills", meleeKills);
        nbt.putInt("rangedKills", rangedKills);
        nbt.putBoolean("hasDoubleAttack", hasDoubleAttack);
        nbt.putBoolean("hasSpinAttack", hasSpinAttack);
        nbt.putBoolean("hasCapstoneMelee", hasCapstoneMelee);
        nbt.putBoolean("hasTailwind", hasTailwind);
        nbt.putBoolean("hasHypersonicArrow", hasHypersonicArrow);
        nbt.putBoolean("hasHybridRangedMelee", hasHybridRangedMelee);
        nbt.putInt("ultimateCooldown", ultimateCooldown);
        nbt.putInt("spinCooldown", spinCooldown);
    }

    // --- LEER NBT ---
    public void loadNBTData(CompoundTag nbt) {
        this.meleeLevel = nbt.getInt("meleeLevel");
        this.rangedLevel = nbt.getInt("rangedLevel");
        this.mobilityLevel = nbt.getInt("mobilityLevel");
        this.meleeKills = nbt.getInt("meleeKills");
        this.rangedKills = nbt.getInt("rangedKills");
        this.hasDoubleAttack = nbt.getBoolean("hasDoubleAttack");
        this.hasSpinAttack = nbt.getBoolean("hasSpinAttack");
        this.hasCapstoneMelee = nbt.getBoolean("hasCapstoneMelee");
        this.hasTailwind = nbt.getBoolean("hasTailwind");
        this.hasHypersonicArrow = nbt.getBoolean("hasHypersonicArrow");
        this.hasHybridRangedMelee = nbt.getBoolean("hasHybridRangedMelee");
        this.ultimateCooldown = nbt.getInt("ultimateCooldown");
        this.spinCooldown = nbt.getInt("spinCooldown");
    }
}