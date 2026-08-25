package ch.aschwanden.mailtriage.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import ch.aschwanden.mailtriage.domain.EmailMessage;
import ch.aschwanden.mailtriage.domain.Mailkonto;
import ch.aschwanden.mailtriage.domain.TriageResult;

/**
 * Driven Port zum Audit-Log und Verarbeitungszustand.
 * Jede Entscheidung wird protokolliert - das ist die Grundlage, um
 * Fehlklassifikationen auszuwerten und den Digest zu bauen.
 *
 * <p>Alles ist auf ein Konto bezogen. Insbesondere die Idempotenz: dieselbe
 * Message-ID kann in zwei Postfaechern liegen (CC, Verteiler) und muss dann
 * auch in beiden gelabelt werden.
 */
public interface TriageStorePort {

    /** Zuletzt verarbeitete UID (und UIDVALIDITY) des Kontos. */
    Optional<MailboxState> mailboxState(Mailkonto konto);

    void saveMailboxState(Mailkonto konto, long uidValidity, long lastUid);

    boolean isProcessed(Mailkonto konto, String messageId);

    /** Erfolgreich triagierte Mail protokollieren. */
    void saveResult(Mailkonto konto, EmailMessage mail, TriageResult result, List<String> labelsApplied);

    /** Mail vormerken, deren Klassifikation fehlgeschlagen ist (Classifier nicht erreichbar). */
    void savePending(Mailkonto konto, EmailMessage mail, String grund);

    /** Vorgemerkte Mails des Kontos, aelteste zuerst. */
    List<PendingMail> pending(Mailkonto konto, int maxCount);

    void resolvePending(Mailkonto konto, String messageId);

    long pendingCount(Mailkonto konto);

    /** Vorgemerkte Mails ueber alle Konten - Grundlage fuer die Metrik. */
    long pendingCountGesamt();

    /** Alter der aeltesten vorgemerkten Mail ueber alle Konten - Grundlage fuer den Stau-Alarm. */
    Optional<Instant> oldestPendingSince();

    /** Triage-Eintraege des Kontos seit einem Zeitpunkt, fuer den Digest. */
    List<DigestEntry> resultsSince(Mailkonto konto, Instant since);

    /** Kennzahlen fuer die Kontouebersicht in der UI. */
    KontoStatistik statistik(Mailkonto konto);

    record MailboxState(long uidValidity, long lastUid) { }

    record PendingMail(String messageId, long uid, Instant since) { }

    record DigestEntry(String from, String subject, String kategorie, String aktion,
            LocalDate frist, String zusammenfassung, double confidence) { }

    record KontoStatistik(long verarbeitet, long pending, Optional<Instant> letzterLauf,
            Optional<Long> lastUid) { }
}
