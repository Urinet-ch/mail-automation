package ch.aschwanden.mailtriage.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import ch.aschwanden.mailtriage.domain.Mailkonto;
import ch.aschwanden.mailtriage.port.KontoStorePort;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Postgres-Umsetzung der Kontoverwaltung. Verschluesselt beim Schreiben, entschluesselt beim Lesen. */
@ApplicationScoped
public class PanacheKontoStore implements KontoStorePort {

    @Inject
    Verschluesselung crypto;

    @Override
    public List<Mailkonto> alle() {
        return MailkontoEntity.<MailkontoEntity>findAll(Sort.by("email")).list().stream()
                .map(this::zuDomain).toList();
    }

    @Override
    public List<Mailkonto> aktive() {
        return MailkontoEntity.<MailkontoEntity>find("aktiv", Sort.by("email"), true).list().stream()
                .map(this::zuDomain).toList();
    }

    @Override
    public Optional<Mailkonto> findeById(long id) {
        return Optional.ofNullable(MailkontoEntity.<MailkontoEntity>findById(id)).map(this::zuDomain);
    }

    @Override
    public Optional<Mailkonto> findeByEmail(String email) {
        return MailkontoEntity.<MailkontoEntity>find("email", email.trim().toLowerCase())
                .firstResultOptional().map(this::zuDomain);
    }

    @Override
    @Transactional
    public Mailkonto speichere(Mailkonto konto) {
        MailkontoEntity e = konto.id() == null ? null : MailkontoEntity.findById(konto.id());
        boolean neu = e == null;
        if (neu) {
            e = new MailkontoEntity();
            e.erstelltAm = Instant.now();
        }
        e.name = konto.name();
        e.email = konto.email();
        e.imapHost = konto.imapHost();
        e.imapPort = konto.imapPort();
        e.smtpHost = konto.smtpHost();
        e.smtpPort = konto.smtpPort();
        e.labelPrefix = konto.labelPrefix();
        e.appPasswortEnc = crypto.verschluessle(konto.appPasswort());
        e.aktiv = konto.aktiv();
        if (neu) {
            e.persist();
            // flush, damit die von der Datenbank vergebene id vorliegt: der Aufrufer
            // braucht sie sofort, um den Verarbeitungszustand anzulegen.
            e.flush();
        }
        return konto.mitId(e.id);
    }

    @Override
    @Transactional
    public void loesche(long id) {
        // Reihenfolge wegen der Fremdschluessel: erst die abhaengigen Zeilen.
        PendingMailEntity.delete("kontoId", id);
        TriageRecordEntity.delete("kontoId", id);
        MailboxStateEntity.delete("kontoId", id);
        MailkontoEntity.deleteById(id);
    }

    @Override
    @Transactional
    public AltdatenUebernahme uebernehmeAltdaten(long kontoId) {
        return new AltdatenUebernahme(
                MailboxStateEntity.update("kontoId = ?1 where kontoId is null", kontoId),
                TriageRecordEntity.update("kontoId = ?1 where kontoId is null", kontoId),
                PendingMailEntity.update("kontoId = ?1 where kontoId is null", kontoId));
    }

    private Mailkonto zuDomain(MailkontoEntity e) {
        return new Mailkonto(e.id, e.name, e.email, crypto.entschluessle(e.appPasswortEnc),
                e.imapHost, e.imapPort, e.smtpHost, e.smtpPort, e.labelPrefix, e.aktiv);
    }
}
