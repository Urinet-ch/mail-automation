package ch.aschwanden.mailtriage.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Audit-Log: eine Zeile pro triagierter Mail. */
@Entity
@Table(name = "triage_record")
public class TriageRecordEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "message_id", nullable = false, unique = true, length = 512)
    public String messageId;

    @Column(name = "gmail_uid", nullable = false)
    public long gmailUid;

    @Column(name = "sender", length = 512)
    public String sender;

    @Column(name = "subject", length = 1024)
    public String subject;

    @Column(name = "received_at", nullable = false)
    public Instant receivedAt;

    @Column(name = "kategorie", nullable = false, length = 32)
    public String kategorie;

    @Column(name = "aktion", nullable = false, length = 32)
    public String aktion;

    @Column(name = "frist")
    public LocalDate frist;

    @Column(name = "zusammenfassung", columnDefinition = "text")
    public String zusammenfassung;

    @Column(name = "confidence", nullable = false)
    public double confidence;

    @Column(name = "quelle", nullable = false, length = 16)
    public String quelle;

    @Column(name = "labels_applied", length = 512)
    public String labelsApplied;

    @Column(name = "model_name", length = 128)
    public String modelName;

    @Column(name = "processed_at", nullable = false)
    public Instant processedAt;
}
