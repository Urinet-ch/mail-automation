package ch.aschwanden.mailtriage.adapter.out.imap;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jsoup.Jsoup;

import ch.aschwanden.mailtriage.domain.EmailMessage;
import ch.aschwanden.mailtriage.port.MailboxPort;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.MimeBodyPart;

/**
 * IMAP-Adapter fuer Gmail. Uebernommen und angepasst aus dem in Etappe 0
 * verifizierten gmail-mcp-Server. Bewusst eingeschraenkt: lesen und labeln.
 * Kein Senden, kein Loeschen, kein Archivieren.
 */
@Singleton
public class ImapMailboxAdapter implements MailboxPort {

    private static final Logger LOG = Logger.getLogger(ImapMailboxAdapter.class);

    @ConfigProperty(name = "gmail.user")
    String user;

    @ConfigProperty(name = "gmail.app-password")
    Optional<String> appPassword;

    @ConfigProperty(name = "gmail.imap-host", defaultValue = "imap.gmail.com")
    String host;

    @ConfigProperty(name = "gmail.imap-port", defaultValue = "993")
    int port;

    @ConfigProperty(name = "gmail.label-prefix", defaultValue = "AI/")
    String labelPrefix;

    @ConfigProperty(name = "gmail.max-body-chars", defaultValue = "6000")
    int maxBodyChars;

    private Session session;
    private IMAPStore store;

    // ---------------------------------------------------------------- Verbindung

    private synchronized IMAPStore store() throws MessagingException {
        if (store != null && store.isConnected()) {
            return store;
        }
        if (session == null) {
            Properties p = new Properties();
            p.put("mail.store.protocol", "imaps");
            p.put("mail.imaps.host", host);
            p.put("mail.imaps.port", String.valueOf(port));
            p.put("mail.imaps.ssl.enable", "true");
            p.put("mail.imaps.connectiontimeout", "15000");
            p.put("mail.imaps.timeout", "60000");
            p.put("mail.mime.charset", "UTF-8");
            session = Session.getInstance(p);
        }
        store = (IMAPStore) session.getStore("imaps");
        store.connect(host, user, appPassword
                .filter(pw -> !pw.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "GMAIL_APP_PASSWORD ist nicht gesetzt (als k8s-Secret bereitstellen)")));
        return store;
    }

    Session mailSession() throws MessagingException {
        store();
        return session;
    }

    String user() {
        return user;
    }

    String appPassword() {
        return appPassword.orElseThrow();
    }

    @PreDestroy
    void close() {
        try {
            if (store != null && store.isConnected()) {
                store.close();
            }
        } catch (MessagingException ignored) {
            // beim Herunterfahren egal
        }
    }

    private interface InboxCall<T> {
        T apply(IMAPFolder inbox) throws MessagingException, IOException;
    }

    private synchronized <T> T withInbox(boolean writable, InboxCall<T> call) {
        IMAPFolder inbox = null;
        try {
            inbox = (IMAPFolder) store().getFolder("INBOX");
            inbox.open(writable ? Folder.READ_WRITE : Folder.READ_ONLY);
            return call.apply(inbox);
        } catch (MessagingException | IOException e) {
            // Verbindung verwerfen, naechster Aufruf verbindet neu
            try {
                if (store != null) {
                    store.close();
                }
            } catch (MessagingException ignored) {
                // egal
            }
            store = null;
            throw new MailboxException("IMAP-Zugriff fehlgeschlagen: " + e.getMessage(), e);
        } finally {
            try {
                if (inbox != null && inbox.isOpen()) {
                    inbox.close(false);
                }
            } catch (MessagingException ignored) {
                // egal
            }
        }
    }

    // ---------------------------------------------------------------- MailboxPort

    @Override
    public long uidValidity() {
        return withInbox(false, IMAPFolder::getUIDValidity);
    }

    @Override
    public long highestUid() {
        return withInbox(false, inbox -> {
            int total = inbox.getMessageCount();
            return total == 0 ? 0L : inbox.getUID(inbox.getMessage(total));
        });
    }

    @Override
    public List<EmailMessage> fetchNewerThan(long lastUid, int maxCount) {
        return withInbox(false, inbox -> {
            Message[] messages = inbox.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);
            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add(UIDFolder.FetchProfileItem.UID);
            inbox.fetch(messages, fp);

            List<EmailMessage> result = new ArrayList<>();
            for (Message m : messages) {
                long uid = inbox.getUID(m);
                if (uid <= lastUid) {
                    // getMessagesByUID liefert bei leerem Bereich die letzte Mail - filtern
                    continue;
                }
                result.add(toEmail(inbox, m, uid));
                if (result.size() >= maxCount) {
                    break;
                }
            }
            return result;
        });
    }

    @Override
    public Optional<EmailMessage> fetchByUid(long uid) {
        return withInbox(false, inbox -> {
            Message m = inbox.getMessageByUID(uid);
            return m == null ? Optional.empty() : Optional.of(toEmail(inbox, m, uid));
        });
    }

    @Override
    public void applyLabels(long uid, List<String> labels) {
        for (String label : labels) {
            String full = labelPrefix + sanitize(label);
            withInbox(true, inbox -> {
                Message m = inbox.getMessageByUID(uid);
                if (m == null) {
                    LOG.warnf("uid=%d nicht mehr in der INBOX, Label %s uebersprungen", uid, full);
                    return null;
                }
                Folder target = store().getFolder(full);
                if (!target.exists() && !target.create(Folder.HOLDS_MESSAGES)) {
                    throw new MessagingException("Label " + full + " konnte nicht angelegt werden");
                }
                inbox.copyMessages(new Message[] { m }, target);
                return null;
            });
        }
    }

    // ---------------------------------------------------------------- Hilfen

    private EmailMessage toEmail(IMAPFolder inbox, Message m, long uid)
            throws MessagingException, IOException {
        String from = addr(m.getFrom());
        String messageId = firstHeader(m, "Message-ID");
        boolean listUnsubscribe = firstHeader(m, "List-Unsubscribe") != null;
        boolean fromSelf = from != null
                && from.toLowerCase(Locale.ROOT).contains(user.toLowerCase(Locale.ROOT));
        Instant received = m.getReceivedDate() == null ? Instant.now() : m.getReceivedDate().toInstant();
        String body = truncate(extractText(m), maxBodyChars);
        return new EmailMessage(uid, messageId, from, m.getSubject(), received,
                body, listUnsubscribe, fromSelf);
    }

    private String sanitize(String label) {
        String clean = label == null ? "" : label.trim();
        if (clean.startsWith(labelPrefix)) {
            clean = clean.substring(labelPrefix.length());
        }
        if (!clean.matches("[\\p{L}\\p{N} _-]{1,40}")) {
            throw new IllegalArgumentException("Unzulaessiger Labelname: " + label);
        }
        return clean;
    }

    private static String firstHeader(Message m, String name) throws MessagingException {
        String[] v = m.getHeader(name);
        return v == null || v.length == 0 ? null : v[0];
    }

    private static String addr(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Address a : addresses) {
            parts.add(a.toString());
        }
        return String.join(", ", parts);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Rekursiv den lesbaren Text extrahieren; text/plain bevorzugt, sonst HTML -> Text. */
    private static String extractText(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            return String.valueOf(part.getContent());
        }
        if (part.isMimeType("text/html")) {
            return Jsoup.parse(String.valueOf(part.getContent())).wholeText();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            String html = null;
            StringBuilder rest = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                MimeBodyPart bp = (MimeBodyPart) mp.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(bp.getDisposition())) {
                    continue;
                }
                if (bp.isMimeType("text/plain")) {
                    return String.valueOf(bp.getContent());
                }
                if (bp.isMimeType("text/html")) {
                    html = String.valueOf(bp.getContent());
                } else {
                    rest.append(extractText(bp));
                }
            }
            if (html != null) {
                return Jsoup.parse(html).wholeText();
            }
            return rest.toString();
        }
        return "";
    }

    public static class MailboxException extends RuntimeException {
        public MailboxException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
