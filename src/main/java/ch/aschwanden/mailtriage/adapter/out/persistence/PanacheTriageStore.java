package ch.aschwanden.mailtriage.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import ch.aschwanden.mailtriage.domain.EmailMessage;
import ch.aschwanden.mailtriage.domain.TriageResult;
import ch.aschwanden.mailtriage.port.TriageStorePort;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/** Postgres-Umsetzung des Audit-Logs und Verarbeitungszustands. */
@ApplicationScoped
public class PanacheTriageStore implements TriageStorePort {

    @ConfigProperty(name = "lmstudio.model")
    String modelName;

    @Override
    public Optional<MailboxState> mailboxState() {
        MailboxStateEntity e = MailboxStateEntity.findById(1);
        return e == null ? Optional.empty() : Optional.of(new MailboxState(e.uidValidity, e.lastUid));
    }

    @Override
    @Transactional
    public void saveMailboxState(long uidValidity, long lastUid) {
        MailboxStateEntity e = MailboxStateEntity.findById(1);
        if (e == null) {
            e = new MailboxStateEntity();
            e.id = 1;
            e.uidValidity = uidValidity;
            e.lastUid = lastUid;
            e.persist();
        } else {
            e.uidValidity = uidValidity;
            e.lastUid = Math.max(e.lastUid, lastUid);
        }
    }

    @Override
    public boolean isProcessed(String messageId) {
        return TriageRecordEntity.count("messageId", messageId) > 0;
    }

    @Override
    @Transactional
    public void saveResult(EmailMessage mail, TriageResult result, List<String> labelsApplied) {
        if (isProcessed(mail.messageId())) {
            return;
        }
        TriageRecordEntity e = new TriageRecordEntity();
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
    public void savePending(EmailMessage mail, String grund) {
        PendingMailEntity existing = PendingMailEntity.find("messageId", mail.messageId()).firstResult();
        if (existing != null) {
            existing.grund = grund;
            return;
        }
        PendingMailEntity e = new PendingMailEntity();
        e.messageId = mail.messageId();
        e.gmailUid = mail.uid();
        e.grund = grund;
        e.since = Instant.now();
        e.persist();
    }

    @Override
    public List<PendingMail> pending(int maxCount) {
        return PendingMailEntity.<PendingMailEntity>findAll(Sort.by("since"))
                .page(0, maxCount).list().stream()
                .map(e -> new PendingMail(e.messageId, e.gmailUid, e.since))
                .toList();
    }

    @Override
    @Transactional
    public void resolvePending(String messageId) {
        PendingMailEntity.delete("messageId", messageId);
    }

    @Override
    public long pendingCount() {
        return PendingMailEntity.count();
    }

    @Override
    public Optional<Instant> oldestPendingSince() {
        PendingMailEntity e = PendingMailEntity.<PendingMailEntity>findAll(Sort.by("since")).firstResult();
        return e == null ? Optional.empty() : Optional.of(e.since);
    }

    @Override
    public List<DigestEntry> resultsSince(Instant since) {
        return TriageRecordEntity.<TriageRecordEntity>find("processedAt >= ?1", Sort.by("processedAt"), since)
                .list().stream()
                .map(e -> new DigestEntry(e.sender, e.subject,
                        ch.aschwanden.mailtriage.domain.Kategorie.valueOf(e.kategorie).label(),
                        ch.aschwanden.mailtriage.domain.Aktion.valueOf(e.aktion).label(),
                        e.frist, e.zusammenfassung, e.confidence))
                .toList();
    }
}
