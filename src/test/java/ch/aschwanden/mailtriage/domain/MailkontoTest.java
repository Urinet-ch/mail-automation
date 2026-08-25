package ch.aschwanden.mailtriage.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailkontoTest {

    private static Mailkonto gueltig() {
        return Mailkonto.neuFuerGmail("Privat", "Name@Gmail.com", "abcdefghijklmnop");
    }

    @Test
    @DisplayName("Adresse wird normalisiert, damit dasselbe Postfach nicht zweimal entsteht")
    void adresseNormalisiert() {
        assertEquals("name@gmail.com", gueltig().email());
    }

    @Test
    @DisplayName("Fehlender Anzeigename faellt auf die Adresse zurueck")
    void nameFaelltZurueck() {
        assertEquals("name@gmail.com",
                Mailkonto.neuFuerGmail("   ", "Name@Gmail.com", "geheim").name());
    }

    @Test
    @DisplayName("Label-Praefix endet immer auf Schraegstrich - sonst waeren die Labels flach")
    void praefixBekommtSchraegstrich() {
        Mailkonto k = new Mailkonto(null, "x", "a@b.ch", "pw",
                "imap.gmail.com", 993, "smtp.gmail.com", 465, "KI", true);
        assertEquals("KI/", k.labelPrefix());
    }

    @Test
    @DisplayName("toString maskiert das Passwort - ein Log-Aufruf darf es nicht verraten")
    void toStringMaskiert() {
        String s = gueltig().toString();
        assertFalse(s.contains("abcdefghijklmnop"), "Passwort steht im toString: " + s);
        assertTrue(s.contains("***"));
    }

    @Test
    @DisplayName("Unvollstaendige Konten werden abgewiesen")
    void invarianten() {
        assertThrows(IllegalArgumentException.class,
                () -> Mailkonto.neuFuerGmail("x", "keine-adresse", "pw"));
        assertThrows(IllegalArgumentException.class,
                () -> Mailkonto.neuFuerGmail("x", "a@b.ch", "  "));
        assertThrows(IllegalArgumentException.class,
                () -> new Mailkonto(null, "x", "a@b.ch", "pw",
                        "imap.gmail.com", 0, "smtp.gmail.com", 465, "AI/", true));
    }
}
