package com.example.modrpg.ai.director;

public enum DirectorState {
    REPRIEVE("§a[RESPIRO / PAZ]", "El Director retiene a las hordas para permitir recuperación."),
    BUILD_UP("§e[ACECHO EN SOMBRAS]", "El Director guía exploradores hacia tus puntos ciegos."),
    AMBUSH_READY("§6[EMBOSCADA PREPARADA]", "Vulnerabilidad detectada. El escuadrón cierra salidas."),
    CLIMAX("§4§l[CLÍMAX DE COMBATE]", "Enfrentamiento F.E.A.R. total a máxima intensidad.");

    private final String badge;
    private final String description;

    DirectorState(String badge, String description) {
        this.badge = badge;
        this.description = description;
    }

    public String getBadge() { return badge; }
    public String getDescription() { return description; }
}