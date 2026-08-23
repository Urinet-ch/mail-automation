package ch.aschwanden.mailtriage.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import ch.aschwanden.mailtriage.domain.EmailMessage;
import ch.aschwanden.mailtriage.domain.TriageResult;

/**
 * Driven Port zum Audit-Log und Verarbeitungszustand.
 * Jede Entscheidung wird protokolliert - das ist die Grundlage, um
 * Fehlklassifikationen auszuwerten und den Digest zu bauen.
 */
public interface TriageStorePort {

    /** Zuletzt verarbeitete UID (und UIDVALIDITY) der Mailbox. */
    Optional<MailboxState> mailboxState();

    void saveMailboxState(long uidValidity, long lastUid);

    boolean isProcessed(String messageId);

    /** Erfolgreich triagierte Mail protokollieren. */
    void saveResult(EmailMessage mail, TriageResult result, List<String> labelsApplied);

    /** Mail vormerken, deren Klassifikation fehlgeschlagen ist (Classifier nicht erreichbar). */
    void savePending(EmailMessage mail, String grund);

    /** Vorgemerkte Mails, aelteste zuerst. */
    List<PendingMail> pending(int maxCount);

    void resolvePending(String messageId);

    long pendingCount();

    /** Alter der aeltesten vorgemerkten Mail - Grundlage fuer den Stau-Alarm. */
    Optional<Instant> oldestPendingSince();

    /** Triage-Eintraege seit einem Zeitpunkt, fuer den Digest. */
    List<DigestEntry> resultsSince(Instant since);

    record MailboxState(long uidValidity, long lastUid) { }

    record PendingMail(String messageId, long uid, Instant since) { }

    record DigestEntry(String from, String subject, String kategorie, String aktion,
            java.time.LocalDate frist, String zusammenfassung, double confidence) { }
}
