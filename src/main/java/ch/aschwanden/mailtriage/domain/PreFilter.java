package ch.aschwanden.mailtriage.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Deterministische Regeln, die VOR dem Modell greifen. Alles, was sich als feste
 * Regel formulieren laesst, wird hier entschieden - schneller, billiger, und ein
 * Fehler ist konstruktiv unmoeglich statt nur unwahrscheinlich.
 *
 * Reine Domainklasse ohne Framework-Abhaengigkeiten, damit trivial testbar.
 */
public final class PreFilter {

    private final List<String> werbungAbsender;
    private final List<String> benachrichtigungAbsender;

    public PreFilter(List<String> werbungAbsender, List<String> benachrichtigungAbsender) {
        this.werbungAbsender = normalize(werbungAbsender);
        this.benachrichtigungAbsender = normalize(benachrichtigungAbsender);
    }

    /**
     * @return Ergebnis, wenn eine Regel greift - sonst empty, dann entscheidet das Modell.
     */
    public Optional<TriageResult> pruefe(EmailMessage mail) {
        // Regel 1: Selbst-Mails sind Erinnerungen an mich - ausnahmslos Handlungsbedarf.
        // (Erkenntnis aus Etappe 0: das binaere "wichtig" hat genau diesen Fall verfehlt.)
        if (mail.fromSelf()) {
            return Optional.of(TriageResult.vorfilter(
                    Kategorie.PERSOENLICH, Aktion.ERLEDIGEN, "Erinnerung an mich selbst"));
        }

        String from = mail.from() == null ? "" : mail.from().toLowerCase(Locale.ROOT);

        // Regel 2: konfigurierte Werbe-Absender
        if (matches(from, werbungAbsender)) {
            return Optional.of(TriageResult.vorfilter(
                    Kategorie.WERBUNG, Aktion.KEINE, "Bekannter Werbe-Absender"));
        }

        // Regel 3: konfigurierte Benachrichtigungs-Absender (Monitoring, Builds, Lieferstatus)
        if (matches(from, benachrichtigungAbsender)) {
            return Optional.of(TriageResult.vorfilter(
                    Kategorie.BENACHRICHTIGUNG, Aktion.KEINE, "Bekannter Benachrichtigungs-Absender"));
        }

        // Regel 4: List-Unsubscribe-Header = abonnierter Massenversand.
        // Bewusst NACH den Absenderlisten: eine Rechnung eines Absenders von der
        // Whitelist gewinnt gegen den Newsletter-Header.
        if (mail.hasListUnsubscribe()) {
            return Optional.of(TriageResult.vorfilter(
                    Kategorie.NEWSLETTER, Aktion.KEINE, "Massenversand (List-Unsubscribe)"));
        }

        return Optional.empty();
    }

    private static boolean matches(String from, List<String> patterns) {
        return patterns.stream().anyMatch(from::contains);
    }

    private static List<String> normalize(List<String> in) {
        return in == null ? List.of()
                : in.stream().map(s -> s.toLowerCase(Locale.ROOT).trim()).filter(s -> !s.isEmpty()).toList();
    }
}
