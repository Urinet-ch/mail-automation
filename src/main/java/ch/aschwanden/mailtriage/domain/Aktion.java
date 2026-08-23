package ch.aschwanden.mailtriage.domain;

/**
 * Handlungsbedarf einer Mail - die in Etappe 0 validierte Achse:
 * nicht "wichtig", sondern "muss ich etwas tun, und wenn ja was".
 */
public enum Aktion {
    ANTWORTEN("Antworten"),
    BEZAHLEN("Bezahlen"),
    ERLEDIGEN("Erledigen"),
    TERMIN("Termin"),
    KEINE("Keine");

    private final String label;

    Aktion(String label) {
        this.label = label;
    }

    /** Anzeigename, z. B. fuer das Label "Aktion-Bezahlen". */
    public String label() {
        return label;
    }

    public boolean erfordertHandlung() {
        return this != KEINE;
    }

    public static Aktion fromJson(String value) {
        for (Aktion a : values()) {
            if (a.name().equalsIgnoreCase(value) || a.label.equalsIgnoreCase(value)) {
                return a;
            }
        }
        return KEINE;
    }
}
