package ch.aschwanden.mailtriage.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerschluesselungTest {

    private static Verschluesselung mitSchluessel(String base64) {
        Verschluesselung v = new Verschluesselung();
        v.keyBase64 = Optional.ofNullable(base64);
        return v;
    }

    private static Verschluesselung standard() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }
        return mitSchluessel(Base64.getEncoder().encodeToString(key));
    }

    @Test
    @DisplayName("Verschluesseln und Entschluesseln ergibt den Klartext zurueck")
    void rundreise() {
        Verschluesselung v = standard();
        String klartext = "abcd efgh ijkl mnop";
        assertEquals(klartext, v.entschluessle(v.verschluessle(klartext)));
    }

    @Test
    @DisplayName("Derselbe Klartext ergibt zweimal ein anderes Chiffrat (zufaelliger Nonce)")
    void nonceIstZufaellig() {
        Verschluesselung v = standard();
        assertNotEquals(v.verschluessle("gleich"), v.verschluessle("gleich"));
    }

    @Test
    @DisplayName("Der Klartext taucht im Chiffrat nicht auf")
    void keinKlartextImChiffrat() {
        String chiffrat = standard().verschluessle("geheimespasswort");
        assertFalse(chiffrat.contains("geheimespasswort"));
        assertTrue(chiffrat.startsWith("aesgcm1:"));
    }

    @Test
    @DisplayName("Manipuliertes Chiffrat wird erkannt, statt Unsinn zu liefern")
    void manipulationFaelltAuf() {
        Verschluesselung v = standard();
        String chiffrat = v.verschluessle("passwort");
        String[] teile = chiffrat.split(":", 3);
        // letztes Zeichen des Chiffrats kippen
        char letzter = teile[2].charAt(teile[2].length() - 1);
        String manipuliert = teile[0] + ":" + teile[1] + ":"
                + teile[2].substring(0, teile[2].length() - 1) + (letzter == 'A' ? 'B' : 'A');
        assertThrows(IllegalStateException.class, () -> v.entschluessle(manipuliert));
    }

    @Test
    @DisplayName("Ein fremder Schluessel kann nicht entschluesseln")
    void fremderSchluessel() {
        String chiffrat = standard().verschluessle("passwort");
        byte[] anderer = new byte[32];
        java.util.Arrays.fill(anderer, (byte) 7);
        Verschluesselung fremd = mitSchluessel(Base64.getEncoder().encodeToString(anderer));
        assertThrows(IllegalStateException.class, () -> fremd.entschluessle(chiffrat));
    }

    @Test
    @DisplayName("Fehlender oder zu kurzer Schluessel meldet sich verstaendlich")
    void schluesselfehler() {
        assertTrue(assertThrows(IllegalStateException.class,
                () -> mitSchluessel(null).verschluessle("x")).getMessage().contains("TRIAGE_CRYPTO_KEY"));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> mitSchluessel(Base64.getEncoder().encodeToString(new byte[16])).verschluessle("x"))
                .getMessage().contains("32 Byte"));
    }
}
