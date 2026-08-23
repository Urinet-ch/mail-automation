package ch.aschwanden.mailtriage.adapter.in.scheduler;

import org.jboss.logging.Logger;

import ch.aschwanden.mailtriage.application.DigestService;
import ch.aschwanden.mailtriage.application.TriageService;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Driving Adapter: zeitgesteuerte Ausloesung von Triage und Digest. */
@ApplicationScoped
public class TriageScheduler {

    private static final Logger LOG = Logger.getLogger(TriageScheduler.class);

    @Inject
    TriageService triage;

    @Inject
    DigestService digest;

    /** Neue Mails triagieren; ueberlappende Laeufe werden uebersprungen. */
    @Scheduled(every = "{triage.poll-interval}", concurrentExecution = ConcurrentExecution.SKIP)
    void pollAndTriage() {
        try {
            int n = triage.run();
            if (n > 0) {
                LOG.infof("Triage-Lauf: %d Mail(s) verarbeitet", n);
            }
            triage.retryPending();
        } catch (RuntimeException e) {
            // Fehler loggen, nicht eskalieren - der naechste Lauf versucht es erneut.
            // Dauerhafte Probleme werden ueber Metriken/Alerting sichtbar, nicht ueber Crashes.
            LOG.errorf(e, "Triage-Lauf fehlgeschlagen: %s", e.getMessage());
        }
    }

    /** Tages-Digest. */
    @Scheduled(cron = "{digest.cron}", concurrentExecution = ConcurrentExecution.SKIP)
    void dailyDigest() {
        try {
            digest.sendeDigest();
        } catch (RuntimeException e) {
            LOG.errorf(e, "Digest fehlgeschlagen: %s", e.getMessage());
        }
    }
}
