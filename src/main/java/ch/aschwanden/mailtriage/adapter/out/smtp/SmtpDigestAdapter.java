package ch.aschwanden.mailtriage.adapter.out.smtp;

import java.util.Date;
import java.util.Optional;
import java.util.Properties;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import ch.aschwanden.mailtriage.port.DigestMailPort;
import jakarta.inject.Singleton;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Digest als Mail an das eigene Konto, ueber denselben Gmail-Zugang (SMTP).
 * Einziger Sendeweg der Anwendung - und der kann nur an die eigene Adresse.
 */
@Singleton
public class SmtpDigestAdapter implements DigestMailPort {

    private static final Logger LOG = Logger.getLogger(SmtpDigestAdapter.class);

    @ConfigProperty(name = "gmail.user")
    String user;

    @ConfigProperty(name = "gmail.app-password")
    Optional<String> appPassword;

    @ConfigProperty(name = "gmail.smtp-host", defaultValue = "smtp.gmail.com")
    String host;

    @ConfigProperty(name = "gmail.smtp-port", defaultValue = "465")
    int port;

    @Override
    public void sendToSelf(String subject, String textBody) {
        Properties p = new Properties();
        p.put("mail.smtp.host", host);
        p.put("mail.smtp.port", String.valueOf(port));
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.ssl.enable", "true");
        p.put("mail.smtp.connectiontimeout", "15000");
        p.put("mail.smtp.timeout", "30000");
        Session session = Session.getInstance(p);
        try {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(user));
            // Empfaenger ist fest die eigene Adresse - kein anderer Empfaenger moeglich
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(user));
            msg.setSubject(subject, "UTF-8");
            msg.setText(textBody, "UTF-8");
            msg.setSentDate(new Date());
            String pw = appPassword.filter(s -> !s.isBlank())
                    .orElseThrow(() -> new IllegalStateException("GMAIL_APP_PASSWORD ist nicht gesetzt"));
            Transport.send(msg, user, pw);
            LOG.infof("Digest-Mail gesendet: %s", subject);
        } catch (MessagingException e) {
            throw new IllegalStateException("Digest-Versand fehlgeschlagen: " + e.getMessage(), e);
        }
    }
}
