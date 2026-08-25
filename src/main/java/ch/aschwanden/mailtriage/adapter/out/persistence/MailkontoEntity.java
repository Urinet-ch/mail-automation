package ch.aschwanden.mailtriage.adapter.out.persistence;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ein ueberwachtes Postfach. app_passwort_enc ist AES-GCM-Chiffrat, nie Klartext. */
@Entity
@Table(name = "mailkonto")
public class MailkontoEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "email", nullable = false, unique = true)
    public String email;

    @Column(name = "imap_host", nullable = false)
    public String imapHost;

    @Column(name = "imap_port", nullable = false)
    public int imapPort;

    @Column(name = "smtp_host", nullable = false)
    public String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    public int smtpPort;

    @Column(name = "label_prefix", nullable = false)
    public String labelPrefix;

    @Column(name = "app_passwort_enc", nullable = false)
    public String appPasswortEnc;

    @Column(name = "aktiv", nullable = false)
    public boolean aktiv;

    @Column(name = "erstellt_am", nullable = false)
    public Instant erstelltAm;
}
