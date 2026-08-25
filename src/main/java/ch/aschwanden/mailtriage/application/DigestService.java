package ch.aschwanden.mailtriage.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import ch.aschwanden.mailtriage.domain.Mailkonto;
import ch.aschwanden.mailtriage.port.DigestMailPort;
import ch.aschwanden.mailtriage.port.KontoStorePort;
import ch.aschwanden.mailtriage.port.TriageStorePort;
import ch.aschwanden.mailtriage.port.TriageStorePort.DigestEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Tages-Digest: "Was muss ich tun" zuerst, dann die Statistik.
 * Geht als Mail an das eigene Konto - nie ueber einen oeffentlichen Dienst,
 * weil der Digest Mail-Inhalte enthaelt.
 *
 * <p>Je Konto ein eigener Digest, versandt ueber dieses Konto an sich selbst. Ein
 * gemeinsamer Digest ueber alle Postfaecher waere bequemer zu lesen, wuerde aber
 * Inhalte des einen Postfachs in ein anderes tragen - genau die Vermischung, die
 * getrennte Konten vermeiden sollen.
 */
@ApplicationScoped
public class DigestService {

    private static final Logger LOG = Logger.getLogger(DigestService.class);
    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Inject
    TriageStorePort store;

    @Inject
    DigestMailPort digestMail;

    @Inject
    KontoStorePort konten;

    @ConfigProperty(name = "digest.zeitraum-stunden", defaultValue = "24")
    int zeitraumStunden;

    /** Digest fuer jedes aktive Konto. Ein Fehlschlag stoppt die uebrigen nicht. */
    public void sendeDigest() {
        for (Mailkonto konto : konten.aktive()) {
            try {
                sendeDigest(konto);
            } catch (RuntimeException e) {
                LOG.errorf(e, "Digest fuer %s fehlgeschlagen: %s", konto.email(), e.getMessage());
            }
        }
    }

    public void sendeDigest(Mailkonto konto) {
        Instant since = Instant.now().minus(Duration.ofHours(zeitraumStunden));
        List<DigestEntry> eintraege = store.resultsSince(konto, since);
        long pending = store.pendingCount(konto);

        if (eintraege.isEmpty() && pending == 0) {
            LOG.infof("[%s] Digest: nichts zu berichten, keine Mail gesendet.", konto.email());
            return;
        }

        List<DigestEntry> aktionen = eintraege.stream()
                .filter(e -> !"Keine".equalsIgnoreCase(e.aktion()))
                .sorted((a, b) -> {
                    // Mit Frist zuerst, dann nach Frist aufsteigend
                    LocalDate fa = a.frist();
                    LocalDate fb = b.frist();
                    if (fa == null && fb == null) {
                        return 0;
                    }
                    if (fa == null) {
                        return 1;
                    }
                    if (fb == null) {
                        return -1;
                    }
                    return fa.compareTo(fb);
                })
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("Mail-Triage der letzten ").append(zeitraumStunden).append(" Stunden\n");
        sb.append("Postfach: ").append(konto.email()).append('\n');
        sb.append("==========================================\n\n");

        if (aktionen.isEmpty()) {
            sb.append("Nichts zu tun - keine Mail verlangt eine Handlung.\n\n");
        } else {
            sb.append("ZU TUN (").append(aktionen.size()).append("):\n\n");
            for (DigestEntry e : aktionen) {
                sb.append("- [").append(e.aktion());
                if (e.frist() != null) {
                    sb.append(" bis ").append(DATUM.format(e.frist()));
                }
                sb.append("] ").append(e.subject()).append('\n');
                sb.append("  von ").append(e.from()).append(" - ").append(e.zusammenfassung()).append('\n');
            }
            sb.append('\n');
        }

        Map<String, Long> proKategorie = eintraege.stream()
                .collect(Collectors.groupingBy(DigestEntry::kategorie, Collectors.counting()));
        sb.append("Verarbeitet: ").append(eintraege.size()).append(" Mail(s)");
        if (!proKategorie.isEmpty()) {
            sb.append(" (").append(proKategorie.entrySet().stream()
                    .map(e -> e.getValue() + "x " + e.getKey())
                    .collect(Collectors.joining(", "))).append(')');
        }
        sb.append('\n');

        if (pending > 0) {
            sb.append("\nACHTUNG: ").append(pending)
              .append(" Mail(s) warten auf Klassifikation (LM Studio nicht erreichbar?).\n");
        }

        String betreff = aktionen.isEmpty()
                ? "Mail-Digest: nichts zu tun (" + eintraege.size() + " verarbeitet)"
                : "Mail-Digest: " + aktionen.size() + " Aufgabe(n)";
        digestMail.sendToSelf(konto, betreff, sb.toString());
        LOG.infof("[%s] Digest gesendet: %d Eintraege, %d Aktionen, %d pending",
                konto.email(), eintraege.size(), aktionen.size(), pending);
    }
}
