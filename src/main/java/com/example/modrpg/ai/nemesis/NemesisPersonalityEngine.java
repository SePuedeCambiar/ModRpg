package com.example.modrpg.ai.nemesis;

import net.minecraft.util.RandomSource;

/**
 * Genera proceduralmente nombres, asigna títulos según el contra-estilo
 * y construye líneas de diálogo reactivas con pools de variedad amplios.
 */
public class NemesisPersonalityEngine {

    private static final String[] NAME_PREFIXES = {
            "Vor", "Az", "Mala", "Thru", "Kae", "Skar", "Bram", "Dra", "Mor", "Krug", "Gor", "Zul", "Tor", "Vak", "Khor"
    };

    private static final String[] NAME_SUFFIXES = {
            "gul", "gar", "kor", "rum", "len", "gash", "gan", "vath", "thar", "mok", "kash", "nak", "gorg", "drak"
    };

    public static String generateProceduralName(RandomSource random) {
        String pre = NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)];
        String suf = NAME_SUFFIXES[random.nextInt(NAME_SUFFIXES.length)];
        return pre + suf;
    }

    public static String assignReactiveTitle(PlayerCombatProfiler.DominantStyle playerStyle, String scar, RandomSource random) {
        if (scar != null && !scar.equals("NONE")) {
            return switch (scar) {
                case "FIRE" -> randomElement(random, "el Calcinado", "el Forjado en Fuego", "el Fénix de Cenizas");
                case "FROST" -> randomElement(random, "el Piel-de-Escarcha", "el Descongelado", "el Alma Invernal");
                case "LIGHTNING" -> randomElement(random, "el Aterrado", "el Conductor de Rayos", "el Forjatormentas");
                default -> randomElement(random, "el Resucitado", "el Inmortal", "el Marcado por la Muerte");
            };
        }

        return switch (playerStyle) {
            case AIR_JUMPER -> randomElement(random, "el Rompe-Cielos", "el Cazador de Aves", "el Verdugo de Saltimbanquis");
            case SHIELD_TURTLE -> randomElement(random, "el Hendidor de Acero", "el Aplasta-Guardias", "el Mazo Implacable");
            case ELEMENTAL_MAGE -> randomElement(random, "el Come-Hechizos", "el Silenciador Arcano", "el Devorador de Maná");
            case SNIPER_KITER -> randomElement(random, "el Desvía-Flechas", "el Cazador Implacable", "el Corta-Viento");
            case MELEE_BERSERKER -> randomElement(random, "el Quebrantahuesos", "el Maestro del Acero", "el Matagigantes");
            case BALANCED -> randomElement(random, "el Verdugo Solitario", "el Señor de Guerra", "el Veterano de Sangre");
        };
    }

    /**
     * Diálogo al iniciar el combate (Intro cinematográfica en Chat).
     */
    public static String buildIntroDialogue(NemesisCaptain captain, RandomSource random) {
        String trait = captain.getCounterTrait();
        String scar = captain.getScar();

        if (scar != null && !scar.equals("NONE")) {
            return switch (scar) {
                case "FIRE" -> randomElement(random,
                        "«¿Creías que las llamas me consumirían en aquella cueva? ¡El fuego solo templó mi odio!»",
                        "«Tu fuego me dejó marcas, humano... ¡pero hoy te quemarás en tu propia ceniza!»"
                );
                case "FROST" -> randomElement(random,
                        "«Tu hielo me congeló una vez... ¡pero hoy te romperé en mil pedazos fríos!»",
                        "«Siento el frío en mis huesos desde nuestro último choque. ¡Hora de cobrar esa deuda!»"
                );
                default -> randomElement(random,
                        "«¡Recuerdo tu arma, humano! ¡Hoy terminamos lo que empezaste!»",
                        "«¿Sorprendido de verme en pie? La muerte me rechazó para poder matarte yo mismo.»"
                );
            };
        }

        return switch (trait) {
            case "ANTI_AIR_GRAVITY" -> randomElement(random,
                    "«¿Te gusta saltar por los aires? ¡Veamos cómo peleas arrastrándote por el barro!»",
                    "«¡El cielo no te salvará hoy! Mis cadenas te mantendrán pegado al suelo.»"
            );
            case "SHIELD_BREAKER" -> randomElement(random,
                    "«¡Escóndete tras tu trozo de hierro todo lo que quieras! ¡Lo haré astillas con tus huesos!»",
                    "«Ningún escudo resiste la furia de mi maza. ¡Prepárate a sentir el impacto!»"
            );
            case "MANA_DRAINER" -> randomElement(random,
                    "«Huelo la chispa en tus venas... ¡Trágate tus propios hechizos, mago!»",
                    "«Tu magia es un regalo prestado... ¡y he venido a cobrártela toda!»"
            );
            case "PROJECTILE_DEFLECTOR" -> randomElement(random,
                    "«¡Tus flechitas no perforarán mi guardia! ¡Acércate y pelea como un guerrero!»",
                    "«Dispara cuanto quieras, cobarde. ¡Cada flecha que caiga será devuelta con creces!»"
            );
            default -> randomElement(random,
                    "«¡Tu racha de supervivencia termina hoy! ¡Tu cabeza será mi trofeo!»",
                    "«Mi escuadrón te ha rastreado por días. ¡No hay rincón de este mundo donde escapar!»"
            );
        };
    }

    /**
     * Burlas rápidas durante el combate (Action Bar).
     */
    public static String buildTauntDialogue(NemesisCaptain captain, RandomSource random) {
        return switch (captain.getCounterTrait()) {
            case "ANTI_AIR_GRAVITY" -> randomElement(random,
                    "«¡Al suelo! ¡El cielo no es para gusanos como tú!»",
                    "«¿Se te acabaron las alas? ¡Pelea aquí abajo!»"
            );
            case "SHIELD_BREAKER" -> randomElement(random,
                    "«¡Guardia rota! ¿Dónde está tu valentía ahora?»",
                    "«¡Ese escudo de juguete no te servirá de nada!»"
            );
            case "MANA_DRAINER" -> randomElement(random,
                    "«¡Siente cómo se apaga tu chispa! ¡Estás vacío!»",
                    "«¿Dónde están tus truquitos ahora, hechicero?»"
            );
            default -> randomElement(random,
                    "«¡Sangra! ¡Vamos, enséñame si eres tan fuerte como decían!»",
                    "«¿Eso es todo lo que tienes? ¡Apenas estoy calentando!»",
                    "«¡Acorraladlo! ¡Que no llegue al amanecer!»"
            );
        };
    }

    /**
     * Diálogo de escape táctico (< 25% HP con bomba de humo).
     */
    public static String buildEscapeDialogue(NemesisCaptain captain, RandomSource random) {
        return randomElement(random,
                "«¡Maldito seas! ¡Esta herida la pagarás con tu vida cuando menos lo esperes!»",
                "«¡Disfruta tu pequeña victoria! ¡Mi escuadrón volverá por tu cuello!»",
                "«¡El humo esconde mi retirada, pero tu sangre guiará mi regreso!»",
                "«¡Bien jugado, mortal! Pero la caza apenas ha comenzado... ¡Nos veremos en las sombras!»"
        );
    }

    /**
     * Diálogo final al ser derrotado definitivamente.
     */
    public static String buildDeathDialogue(NemesisCaptain captain, RandomSource random) {
        return randomElement(random,
                "«Mi... escuadrón... no descansará... hasta verte... caer...»",
                "«Te llevas mi vida... pero el Director... ya conoce todos tus secretos...»",
                "«Nos... volveremos a ver... en el abismo...»"
        );
    }

    private static String randomElement(RandomSource random, String... options) {
        return options[random.nextInt(options.length)];
    }
}