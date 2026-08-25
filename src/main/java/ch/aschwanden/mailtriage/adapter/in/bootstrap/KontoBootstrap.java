package ch.aschwanden.mailtriage.adapter.in.bootstrap;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import ch.aschwanden.mailtriage.domain.Mailkonto;
import ch.aschwanden.mailtriage.port.KontoStorePort;
import ch.aschwanden.mailtriage.port.KontoStorePort.AltdatenUebernahme;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Uebernimmt das frueher ueber Umgebungsvariablen konfigurierte Konto einmalig in die
 * Datenbank und haengt die Altdaten daran.
 *
 * <p>Warum ueberhaupt: Bis zur Mehrkonten-Faehigkeit kamen Adresse und App-Passwort aus
 * GMAIL_USER/GMAIL_APP_PASSWORD, und mailbox_state, triage_record und pending_mail hatten
 * keinen Kontobezug. Ohne diese Uebernahme haette die Anwendung nach dem Update kein Konto
 * mehr gekannt und mit einem "Erstlauf" bei der aktuellen UID neu begonnen - der gesamte
 * Verlauf waere verwaist und alle Mails dazwischen unklassifiziert geblieben.
 *
 * <p>Laeuft nach Liquibase und ist idempotent: gibt es die Adresse bereits als Konto,
 * geschieht nichts. Nach der Uebernahme koennen die beiden Umgebungsvariablen weg.
 */
@ApplicationScoped
public class KontoBootstrap {

    private static final Logger LOG = Logger.getLogger(KontoBootstrap.class);

    @Inject
    KontoStorePort konten;

    @ConfigProperty(name = "gmail.user")
    Optional<String> altUser;

    @ConfigProperty(name = "gmail.app-password")
    Optional<String> altPasswort;

    @ConfigProperty(name = "gmail.label-prefix", defaultValue = "AI/")
    String altPrefix;

    void beimStart(@Observes StartupEvent ev) {
        try {
            uebernehmen();
        } catch (RuntimeException e) {
            // Nicht den Start verhindern: die Anwendung ist ohne Uebernahme benutzbar,
            // Konten lassen sich in der UI anlegen. Der Fehler muss aber sichtbar sein.
            LOG.errorf(e, "Uebernahme des Alt-Kontos fehlgeschlagen: %s", e.getMessage());
        }
    }

    void uebernehmen() {
        String email = altUser.map(String::trim).filter(s -> !s.isBlank()).orElse(null);
        String passwort = altPasswort.map(String::trim).filter(s -> !s.isBlank()).orElse(null);
        if (email == null || passwort == null) {
            LOG.debug("Keine Alt-Konfiguration (GMAIL_USER/GMAIL_APP_PASSWORD) vorhanden.");
            return;
        }
        if (konten.findeByEmail(email).isPresent()) {
            return;
        }

        Mailkonto konto = konten.speichere(new Mailkonto(null, email, email, passwort,
                "imap.gmail.com", 993, "smtp.gmail.com", 465, altPrefix, true));

        // Altdaten anhaengen: alles ohne Kontobezug stammt zwangslaeufig aus der
        // Ein-Konto-Zeit und gehoert damit diesem Konto.
        AltdatenUebernahme u = konten.uebernehmeAltdaten(konto.id());

        LOG.infof("Konto %s aus der Umgebung uebernommen (id=%d); zugeordnet: %d Zustand, "
                + "%d Audit-Eintraege, %d vorgemerkt. GMAIL_USER/GMAIL_APP_PASSWORD koennen "
                + "jetzt aus dem Secret entfernt werden.",
                email, konto.id(), u.zustand(), u.auditEintraege(), u.vorgemerkt());
    }
}
