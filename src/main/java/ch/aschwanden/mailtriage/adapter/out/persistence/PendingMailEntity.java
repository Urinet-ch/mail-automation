package ch.aschwanden.mailtriage.adapter.out.persistence;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Mails, die auf Klassifikation warten (LM Studio nicht erreichbar). */
@Entity
@Table(name = "pending_mail")
public class PendingMailEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "message_id", nullable = false, unique = true, length = 512)
    public String messageId;

    @Column(name = "gmail_uid", nullable = false)
    public long gmailUid;

    @Column(name = "grund", length = 1024)
    public String grund;

    @Column(name = "since", nullable = false)
    public Instant since;
}
