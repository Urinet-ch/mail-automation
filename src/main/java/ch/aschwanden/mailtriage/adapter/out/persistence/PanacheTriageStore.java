package ch.aschwanden.mailtriage.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import ch.aschwanden.mailtriage.domain.Aktion;
import ch.aschwanden.mailtriage.domain.EmailMessage;
import ch.aschwanden.mailtriage.domain.Kategorie;
import ch.aschwanden.mailtriage.domain.Mailkonto;
import ch.aschwanden.mailtriage.domain.TriageResult;
import ch.aschwanden.mailtriage.port.TriageStorePort;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/** Postgres-Umsetzung des Audit-Logs und Verarbeitungszustands, je Konto. */
@ApplicationScoped
public class PanacheTriageStore implements TriageStorePort {

    @ConfigProperty(name = "lmstudio.model")
    String modelName;

    /**
     * Ein Konto ohne id waere ein Programmierfehler: gespeichert wird erst, wenn das
     * Konto in der Datenbank steht. Lieber hier laut scheitern als kontolose Zeilen
     * schreiben, die spaeter niemand mehr zuordnen kann.
     */
    private static long id(Mailkonto konto) {
        Long id = konto.id();
        if (id == null) {
            throw new IllegalStateException("Konto ist noch nicht gespeichert: " + konto);
        }
        return id;
    }

    @Override
    public Optional<MailboxState> mailboxState(Mailkonto konto) {
        return MailboxStateEntity.<MailboxStateEntity>find("kontoId", id(konto))
                .firstResultOptional()
                .map(e -> new MailboxState(e.uidValidity, e.lastUid));
    }

    @Override
    @Transactional
    public void saveMailboxState(Mailkonto konto, long uidValidity, long lastUid) {
        MailboxStateEntity e = MailboxStateEntity.<MailboxStateEntity>find("kontoId", id(konto))
                .firstResult();
        if (e == null) {
            e = new MailboxStateEntity();
            e.kontoId = id(konto);
            e.uidValidity = uidValidity;
            e.lastUid = lastUid;
            e.persist();
            return;
        }
        if (e.uidValidity != uidValidity) {
            // Gmail hat die UIDs neu vergeben - der gemerkte Hoechststand ist wertlos
            // und darf den neuen Wert nicht per Math.max nach oben verfaelschen.
            e.uidValidity = uidValidity;
            e.lastUid = lastUid;
            return;
        }
        e.lastUid = Math.max(e.lastUid, lastUid);
    }

    @Override
    public boolean isProcessed(Mailkonto konto, String messageId) {
        return TriageRecordEntity.count("kontoId = ?1 and messageId = ?2", id(konto), messageId) > 0;
    }

    @Override
    @Transactional
    public void saveResult(Mailkonto konto, EmailMessage mail, TriageResult result,
            List<String> labelsApplied) {
        if (isProcessed(konto, mail.messageId())) {
            return;
        }
        TriageRecordEntity e = new TriageRecordEntity();
        e.kontoId = id(konto);
        e.messageId = mail.messageId();
        e.gmailUid = mail.uid();
        e.sender = mail.from();
        e.subject = mail.subject();
        e.receivedAt = mail.receivedAt();
        e.kategorie = result.kategorie().name();
        e.aktion = result.aktion().name();
        e.frist = result.frist();
        e.zusammenfassung = result.zusammenfassung();
        e.confidence = result.confidence();
        e.quelle = result.quelle().name();
        e.labelsApplied = String.join(",", labelsApplied);
        e.modelName = result.quelle() == TriageResult.Quelle.MODELL ? modelName : null;
        e.processedAt = Instant.now();
        e.persist();
    }

    @Override
    @Transactional
    public void savePending(Mailkonto konto, EmailMessage mail, String grund) {
        PendingMailEntity existing = PendingMailEntity
                .find("kontoId = ?1 and messageId = ?2", id(konto), mail.messageId()).firstResult();
        if (existing != null) {
            existing.grund = grund;
            return;
        }
        PendingMailEntity e = new PendingMailEntity();
        e.kontoId = id(konto);
        e.messageId = mail.messageId();
        e.gmailUid = mail.uid();
        e.grund = grund;
        e.since = Instant.now();
        e.persist();
    }

    @Override
    public List<PendingMail> pending(Mailkonto konto, int maxCount) {
        return PendingMailEntity.<PendingMailEntity>find("kontoId", Sort.by("since"), id(konto))
                .page(0, maxCount).list().stream()
                .map(e -> new PendingMail(e.messageId, e.gmailUid, e.since))
                .toList();
    }

    @Override
    @Transactional
    public void resolvePending(Mailkonto konto, String messageId) {
        PendingMailEntity.delete("kontoId = ?1 and messageId = ?2", id(konto), messageId);
    }

    @Override
    public long pendingCount(Mailkonto konto) {
        return PendingMailEntity.count("kontoId", id(konto));
    }

    @Override
    public long pendingCountGesamt() {
        return PendingMailEntity.count();
    }

    @Override
    public Optional<Instant> oldestPendingSince() {
        return PendingMailEntity.<PendingMailEntity>findAll(Sort.by("since"))
                .firstResultOptional().map(e -> e.since);
    }

    @Override
    public List<DigestEntry> resultsSince(Mailkonto konto, Instant since) {
        return TriageRecordEntity.<TriageRecordEntity>find(
                        "kontoId = ?1 and processedAt >= ?2", Sort.by("processedAt"), id(konto), since)
                .list().stream()
                .map(e -> new DigestEntry(e.sender, e.subject,
                        Kategorie.valueOf(e.kategorie).label(),
                        Aktion.valueOf(e.aktion).label(),
                        e.frist, e.zusammenfassung, e.confidence))
                .toList();
    }

    @Override
    public KontoStatistik statistik(Mailkonto konto) {
        long kontoId = id(konto);
        long verarbeitet = TriageRecordEntity.count("kontoId", kontoId);
        long pending = PendingMailEntity.count("kontoId", kontoId);
        Optional<Instant> letzterLauf = TriageRecordEntity
                .<TriageRecordEntity>find("kontoId", Sort.by("processedAt", Sort.Direction.Descending), kontoId)
                .firstResultOptional().map(e -> e.processedAt);
        Optional<Long> lastUid = MailboxStateEntity.<MailboxStateEntity>find("kontoId", kontoId)
                .firstResultOptional().map(e -> e.lastUid);
        return new KontoStatistik(verarbeitet, pending, letzterLauf, lastUid);
    }
}
