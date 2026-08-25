package ch.aschwanden.mailtriage.port;

import java.util.List;
import java.util.Optional;

import ch.aschwanden.mailtriage.domain.EmailMessage;
import ch.aschwanden.mailtriage.domain.Mailkonto;

/**
 * Driven Port zur Mailbox. Bewusst minimal: lesen und labeln.
 * Kein Senden, kein Loeschen, kein Archivieren - in keiner Implementierung.
 *
 * <p>Jede Operation nennt ihr Konto explizit. Die Alternative - eine Instanz je
 * Konto - haette dieselbe Verbindungsverwaltung in eine Factory verschoben, ohne
 * dass der Aufrufer weniger wissen muesste.
 */
public interface MailboxPort {

    /** Aktueller UIDVALIDITY-Wert der INBOX - aendert er sich, sind gemerkte UIDs ungueltig. */
    long uidValidity(Mailkonto konto);

    /** Hoechste aktuell vorhandene UID der INBOX (0 bei leerer Mailbox). */
    long highestUid(Mailkonto konto);

    /** Alle Nachrichten mit UID groesser als {@code lastUid}, aelteste zuerst. */
    List<EmailMessage> fetchNewerThan(Mailkonto konto, long lastUid, int maxCount);

    /** Einzelne Nachricht per UID, leer wenn nicht (mehr) in der INBOX. */
    Optional<EmailMessage> fetchByUid(Mailkonto konto, long uid);

    /**
     * Setzt Labels auf eine Nachricht. Die Implementierung erzwingt das Praefix des
     * Kontos - Labels ausserhalb dieses Namensraums sind nicht moeglich.
     */
    void applyLabels(Mailkonto konto, long uid, List<String> labels);

    /**
     * Prueft Erreichbarkeit und Zugangsdaten, ohne etwas zu veraendern.
     * Fuer den Verbindungstest beim Anlegen eines Kontos in der UI.
     */
    void pruefeVerbindung(Mailkonto konto);

    /** Verwirft eine zwischengehaltene Verbindung, etwa nach Passwortwechsel oder Loeschen. */
    void schliesse(Mailkonto konto);
}
