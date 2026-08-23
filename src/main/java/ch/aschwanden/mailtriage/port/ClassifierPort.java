package ch.aschwanden.mailtriage.port;

import ch.aschwanden.mailtriage.domain.EmailMessage;
import ch.aschwanden.mailtriage.domain.TriageResult;

/**
 * Driven Port zum Klassifikator. Heute: LM Studio auf dem Mac (Gemma 4 lokal).
 * Der Port ist der Kern der Zukunftssicherheit - laeuft das Modell irgendwann
 * woanders, wird nur der Adapter getauscht.
 */
public interface ClassifierPort {

    /**
     * Klassifiziert eine Mail. Ein Aufruf, ein JSON-Ergebnis - der Klassifikator
     * hat bewusst keine Tools und kann nichts tun ausser antworten.
     *
     * @throws ClassifierUnavailableException wenn der Klassifikator nicht erreichbar ist
     *         (Mac schlaeft, LM Studio nicht gestartet) - die Mail bleibt dann PENDING.
     */
    TriageResult classify(EmailMessage mail);

    /** Schneller Erreichbarkeitstest fuer Health-Check und Metriken. */
    boolean isAvailable();

    class ClassifierUnavailableException extends RuntimeException {
        public ClassifierUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
