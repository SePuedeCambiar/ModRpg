package com.example.modrpg.skills;


import net.minecraft.nbt.CompoundTag;

public class PlayerSkills {

    // === NIVELES DE RAMAS ===
    private int meleeLevel = 0;
    private int rangedLevel = 0;
    private int mobilityLevel = 0;

    // === REQUISITOS DE PRÁCTICA (Kills / Acciones) ===
    private int meleeKills = 0;
    private int rangedKills = 0;

    // === HABILIDADES DESBLOQUEADAS ===
    private boolean hasSpinAttack = false;     // Habilidad secundaria (giro)
    private boolean hasCapstoneMelee = false;  // Habilidad final (500% crítico)
    private boolean hasHybridRangedMelee = false; // Habilidad híbrida

    public PlayerSkills() {}

    // --- GETTERS Y SETTERS (Cuerpo a Cuerpo) ---
    public int getMeleeLevel() { return meleeLevel; }
    public void setMeleeLevel(int level) { this.meleeLevel = Math.min(level, 100); }
    public void addMeleeLevel(int amount) { setMeleeLevel(this.meleeLevel + amount); }

    public int getMeleeKills() { return meleeKills; }
    public void addMeleeKill() { this.meleeKills++; }

    // --- GETTERS Y SETTERS (Arquería / Distancia) ---
    public int getRangedLevel() { return rangedLevel; }
    public void setRangedLevel(int level) { this.rangedLevel = Math.min(level, 100); }
    public void addRangedLevel(int amount) { setRangedLevel(this.rangedLevel + amount); }

    public int getRangedKills() { return rangedKills; }
    public void addRangedKill() { this.rangedKills++; }

    // --- GETTERS Y SETTERS (Movilidad) ---
    public int getMobilityLevel() { return mobilityLevel; }
    public void setMobilityLevel(int level) { this.mobilityLevel = Math.min(level, 100); }

    // --- ESTADO DE HABILIDADES ACTIVAS ---
    public boolean hasSpinAttack() { return hasSpinAttack; }
    public void setSpinAttack(boolean unlocked) { this.hasSpinAttack = unlocked; }

    public boolean hasCapstoneMelee() { return hasCapstoneMelee; }
    public void setCapstoneMelee(boolean unlocked) { this.hasCapstoneMelee = unlocked; }

    public boolean hasHybridRangedMelee() { return hasHybridRangedMelee; }
    public void setHybridRangedMelee(boolean unlocked) { this.hasHybridRangedMelee = unlocked; }

    // --- COPIAR DATOS (Cuando el jugador muere y respawnea) ---
    public void copyFrom(PlayerSkills source) {
        this.meleeLevel = source.meleeLevel;
        this.rangedLevel = source.rangedLevel;
        this.mobilityLevel = source.mobilityLevel;
        this.meleeKills = source.meleeKills;
        this.rangedKills = source.rangedKills;
        this.hasSpinAttack = source.hasSpinAttack;
        this.hasCapstoneMelee = source.hasCapstoneMelee;
        this.hasHybridRangedMelee = source.hasHybridRangedMelee;
    }

    // --- GUARDAR EN EL DISCO (NBT) ---
    public void saveNBTData(CompoundTag nbt) {
        nbt.putInt("meleeLevel", meleeLevel);
        nbt.putInt("rangedLevel", rangedLevel);
        nbt.putInt("mobilityLevel", mobilityLevel);
        nbt.putInt("meleeKills", meleeKills);
        nbt.putInt("rangedKills", rangedKills);
        nbt.putBoolean("hasSpinAttack", hasSpinAttack);
        nbt.putBoolean("hasCapstoneMelee", hasCapstoneMelee);
        nbt.putBoolean("hasHybridRangedMelee", hasHybridRangedMelee);
    }

    // --- LEER DEL DISCO (NBT) ---
    public void loadNBTData(CompoundTag nbt) {
        this.meleeLevel = nbt.getInt("meleeLevel");
        this.rangedLevel = nbt.getInt("rangedLevel");
        this.mobilityLevel = nbt.getInt("mobilityLevel");
        this.meleeKills = nbt.getInt("meleeKills");
        this.rangedKills = nbt.getInt("rangedKills");
        this.hasSpinAttack = nbt.getBoolean("hasSpinAttack");
        this.hasCapstoneMelee = nbt.getBoolean("hasCapstoneMelee");
        this.hasHybridRangedMelee = nbt.getBoolean("hasHybridRangedMelee");
    }
}