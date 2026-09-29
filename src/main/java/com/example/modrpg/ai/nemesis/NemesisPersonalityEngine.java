package com.example.modrpg.ai.nemesis;

import net.minecraft.util.RandomSource;

/**
 * Genera proceduralmente nombres arcanos/orcanos, asigna títulos según el contra-estilo
 * y construye líneas de diálogo reactivas con memoria de heridas pasadas.
 */
public class NemesisPersonalityEngine {

    private static final String[] NAME_PREFIXES = {
            "Vor", "Az", "Mala", "Thru", "Kae", "Skar", "Bram", "Dra", "Mor", "Krug", "Gor", "Zul", "Tor"
    };

    private static final String[] NAME_SUFFIXES = {
            "gul", "gar", "kor", "rum", "len", "gash", "gan", "vath", "thar", "mok", "kash", "nak"
    };

    public static String generateProceduralName(RandomSource random) {
        String pre = NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)];
        String suf = NAME_SUFFIXES[random.nextInt(NAME_SUFFIXES.length)];
        return pre + suf;
    }

    /**
     * Asigna un título reactivo basado en el estilo dominante del jugador o en una cicatriz previa.
     */
    public static String assignReactiveTitle(PlayerCombatProfiler.DominantStyle playerStyle, String scar, RandomSource random) {
        // 1. Si el capitán tiene una cicatriz de un encuentro previo donde casi muere
        if (scar != null && !scar.equals("NONE")) {
            return switch (scar) {
                case "FIRE" -> random.nextBoolean() ? "el Calcinado" : "el Forjado en Fuego";
                case "FROST" -> random.nextBoolean() ? "el Piel-de-Escarcha" : "el Descongelado";
                case "LIGHTNING" -> random.nextBoolean() ? "el Aterrado" : "el Conductor de Rayos";
                default -> "el Resucitado";
            };
        }

        // 2. Título basado en la táctica del jugador que viene a contrarrestar
        return switch (playerStyle) {
            case AIR_JUMPER -> random.nextBoolean() ? "el Rompe-Cielos" : "el Cazador de Aves";
            case SHIELD_TURTLE -> random.nextBoolean() ? "el Hendidor de Acero" : "el Aplasta-Guardias";
            case ELEMENTAL_MAGE -> random.nextBoolean() ? "el Come-Hechizos" : "el Silenciador";
            case SNIPER_KITER -> random.nextBoolean() ? "el Desvía-Flechas" : "el Cazador Implacable";
            case MELEE_BERSERKER -> random.nextBoolean() ? "el Quebrantahuesos" : "el Maestro del Acero";
            case BALANCED -> random.nextBoolean() ? "el Verdugo Solitario" : "el Señor de Guerra";
        };
    }

    /**
     * Diálogo al iniciar el combate (Intro cinematográfica).
     */
    public static String buildIntroDialogue(NemesisCaptain captain) {
        String trait = captain.getCounterTrait();
        String scar = captain.getScar();

        if (scar != null && !scar.equals("NONE")) {
            return switch (scar) {
                case "FIRE" -> "«¿Creías que las llamas me consumirían en aquella cueva? ¡El fuego solo templó mi odio!»";
                case "FROST" -> "«Tu hielo me congeló una vez... ¡pero hoy te romperé en mil pedazos fríos!»";
                default -> "«¡Recuerdo tu arma, humano! ¡Hoy terminamos lo que empezaste!»";
            };
        }

        return switch (trait) {
            case "ANTI_AIR_GRAVITY" -> "«¿Te gusta saltar por los aires? ¡Veamos cómo peleas arrastrándote por el barro!»";
            case "SHIELD_BREAKER" -> "«¡Escóndete tras tu trozo de hierro todo lo que quieras! ¡Lo haré astillas junto con tus huesos!»";
            case "MANA_DRAINER" -> "«Huelo la chispa en tus venas... ¡Trágate tus propios hechizos, mago!»";
            case "PROJECTILE_DEFLECTOR" -> "«¡Tus flechitas no perforarán mi guardia! ¡Acércate y pelea como un guerrero!»";
            default -> "«¡Tu racha de supervivencia termina hoy! ¡Tu cabeza será mi trofeo!»";
        };
    }

    /**
     * Burlas durante el combate cuando el némesis acierta un golpe pesado o rompe la postura del jugador.
     */
    public static String buildTauntDialogue(NemesisCaptain captain) {
        return switch (captain.getCounterTrait()) {
            case "ANTI_AIR_GRAVITY" -> "«¡Al suelo! ¡El cielo no es para gusanos como tú!»";
            case "SHIELD_BREAKER" -> "«¡Guardia rota! ¿Dónde está tu valentía ahora?»";
            case "MANA_DRAINER" -> "«¡Siente cómo se apaga tu magia! ¡Estás vacío!»";
            default -> "«¡Sangra! ¡Vamos, enséñame si eres tan fuerte como decían!»";
        };
    }

    /**
     * Diálogo de escape táctico cuando el némesis casi muere (< 25% HP) y lanza su bomba de humo.
     */
    public static String buildEscapeDialogue(NemesisCaptain captain) {
        return "«¡Maldito seas! ¡Esta herida la pagarás con tu vida cuando menos lo esperes!»";
    }

    /**
     * Diálogo final al ser derrotado definitivamente.
     */
    public static String buildDeathDialogue(NemesisCaptain captain) {
        return "«Mi... escuadrón... no descansará... hasta verte... caer...»";
    }
}