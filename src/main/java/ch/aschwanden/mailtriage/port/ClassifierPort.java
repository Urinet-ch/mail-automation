package ch.aschwanden.mailtriage.port;

import ch.aschwanden.mailtriage.domain.EmailMessage;
import ch.aschwanden.mailtriage.domain.Einstellungen;
import ch.aschwanden.mailtriage.domain.Mailkonto;
import ch.aschwanden.mailtriage.domain.TriageResult;

/**
 * Driven Port zum Klassifikator. Heute: LM Studio auf dem Mac (Gemma 4 lokal).
 * Der Port ist der Kern der Zukunftssicherheit - laeuft das Modell irgendwann
 * woanders, wird nur der Adapter getauscht.
 *
 * <p>Die {@link Einstellungen} kommen als Parameter herein, statt dass der Adapter sie
 * sich holt: ein Driven Adapter darf nicht in die Anwendungsschicht zurueckgreifen.
 */
public interface ClassifierPort {

    /**
     * Klassifiziert eine Mail. Ein Aufruf, ein JSON-Ergebnis - der Klassifikator
     * hat bewusst keine Tools und kann nichts tun ausser antworten.
     *
     * <p>Das Konto liefert dem Modell den Kontext, um welches Postfach es geht - eine
     * Mail an eine Arbeitsadresse ist anders zu werten als dieselbe Mail privat.
     *
     * @throws ClassifierUnavailableException wenn der Klassifikator nicht erreichbar ist
     *         (Mac schlaeft, LM Studio nicht gestartet) - die Mail bleibt dann PENDING.
     */
    TriageResult classify(Mailkonto konto, EmailMessage mail, Einstellungen einstellungen);

    /**
     * Schneller Erreichbarkeitstest fuer Health-Check, Metriken und den
     * "Verbindung testen"-Knopf. Die Adresse wird uebergeben statt gelesen, damit sich
     * auch eine noch nicht gespeicherte pruefen laesst.
     */
    boolean isAvailable(String modellUrl);

    class ClassifierUnavailableException extends RuntimeException {
        public ClassifierUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
