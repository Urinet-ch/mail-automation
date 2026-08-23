package ch.aschwanden.mailtriage.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Verarbeitungszustand der Mailbox - genau eine Zeile. */
@Entity
@Table(name = "mailbox_state")
public class MailboxStateEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public int id;

    @Column(name = "uid_validity", nullable = false)
    public long uidValidity;

    @Column(name = "last_uid", nullable = false)
    public long lastUid;
}
