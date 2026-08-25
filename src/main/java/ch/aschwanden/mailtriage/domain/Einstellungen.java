package ch.aschwanden.mailtriage.domain;

import java.util.List;

/**
 * Betriebseinstellungen, die zur Laufzeit aenderbar sind.
 *
 * <p>Abgegrenzt gegen die Deployment-Konfiguration: hier steht, was sich im Betrieb
 * aendert, ohne dass sich das Deployment aendert - allen voran die Adresse des
 * Inferenz-Rechners. Zugangsdaten stehen NICHT hier, die gehoeren zum
 * {@link Mailkonto} bzw. ins Secret.
 */
public record Einstellungen(
        String modellUrl,
        String modellName,
        double temperatur,
        double confidenceSchwelle,
        int batchMax,
        List<String> werbungAbsender,
        List<String> benachrichtigungAbsender) {

    public Einstellungen {
        modellUrl = modellUrl == null ? "" : modellUrl.trim();
        if (modellUrl.isBlank()) {
            throw new IllegalArgumentException("Modell-URL fehlt");
        }
        // Ohne Schema baut der REST-Client keine URI auf, und die Fehlermeldung waere
        // kryptisch. Lieber hier eine verstaendliche stellen.
        if (!modellUrl.startsWith("http://") && !modellUrl.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "Modell-URL muss mit http:// oder https:// beginnen: " + modellUrl);
        }
        // Ein abschliessender Schraegstrich ergaebe beim Anhaengen von /v1/... doppelte.
        while (modellUrl.endsWith("/")) {
            modellUrl = modellUrl.substring(0, modellUrl.length() - 1);
        }
        modellName = modellName == null ? "" : modellName.trim();
        if (modellName.isBlank()) {
            throw new IllegalArgumentException("Modellname fehlt");
        }
        if (temperatur < 0 || temperatur > 2) {
            throw new IllegalArgumentException("Temperatur muss zwischen 0 und 2 liegen");
        }
        if (confidenceSchwelle < 0 || confidenceSchwelle > 1) {
            throw new IllegalArgumentException("Confidence-Schwelle muss zwischen 0 und 1 liegen");
        }
        if (batchMax < 1 || batchMax > 500) {
            throw new IllegalArgumentException("Batch-Groesse muss zwischen 1 und 500 liegen");
        }
        werbungAbsender = normalisiere(werbungAbsender);
        benachrichtigungAbsender = normalisiere(benachrichtigungAbsender);
    }

    /** Aus einer kommagetrennten Eingabe, wie sie das Formular liefert. */
    public static List<String> ausListe(String kommagetrennt) {
        if (kommagetrennt == null || kommagetrennt.isBlank()) {
            return List.of();
        }
        return normalisiere(List.of(kommagetrennt.split(",")));
    }

    private static List<String> normalisiere(List<String> muster) {
        if (muster == null) {
            return List.of();
        }
        return muster.stream()
                .map(String::trim)
                .filter(m -> !m.isEmpty())
                .distinct()
                .toList();
    }

    public String werbungAlsListe() {
        return String.join(", ", werbungAbsender);
    }

    public String benachrichtigungAlsListe() {
        return String.join(", ", benachrichtigungAbsender);
    }
}
