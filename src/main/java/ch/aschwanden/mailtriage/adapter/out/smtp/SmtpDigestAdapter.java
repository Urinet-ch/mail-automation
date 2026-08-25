package ch.aschwanden.mailtriage.adapter.out.smtp;

import java.util.Date;
import java.util.Properties;

import org.jboss.logging.Logger;

import ch.aschwanden.mailtriage.domain.Mailkonto;
import ch.aschwanden.mailtriage.port.DigestMailPort;
import jakarta.inject.Singleton;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Digest als Mail an das eigene Konto, ueber dessen eigenen Zugang (SMTP).
 * Einziger Sendeweg der Anwendung - und der kann nur an die Adresse des Kontos
 * selbst, aus dem der Digest stammt.
 */
@Singleton
public class SmtpDigestAdapter implements DigestMailPort {

    private static final Logger LOG = Logger.getLogger(SmtpDigestAdapter.class);

    @Override
    public void sendToSelf(Mailkonto konto, String subject, String textBody) {
        Properties p = new Properties();
        p.put("mail.smtp.host", konto.smtpHost());
        p.put("mail.smtp.port", String.valueOf(konto.smtpPort()));
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.ssl.enable", "true");
        p.put("mail.smtp.connectiontimeout", "15000");
        p.put("mail.smtp.timeout", "30000");
        Session session = Session.getInstance(p);
        try {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(konto.email()));
            // Empfaenger ist fest die Adresse des Kontos - kein anderer Empfaenger moeglich
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(konto.email()));
            msg.setSubject(subject, "UTF-8");
            msg.setText(textBody, "UTF-8");
            msg.setSentDate(new Date());
            Transport.send(msg, konto.email(), konto.appPasswort());
            LOG.infof("Digest-Mail an %s gesendet: %s", konto.email(), subject);
        } catch (MessagingException e) {
            throw new IllegalStateException(
                    "Digest-Versand fuer " + konto.email() + " fehlgeschlagen: " + e.getMessage(), e);
        }
    }
}
