package com.example.modrpg.ai.nemesis;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Modelo de datos que representa a un Capitán Némesis persistente en el mundo.
 */
public class NemesisCaptain {

    public enum Status {
        STALKING,       // Vivo y cazando activamente en el mundo
        WAITING_REVENGE,// Escapó con vida y está planeando su regreso
        DEAD            // Derrotado definitivamente
    }

    private final UUID captainUUID;
    private String name;
    private String title;
    private String counterTrait;
    private int prestigeScore;
    private int encounterDay;
    private Status status;
    private String scar;

    public NemesisCaptain(UUID captainUUID, String name, String title, String counterTrait, int prestigeScore, int encounterDay, Status status, String scar) {
        this.captainUUID = captainUUID;
        this.name = name;
        this.title = title;
        this.counterTrait = counterTrait;
        this.prestigeScore = prestigeScore;
        this.encounterDay = encounterDay;
        this.status = status;
        this.scar = scar;
    }

    public UUID getCaptainUUID() { return captainUUID; }
    public String getName() { return name; }
    public String getTitle() { return title; }
    public String getCounterTrait() { return counterTrait; }
    public int getPrestigeScore() { return prestigeScore; }
    public int getEncounterDay() { return encounterDay; }
    public Status getStatus() { return status; }
    public String getScar() { return scar; }

    public void setStatus(Status status) { this.status = status; }
    public void addPrestige(int points) { this.prestigeScore += points; }
    public void setScar(String scar) { this.scar = scar; }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("UUID", captainUUID);
        tag.putString("Name", name);
        tag.putString("Title", title);
        tag.putString("CounterTrait", counterTrait);
        tag.putInt("Prestige", prestigeScore);
        tag.putInt("Day", encounterDay);
        tag.putString("Status", status.name());
        tag.putString("Scar", scar != null ? scar : "NONE");
        return tag;
    }

    public static NemesisCaptain fromNBT(CompoundTag tag) {
        UUID id = tag.getUUID("UUID");
        String name = tag.getString("Name");
        String title = tag.getString("Title");
        String trait = tag.getString("CounterTrait");
        int prestige = tag.getInt("Prestige");
        int day = tag.getInt("Day");
        Status status = Status.valueOf(tag.getString("Status"));
        String scar = tag.getString("Scar");

        return new NemesisCaptain(id, name, title, trait, prestige, day, status, scar);
    }
}