package ch.aschwanden.mailtriage.port;

import java.util.List;

import ch.aschwanden.mailtriage.domain.EmailMessage;

/**
 * Driven Port zur Mailbox. Bewusst minimal: lesen und labeln.
 * Kein Senden, kein Loeschen, kein Archivieren - in keiner Implementierung.
 */
public interface MailboxPort {

    /** Aktueller UIDVALIDITY-Wert der INBOX - aendert er sich, sind gemerkte UIDs ungueltig. */
    long uidValidity();

    /** Hoechste aktuell vorhandene UID der INBOX (0 bei leerer Mailbox). */
    long highestUid();

    /** Alle Nachrichten mit UID groesser als {@code lastUid}, aelteste zuerst. */
    List<EmailMessage> fetchNewerThan(long lastUid, int maxCount);

    /** Einzelne Nachricht per UID, leer wenn nicht (mehr) in der INBOX. */
    java.util.Optional<EmailMessage> fetchByUid(long uid);

    /**
     * Setzt Labels auf eine Nachricht. Die Implementierung erzwingt das AI-Praefix -
     * Labels ausserhalb des Praefix-Namensraums sind nicht moeglich.
     */
    void applyLabels(long uid, List<String> labels);
}
