package ch.aschwanden.mailtriage.port;

import ch.aschwanden.mailtriage.domain.Mailkonto;

/**
 * Driven Port fuer den Tages-Digest. Der Digest enthaelt Mail-Inhalte und geht
 * deshalb NIE ueber einen oeffentlichen Push-Dienst, sondern als Mail an das
 * eigene Konto - damit verlaesst er den bestehenden Vertrauensbereich nicht.
 */
public interface DigestMailPort {

    /**
     * Sendet den Digest an die Adresse des Kontos selbst. Der Empfaenger ist nicht
     * waehlbar: er ist immer {@code konto.email()}, damit aus dem einzigen Sendeweg
     * der Anwendung kein Versandweg nach aussen werden kann.
     */
    void sendToSelf(Mailkonto konto, String subject, String textBody);
}
