package ch.aschwanden.mailtriage.domain;

import java.time.LocalDate;

/**
 * Ergebnis der Klassifikation einer Mail - entweder deterministisch (Vorfilter)
 * oder vom Modell. Das Schema entspricht dem in Etappe 0 validierten Testlauf.
 */
public record TriageResult(
        Kategorie kategorie,
        Aktion aktion,
        LocalDate frist,
        String zusammenfassung,
        double confidence,
        Quelle quelle) {

    public enum Quelle { VORFILTER, MODELL }

    public static TriageResult vorfilter(Kategorie kategorie, Aktion aktion, String zusammenfassung) {
        return new TriageResult(kategorie, aktion, null, zusammenfassung, 1.0, Quelle.VORFILTER);
    }

    /** Unter dieser Confidence wird nur "Unsicher" gelabelt und nichts weiter entschieden. */
    public boolean istUnsicher(double schwelle) {
        return confidence < schwelle;
    }
}
