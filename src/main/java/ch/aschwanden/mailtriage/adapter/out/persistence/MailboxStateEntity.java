package ch.aschwanden.mailtriage.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Verarbeitungszustand einer Mailbox - genau eine Zeile je Konto. */
@Entity
@Table(name = "mailbox_state")
public class MailboxStateEntity extends PanacheEntityBase {

    // Die id vergibt seit Change Set 005 die Datenbank; vorher war sie fest 1,
    // weil es nur eine Zeile gab. Fachlich identifiziert wird ueber kontoId.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public int id;

    /** Bezug zum Konto. Nullable nur wegen der Altdaten aus der Ein-Konto-Zeit;
     *  KontoBootstrap traegt sie beim ersten Start nach. */
    @Column(name = "konto_id")
    public Long kontoId;

    @Column(name = "uid_validity", nullable = false)
    public long uidValidity;

    @Column(name = "last_uid", nullable = false)
    public long lastUid;
}
