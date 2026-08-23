package ch.aschwanden.mailtriage.domain;

/** Fachliche Kategorie einer Mail. Wird 1:1 als Gmail-Label unter dem AI-Praefix sichtbar. */
public enum Kategorie {
    RECHNUNG("Rechnung"),
    ARBEIT("Arbeit"),
    PERSOENLICH("Persoenlich"),
    BEHOERDE("Behoerde"),
    NEWSLETTER("Newsletter"),
    WERBUNG("Werbung"),
    BENACHRICHTIGUNG("Benachrichtigung"),
    UNSICHER("Unsicher");

    private final String label;

    Kategorie(String label) {
        this.label = label;
    }

    /** Labelname ohne Praefix, z. B. "Rechnung". */
    public String label() {
        return label;
    }

    public static Kategorie fromJson(String value) {
        for (Kategorie k : values()) {
            if (k.name().equalsIgnoreCase(value) || k.label.equalsIgnoreCase(value)) {
                return k;
            }
        }
        return UNSICHER;
    }
}
