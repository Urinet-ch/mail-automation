package ch.aschwanden.mailtriage.application;

import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import ch.aschwanden.mailtriage.domain.Mailkonto;
import ch.aschwanden.mailtriage.port.KontoStorePort;
import ch.aschwanden.mailtriage.port.MailboxPort;
import ch.aschwanden.mailtriage.port.TriageStorePort;
import ch.aschwanden.mailtriage.port.TriageStorePort.KontoStatistik;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Kontoverwaltung als Use-Case: anlegen, aendern, aktivieren, loeschen.
 *
 * <p>Ein Konto wird nur gespeichert, wenn sich damit tatsaechlich anmelden laesst.
 * Sonst stuende ein totes Konto in der Liste und die Fehlermeldung erschiene erst
 * Minuten spaeter im Log des naechsten Triage-Laufs.
 */
@ApplicationScoped
public class KontoService {

    private static final Logger LOG = Logger.getLogger(KontoService.class);

    @Inject
    KontoStorePort konten;

    @Inject
    MailboxPort mailbox;

    @Inject
    TriageStorePort store;

    public List<Mailkonto> alle() {
        return konten.alle();
    }

    public Optional<Mailkonto> finde(long id) {
        return konten.findeById(id);
    }

    public KontoStatistik statistik(Mailkonto konto) {
        return store.statistik(konto);
    }

    /**
     * Legt ein Konto an, nachdem die Zugangsdaten geprueft wurden.
     *
     * @throws KontoFehler wenn die Adresse schon erfasst ist oder die Anmeldung scheitert
     */
    public Mailkonto anlegen(Mailkonto neu) {
        if (konten.findeByEmail(neu.email()).isPresent()) {
            throw new KontoFehler("Fuer " + neu.email() + " ist bereits ein Konto erfasst.");
        }
        pruefe(neu);
        Mailkonto gespeichert = konten.speichere(neu);
        LOG.infof("Konto angelegt: %s", gespeichert.email());
        return gespeichert;
    }

    /**
     * Aendert Anzeigename, Label-Praefix und optional das Passwort. Die Adresse bleibt,
     * weil an ihr der gesamte Verarbeitungszustand haengt - ein anderes Postfach ist ein
     * anderes Konto.
     */
    public Mailkonto aendern(long id, String name, String labelPrefix, String neuesPasswortOderLeer) {
        Mailkonto vorhanden = konten.findeById(id)
                .orElseThrow(() -> new KontoFehler("Konto " + id + " existiert nicht."));
        boolean passwortNeu = neuesPasswortOderLeer != null && !neuesPasswortOderLeer.isBlank();
        Mailkonto geaendert = new Mailkonto(vorhanden.id(), name, vorhanden.email(),
                passwortNeu ? neuesPasswortOderLeer : vorhanden.appPasswort(),
                vorhanden.imapHost(), vorhanden.imapPort(),
                vorhanden.smtpHost(), vorhanden.smtpPort(),
                labelPrefix, vorhanden.aktiv());
        if (passwortNeu) {
            pruefe(geaendert);
            // Die offene Verbindung haengt noch am alten Passwort.
            mailbox.schliesse(vorhanden);
        }
        return konten.speichere(geaendert);
    }

    public Mailkonto setzeAktiv(long id, boolean aktiv) {
        Mailkonto vorhanden = konten.findeById(id)
                .orElseThrow(() -> new KontoFehler("Konto " + id + " existiert nicht."));
        if (!aktiv) {
            mailbox.schliesse(vorhanden);
        }
        return konten.speichere(vorhanden.mitAktiv(aktiv));
    }

    /**
     * Loescht Konto und Verlauf. Gesetzte Labels bleiben in der Mailbox - die
     * Anwendung entfernt grundsaetzlich nichts aus einem Postfach.
     */
    public void loeschen(long id) {
        konten.findeById(id).ifPresent(konto -> {
            mailbox.schliesse(konto);
            konten.loesche(id);
            LOG.infof("Konto geloescht: %s", konto.email());
        });
    }

    /** Meldet sich testweise an und uebersetzt technische Fehler in eine lesbare Meldung. */
    public void pruefe(Mailkonto konto) {
        try {
            mailbox.pruefeVerbindung(konto);
        } catch (RuntimeException e) {
            throw new KontoFehler("Anmeldung bei " + konto.email() + " fehlgeschlagen: "
                    + ursache(e) + " Bei Gmail braucht es ein App-Passwort (16 Zeichen), "
                    + "nicht das Kontopasswort.", e);
        }
    }

    private static String ursache(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() == null ? e.toString() : t.getMessage();
    }

    /** Fachlicher Fehler, der in der UI angezeigt werden soll - kein Programmfehler. */
    public static class KontoFehler extends RuntimeException {
        public KontoFehler(String message) {
            super(message);
        }

        public KontoFehler(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
