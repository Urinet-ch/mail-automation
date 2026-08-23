package ch.aschwanden.mailtriage.domain;

import java.time.Instant;

/**
 * Eine Mail, wie die Triage sie sieht: bereits auf Text reduziert und gekuerzt.
 * bodyText ist fremder, nicht vertrauenswuerdiger Inhalt - er wird ausschliesslich
 * als Daten behandelt und nie als Anweisung interpretiert.
 */
public record EmailMessage(
        long uid,
        String messageId,
        String from,
        String subject,
        Instant receivedAt,
        String bodyText,
        boolean hasListUnsubscribe,
        boolean fromSelf) {

    public EmailMessage {
        if (messageId == null || messageId.isBlank()) {
            // Fallback fuer exotische Mails ohne Message-ID: UID ist pro Mailbox eindeutig
            messageId = "uid:" + uid;
        }
        subject = subject == null ? "(kein Betreff)" : subject;
        bodyText = bodyText == null ? "" : bodyText;
    }
}
