package ch.aschwanden.mailtriage.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import ch.aschwanden.mailtriage.domain.EmailMessage;
import ch.aschwanden.mailtriage.domain.Kategorie;
import ch.aschwanden.mailtriage.domain.Mailkonto;
import ch.aschwanden.mailtriage.domain.PreFilter;
import ch.aschwanden.mailtriage.domain.TriageResult;
import ch.aschwanden.mailtriage.port.ClassifierPort;
import ch.aschwanden.mailtriage.port.ClassifierPort.ClassifierUnavailableException;
import ch.aschwanden.mailtriage.port.KontoStorePort;
import ch.aschwanden.mailtriage.port.MailboxPort;
import ch.aschwanden.mailtriage.port.TriageStorePort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Kern-Use-Case: neue Mails holen, klassifizieren (Vorfilter vor Modell),
 * Labels setzen, alles protokollieren. Idempotent ueber die Message-ID.
 */
@ApplicationScoped
public class TriageService {

    private static final Logger LOG = Logger.getLogger(TriageService.class);

    @Inject
    MailboxPort mailbox;

    @Inject
    ClassifierPort classifier;

    @Inject
    TriageStorePort store;

    @Inject
    KontoStorePort konten;

    @Inject
    MeterRegistry metrics;

    @ConfigProperty(name = "triage.confidence-schwelle", defaultValue = "0.7")
    double confidenceSchwelle;

    @ConfigProperty(name = "triage.batch-max", defaultValue = "50")
    int batchMax;

    // Optional statt defaultValue = "": SmallRye kann einen leeren String nicht in eine
    // List<String> konvertieren und bricht beim Start ab ("Failed to load config value of
    // type java.util.List"). Ein leerer Wert gilt fuer Optional als "nicht gesetzt" - und
    // genau das ist der Normalfall, denn beide Muster-Listen sind unkonfiguriert leer.
    @ConfigProperty(name = "triage.vorfilter.werbung-absender")
    Optional<List<String>> werbungAbsender;

    @ConfigProperty(name = "triage.vorfilter.benachrichtigung-absender")
    Optional<List<String>> benachrichtigungAbsender;

    private PreFilter preFilter;
    private Counter verarbeitet;
    private Counter vorfilterTreffer;
    private Counter modellTreffer;
    private Counter unsicher;
    private Counter fehlgeschlagen;

    @PostConstruct
    void init() {
        preFilter = new PreFilter(werbungAbsender.orElse(List.of()), benachrichtigungAbsender.orElse(List.of()));
        verarbeitet = metrics.counter("mail_triage_verarbeitet_total");
        vorfilterTreffer = metrics.counter("mail_triage_entschieden_total", "quelle", "vorfilter");
        modellTreffer = metrics.counter("mail_triage_entschieden_total", "quelle", "modell");
        unsicher = metrics.counter("mail_triage_unsicher_total");
        fehlgeschlagen = metrics.counter("mail_triage_fehlgeschlagen_total");
        metrics.gauge("mail_triage_pending", store, s -> (double) s.pendingCountGesamt());
    }

    /**
     * Ein Triage-Lauf ueber alle aktiven Konten. Wird vom Scheduler und von REST aufgerufen.
     *
     * <p>Ein Konto, das scheitert (Postfach nicht erreichbar, Passwort geaendert), darf die
     * uebrigen nicht mitreissen - deshalb wird je Konto gefangen und geloggt statt eskaliert.
     *
     * @return Anzahl in diesem Lauf verarbeiteter Mails ueber alle Konten
     */
    public int run() {
        int gesamt = 0;
        for (Mailkonto konto : konten.aktive()) {
            try {
                gesamt += run(konto);
            } catch (RuntimeException e) {
                LOG.errorf(e, "Triage fuer %s fehlgeschlagen: %s", konto.email(), e.getMessage());
            }
        }
        return gesamt;
    }

    /** Ein Triage-Lauf fuer genau ein Konto. */
    public int run(Mailkonto konto) {
        long uidValidity = mailbox.uidValidity(konto);
        long lastUid = resolveLastUid(konto, uidValidity);

        List<EmailMessage> neue = mailbox.fetchNewerThan(konto, lastUid, batchMax);
        if (neue.isEmpty()) {
            return 0;
        }
        LOG.infof("[%s] %d neue Mail(s) ab uid=%d", konto.email(), neue.size(), lastUid);

        int count = 0;
        for (EmailMessage mail : neue) {
            triagiere(konto, mail);
            // Fortschritt nach JEDER Mail persistieren: ein Absturz mitten im
            // Batch verarbeitet nach Neustart nichts doppelt (zusaetzlich zur
            // Message-ID-Idempotenz).
            store.saveMailboxState(konto, uidValidity, mail.uid());
            count++;
        }
        return count;
    }

    private void triagiere(Mailkonto konto, EmailMessage mail) {
        if (store.isProcessed(konto, mail.messageId())) {
            LOG.debugf("[%s] Bereits verarbeitet: %s", konto.email(), mail.messageId());
            return;
        }
        verarbeitet.increment();

        Optional<TriageResult> vorentscheid = preFilter.pruefe(mail);
        TriageResult result;
        if (vorentscheid.isPresent()) {
            result = vorentscheid.get();
            vorfilterTreffer.increment();
        } else {
            try {
                result = classifier.classify(mail);
                modellTreffer.increment();
            } catch (ClassifierUnavailableException e) {
                LOG.infof("[%s] Classifier nicht erreichbar, Mail bleibt vorgemerkt: uid=%d",
                        konto.email(), mail.uid());
                store.savePending(konto, mail, e.getMessage());
                return;
            } catch (RuntimeException e) {
                LOG.errorf(e, "[%s] Klassifikation fehlgeschlagen: uid=%d", konto.email(), mail.uid());
                fehlgeschlagen.increment();
                store.savePending(konto, mail, "Fehler: " + e.getMessage());
                return;
            }
        }

        List<String> labels = bestimmeLabels(result);
        mailbox.applyLabels(konto, mail.uid(), labels);
        store.saveResult(konto, mail, result, labels);
        store.resolvePending(konto, mail.messageId());
        LOG.infof("[%s] uid=%d -> %s / %s (%.0f%%, %s)", konto.email(), mail.uid(), result.kategorie(),
                result.aktion(), result.confidence() * 100, result.quelle());
    }

    /**
     * Nachlauf fuer vorgemerkte Mails ueber alle aktiven Konten, sobald der Classifier
     * wieder da ist. Kein Datenverlust bei schlafendem Mac - nur Latenz.
     */
    public int retryPending() {
        if (store.pendingCountGesamt() == 0 || !classifier.isAvailable()) {
            return 0;
        }
        int done = 0;
        for (Mailkonto konto : konten.aktive()) {
            try {
                done += retryPending(konto);
            } catch (RuntimeException e) {
                LOG.errorf(e, "Nachlauf fuer %s fehlgeschlagen: %s", konto.email(), e.getMessage());
            }
        }
        return done;
    }

    private int retryPending(Mailkonto konto) {
        int done = 0;
        for (TriageStorePort.PendingMail pending : store.pending(konto, batchMax)) {
            Optional<EmailMessage> mail = mailbox.fetchByUid(konto, pending.uid());
            if (mail.isEmpty()) {
                // Mail existiert nicht mehr in der INBOX (von Hand archiviert/verschoben)
                store.resolvePending(konto, pending.messageId());
                continue;
            }
            triagiere(konto, mail.get());
            done++;
        }
        return done;
    }

    private List<String> bestimmeLabels(TriageResult result) {
        List<String> labels = new ArrayList<>();
        if (result.quelle() == TriageResult.Quelle.MODELL && result.istUnsicher(confidenceSchwelle)) {
            unsicher.increment();
            labels.add(Kategorie.UNSICHER.label());
            return labels;
        }
        labels.add(result.kategorie().label());
        if (result.aktion().erfordertHandlung()) {
            // Bindestrich statt "/": der Label-Namensraum bleibt flach unter dem AI-Praefix
            labels.add("Aktion-" + result.aktion().label());
        }
        return labels;
    }

    private long resolveLastUid(Mailkonto konto, long uidValidity) {
        Optional<TriageStorePort.MailboxState> state = store.mailboxState(konto);
        if (state.isEmpty()) {
            // Erstlauf: nichts rueckwirkend aufarbeiten, ab jetzt zaehlen.
            long hoechste = mailbox.highestUid(konto);
            store.saveMailboxState(konto, uidValidity, hoechste);
            LOG.infof("[%s] Erstlauf: starte ab uid=%d", konto.email(), hoechste);
            return hoechste;
        }
        if (state.get().uidValidity() != uidValidity) {
            // Gmail hat die UIDs neu vergeben - gemerkte UID ist wertlos.
            LOG.warnf("[%s] UIDVALIDITY hat gewechselt (%d -> %d), setze Zustand zurueck",
                    konto.email(), state.get().uidValidity(), uidValidity);
            long hoechste = mailbox.highestUid(konto);
            store.saveMailboxState(konto, uidValidity, hoechste);
            return hoechste;
        }
        return state.get().lastUid();
    }
}
