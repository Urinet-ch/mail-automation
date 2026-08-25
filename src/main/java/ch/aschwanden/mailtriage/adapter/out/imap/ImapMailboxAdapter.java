package ch.aschwanden.mailtriage.adapter.out.imap;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jsoup.Jsoup;

import ch.aschwanden.mailtriage.domain.EmailMessage;
import ch.aschwanden.mailtriage.domain.Mailkonto;
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
 *
 * <p>Haelt je Konto eine Verbindung offen. Gmail begrenzt gleichzeitige IMAP-
 * Verbindungen je Konto, deshalb wird nicht pro Aufruf neu verbunden; die Sperre
 * liegt aber je Konto und nicht global, damit ein langsames Postfach die anderen
 * nicht blockiert.
 */
@Singleton
public class ImapMailboxAdapter implements MailboxPort {

    private static final Logger LOG = Logger.getLogger(ImapMailboxAdapter.class);

    @ConfigProperty(name = "gmail.max-body-chars", defaultValue = "6000")
    int maxBodyChars;

    /** Verbindung und Sperre je Konto, ueber die E-Mail-Adresse identifiziert. */
    private record Verbindung(Object sperre, AtomicReference<IMAPStore> store) {
        static Verbindung neu() {
            return new Verbindung(new Object(), new AtomicReference<>());
        }
    }

    private final Map<String, Verbindung> verbindungen = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------- Verbindung

    private static Session session(Mailkonto konto) {
        Properties p = new Properties();
        p.put("mail.store.protocol", "imaps");
        p.put("mail.imaps.host", konto.imapHost());
        p.put("mail.imaps.port", String.valueOf(konto.imapPort()));
        p.put("mail.imaps.ssl.enable", "true");
        p.put("mail.imaps.connectiontimeout", "15000");
        p.put("mail.imaps.timeout", "60000");
        p.put("mail.mime.charset", "UTF-8");
        // getInstance statt getDefaultInstance: die Default-Session ist JVM-weit und
        // wuerde die Properties des zuerst angelegten Kontos fuer alle festschreiben.
        return Session.getInstance(p);
    }

    private IMAPStore store(Mailkonto konto) throws MessagingException {
        Verbindung v = verbindungen.computeIfAbsent(konto.email(), e -> Verbindung.neu());
        IMAPStore vorhanden = v.store().get();
        if (vorhanden != null && vorhanden.isConnected()) {
            return vorhanden;
        }
        IMAPStore neu = (IMAPStore) session(konto).getStore("imaps");
        neu.connect(konto.imapHost(), konto.email(), konto.appPasswort());
        v.store().set(neu);
        return neu;
    }

    private void verwerfe(Mailkonto konto) {
        Verbindung v = verbindungen.get(konto.email());
        if (v == null) {
            return;
        }
        IMAPStore alt = v.store().getAndSet(null);
        if (alt == null) {
            return;
        }
        try {
            alt.close();
        } catch (MessagingException ignored) {
            // Die Verbindung ist ohnehin verloren - der naechste Aufruf baut neu auf.
        }
    }

    @Override
    public void schliesse(Mailkonto konto) {
        verwerfe(konto);
        verbindungen.remove(konto.email());
    }

    @Override
    public void pruefeVerbindung(Mailkonto konto) {
        // Bewusst ohne Zwischenspeichern: der Test soll die eingegebenen Daten pruefen,
        // nicht eine zufaellig noch offene Verbindung des alten Passworts bestaetigen.
        verwerfe(konto);
        withInbox(konto, false, inbox -> inbox.getMessageCount());
    }

    @PreDestroy
    void close() {
        verbindungen.values().forEach(v -> {
            IMAPStore s = v.store().getAndSet(null);
            if (s != null) {
                try {
                    s.close();
                } catch (MessagingException ignored) {
                    // beim Herunterfahren egal
                }
            }
        });
        verbindungen.clear();
    }

    private interface InboxCall<T> {
        T apply(IMAPFolder inbox) throws MessagingException, IOException;
    }

    private <T> T withInbox(Mailkonto konto, boolean writable, InboxCall<T> call) {
        Verbindung v = verbindungen.computeIfAbsent(konto.email(), e -> Verbindung.neu());
        synchronized (v.sperre()) {
            IMAPFolder inbox = null;
            try {
                inbox = (IMAPFolder) store(konto).getFolder("INBOX");
                inbox.open(writable ? Folder.READ_WRITE : Folder.READ_ONLY);
                return call.apply(inbox);
            } catch (MessagingException | IOException e) {
                verwerfe(konto);
                throw new MailboxException(
                        "IMAP-Zugriff fuer " + konto.email() + " fehlgeschlagen: " + e.getMessage(), e);
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
    }

    // ---------------------------------------------------------------- MailboxPort

    @Override
    public long uidValidity(Mailkonto konto) {
        return withInbox(konto, false, IMAPFolder::getUIDValidity);
    }

    @Override
    public long highestUid(Mailkonto konto) {
        return withInbox(konto, false, inbox -> {
            int total = inbox.getMessageCount();
            return total == 0 ? 0L : inbox.getUID(inbox.getMessage(total));
        });
    }

    @Override
    public List<EmailMessage> fetchNewerThan(Mailkonto konto, long lastUid, int maxCount) {
        return withInbox(konto, false, inbox -> {
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
                result.add(toEmail(konto, m, uid));
                if (result.size() >= maxCount) {
                    break;
                }
            }
            return result;
        });
    }

    @Override
    public Optional<EmailMessage> fetchByUid(Mailkonto konto, long uid) {
        return withInbox(konto, false, inbox -> {
            Message m = inbox.getMessageByUID(uid);
            return m == null ? Optional.empty() : Optional.of(toEmail(konto, m, uid));
        });
    }

    @Override
    public void applyLabels(Mailkonto konto, long uid, List<String> labels) {
        for (String label : labels) {
            String full = konto.labelPrefix() + sanitize(konto, label);
            withInbox(konto, true, inbox -> {
                Message m = inbox.getMessageByUID(uid);
                if (m == null) {
                    LOG.warnf("uid=%d nicht mehr in der INBOX, Label %s uebersprungen", uid, full);
                    return null;
                }
                Folder target = store(konto).getFolder(full);
                if (!target.exists() && !target.create(Folder.HOLDS_MESSAGES)) {
                    throw new MessagingException("Label " + full + " konnte nicht angelegt werden");
                }
                inbox.copyMessages(new Message[] { m }, target);
                return null;
            });
        }
    }

    // ---------------------------------------------------------------- Hilfen

    private EmailMessage toEmail(Mailkonto konto, Message m, long uid)
            throws MessagingException, IOException {
        String from = addr(m.getFrom());
        String messageId = firstHeader(m, "Message-ID");
        boolean listUnsubscribe = firstHeader(m, "List-Unsubscribe") != null;
        boolean fromSelf = from != null
                && from.toLowerCase(Locale.ROOT).contains(konto.email().toLowerCase(Locale.ROOT));
        Instant received = m.getReceivedDate() == null ? Instant.now() : m.getReceivedDate().toInstant();
        String body = truncate(extractText(m), maxBodyChars);
        return new EmailMessage(uid, messageId, from, m.getSubject(), received,
                body, listUnsubscribe, fromSelf);
    }

    private static String sanitize(Mailkonto konto, String label) {
        String clean = label == null ? "" : label.trim();
        if (clean.startsWith(konto.labelPrefix())) {
            clean = clean.substring(konto.labelPrefix().length());
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
