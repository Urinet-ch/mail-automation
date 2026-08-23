package ch.aschwanden.mailtriage.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import ch.aschwanden.mailtriage.domain.EmailMessage;
import ch.aschwanden.mailtriage.domain.Kategorie;
import ch.aschwanden.mailtriage.domain.PreFilter;
import ch.aschwanden.mailtriage.domain.TriageResult;
import ch.aschwanden.mailtriage.port.ClassifierPort;
import ch.aschwanden.mailtriage.port.ClassifierPort.ClassifierUnavailableException;
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
    MeterRegistry metrics;

    @ConfigProperty(name = "triage.confidence-schwelle", defaultValue = "0.7")
    double confidenceSchwelle;

    @ConfigProperty(name = "triage.batch-max", defaultValue = "50")
    int batchMax;

    @ConfigProperty(name = "triage.vorfilter.werbung-absender", defaultValue = "")
    List<String> werbungAbsender;

    @ConfigProperty(name = "triage.vorfilter.benachrichtigung-absender", defaultValue = "")
    List<String> benachrichtigungAbsender;

    private PreFilter preFilter;
    private Counter verarbeitet;
    private Counter vorfilterTreffer;
    private Counter modellTreffer;
    private Counter unsicher;
    private Counter fehlgeschlagen;

    @PostConstruct
    void init() {
        preFilter = new PreFilter(werbungAbsender, benachrichtigungAbsender);
        verarbeitet = metrics.counter("mail_triage_verarbeitet_total");
        vorfilterTreffer = metrics.counter("mail_triage_entschieden_total", "quelle", "vorfilter");
        modellTreffer = metrics.counter("mail_triage_entschieden_total", "quelle", "modell");
        unsicher = metrics.counter("mail_triage_unsicher_total");
        fehlgeschlagen = metrics.counter("mail_triage_fehlgeschlagen_total");
        metrics.gauge("mail_triage_pending", store, s -> (double) s.pendingCount());
    }

    /**
     * Ein Triage-Lauf. Wird vom Scheduler und von REST aufgerufen.
     *
     * @return Anzahl in diesem Lauf verarbeiteter Mails
     */
    public int run() {
        long uidValidity = mailbox.uidValidity();
        long lastUid = resolveLastUid(uidValidity);

        List<EmailMessage> neue = mailbox.fetchNewerThan(lastUid, batchMax);
        if (neue.isEmpty()) {
            return 0;
        }
        LOG.infof("%d neue Mail(s) ab uid=%d", neue.size(), lastUid);

        int count = 0;
        for (EmailMessage mail : neue) {
            triagiere(mail);
            // Fortschritt nach JEDER Mail persistieren: ein Absturz mitten im
            // Batch verarbeitet nach Neustart nichts doppelt (zusaetzlich zur
            // Message-ID-Idempotenz).
            store.saveMailboxState(uidValidity, mail.uid());
            count++;
        }
        return count;
    }

    private void triagiere(EmailMessage mail) {
        if (store.isProcessed(mail.messageId())) {
            LOG.debugf("Bereits verarbeitet: %s", mail.messageId());
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
                LOG.infof("Classifier nicht erreichbar, Mail bleibt vorgemerkt: uid=%d", mail.uid());
                store.savePending(mail, e.getMessage());
                return;
            } catch (RuntimeException e) {
                LOG.errorf(e, "Klassifikation fehlgeschlagen: uid=%d", mail.uid());
                fehlgeschlagen.increment();
                store.savePending(mail, "Fehler: " + e.getMessage());
                return;
            }
        }

        List<String> labels = bestimmeLabels(result);
        mailbox.applyLabels(mail.uid(), labels);
        store.saveResult(mail, result, labels);
        store.resolvePending(mail.messageId());
        LOG.infof("uid=%d -> %s / %s (%.0f%%, %s)", mail.uid(), result.kategorie(),
                result.aktion(), result.confidence() * 100, result.quelle());
    }

    /**
     * Nachlauf fuer vorgemerkte Mails, sobald der Classifier wieder da ist.
     * Kein Datenverlust bei schlafendem Mac - nur Latenz.
     */
    public int retryPending() {
        if (store.pendingCount() == 0 || !classifier.isAvailable()) {
            return 0;
        }
        int done = 0;
        for (TriageStorePort.PendingMail pending : store.pending(batchMax)) {
            Optional<EmailMessage> mail = mailbox.fetchByUid(pending.uid());
            if (mail.isEmpty()) {
                // Mail existiert nicht mehr in der INBOX (von Hand archiviert/verschoben)
                store.resolvePending(pending.messageId());
                continue;
            }
            triagiere(mail.get());
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

    private long resolveLastUid(long uidValidity) {
        Optional<TriageStorePort.MailboxState> state = store.mailboxState();
        if (state.isEmpty()) {
            // Erstlauf: nichts rueckwirkend aufarbeiten, ab jetzt zaehlen.
            long hoechste = mailbox.highestUid();
            store.saveMailboxState(uidValidity, hoechste);
            LOG.infof("Erstlauf: starte ab uid=%d", hoechste);
            return hoechste;
        }
        if (state.get().uidValidity() != uidValidity) {
            // Gmail hat die UIDs neu vergeben - gemerkte UID ist wertlos.
            LOG.warnf("UIDVALIDITY hat gewechselt (%d -> %d), setze Zustand zurueck",
                    state.get().uidValidity(), uidValidity);
            long hoechste = mailbox.highestUid();
            store.saveMailboxState(uidValidity, hoechste);
            return hoechste;
        }
        return state.get().lastUid();
    }
}
