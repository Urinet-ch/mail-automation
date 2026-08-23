package ch.aschwanden.mailtriage.port;

/**
 * Driven Port fuer den Tages-Digest. Der Digest enthaelt Mail-Inhalte und geht
 * deshalb NIE ueber einen oeffentlichen Push-Dienst, sondern als Mail an das
 * eigene Konto - damit verlaesst er den bestehenden Vertrauensbereich nicht.
 */
public interface DigestMailPort {

    /** Sendet den Digest als Mail an die eigene Adresse. */
    void sendToSelf(String subject, String textBody);
}
