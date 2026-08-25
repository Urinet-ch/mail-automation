package ch.aschwanden.mailtriage.port;

import java.util.List;
import java.util.Optional;

import ch.aschwanden.mailtriage.domain.Mailkonto;

/**
 * Driven Port zur Kontoverwaltung. Die Implementierung verschluesselt das
 * App-Passwort vor dem Schreiben und entschluesselt es beim Lesen - oberhalb
 * dieses Ports existiert nur Klartext, in der Datenbank nur Chiffrat.
 */
public interface KontoStorePort {

    List<Mailkonto> alle();

    /** Nur Konten, die triagiert werden sollen. */
    List<Mailkonto> aktive();

    Optional<Mailkonto> findeById(long id);

    Optional<Mailkonto> findeByEmail(String email);

    /** Legt an oder aktualisiert; gibt das Konto mit vergebener id zurueck. */
    Mailkonto speichere(Mailkonto konto);

    /**
     * Loescht das Konto samt seinem Verarbeitungszustand, Audit-Log und
     * vorgemerkten Mails. In Gmail gesetzte Labels bleiben bestehen - die
     * Anwendung entfernt nie etwas aus einer Mailbox.
     */
    void loesche(long id);

    /**
     * Ordnet Zustand, Audit-Log und Warteschlange ohne Kontobezug diesem Konto zu.
     * Einmalige Uebernahme der Altdaten aus der Ein-Konto-Zeit.
     */
    AltdatenUebernahme uebernehmeAltdaten(long kontoId);

    record AltdatenUebernahme(int zustand, int auditEintraege, int vorgemerkt) { }
}
