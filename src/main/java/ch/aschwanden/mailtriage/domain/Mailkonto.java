package ch.aschwanden.mailtriage.domain;

/**
 * Ein ueberwachtes Postfach. Die Zugangsdaten gehoeren fachlich zum Konto, damit
 * die Anwendung mehrere Postfaecher bedienen kann, ohne dass fuer jedes eine
 * Umgebungsvariable und ein Deployment noetig waere.
 *
 * <p>{@code appPasswort} ist ein Geheimnis: {@link #toString()} maskiert es bewusst.
 * Records erzeugen sonst ein toString ueber alle Komponenten - ein einziges
 * {@code LOG.info(konto)} wuerde das Passwort in die Logs schreiben.
 */
public record Mailkonto(
        Long id,
        String name,
        String email,
        String appPasswort,
        String imapHost,
        int imapPort,
        String smtpHost,
        int smtpPort,
        String labelPrefix,
        boolean aktiv) {

    public Mailkonto {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("E-Mail-Adresse fehlt");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("Keine gueltige E-Mail-Adresse: " + email);
        }
        if (appPasswort == null || appPasswort.isBlank()) {
            throw new IllegalArgumentException("App-Passwort fehlt");
        }
        if (imapHost == null || imapHost.isBlank()) {
            throw new IllegalArgumentException("IMAP-Host fehlt");
        }
        if (smtpHost == null || smtpHost.isBlank()) {
            throw new IllegalArgumentException("SMTP-Host fehlt");
        }
        if (imapPort < 1 || imapPort > 65535 || smtpPort < 1 || smtpPort > 65535) {
            throw new IllegalArgumentException("Port ausserhalb 1-65535");
        }
        // Erst normalisieren, dann den Namen davon ableiten: sonst traegt ein
        // zurueckgefallener Name eine andere Schreibweise als das Konto selbst.
        email = email.trim().toLowerCase();
        name = (name == null || name.isBlank()) ? email : name.trim();
        // Der Praefix trennt den AI-Namensraum vom Rest der Mailbox. Ohne Schraegstrich
        // waeren die Labels flach und liessen sich in Gmail nicht einklappen.
        labelPrefix = (labelPrefix == null || labelPrefix.isBlank()) ? "AI/" : labelPrefix.trim();
        if (!labelPrefix.endsWith("/")) {
            labelPrefix = labelPrefix + "/";
        }
    }

    /** Neues, noch nicht gespeichertes Konto mit den Gmail-Vorgaben. */
    public static Mailkonto neuFuerGmail(String name, String email, String appPasswort) {
        return new Mailkonto(null, name, email, appPasswort,
                "imap.gmail.com", 993, "smtp.gmail.com", 465, "AI/", true);
    }

    public Mailkonto mitId(Long neueId) {
        return new Mailkonto(neueId, name, email, appPasswort, imapHost, imapPort,
                smtpHost, smtpPort, labelPrefix, aktiv);
    }

    public Mailkonto mitPasswort(String neuesPasswort) {
        return new Mailkonto(id, name, email, neuesPasswort, imapHost, imapPort,
                smtpHost, smtpPort, labelPrefix, aktiv);
    }

    public Mailkonto mitAktiv(boolean neuAktiv) {
        return new Mailkonto(id, name, email, appPasswort, imapHost, imapPort,
                smtpHost, smtpPort, labelPrefix, neuAktiv);
    }

    @Override
    public String toString() {
        return "Mailkonto[id=" + id + ", email=" + email + ", aktiv=" + aktiv + ", appPasswort=***]";
    }
}
