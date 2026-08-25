package ch.aschwanden.mailtriage.adapter.out.persistence;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * AES-256-GCM fuer die App-Passwoerter der Konten.
 *
 * <p>Der Schluessel kommt aus der Umgebung (k8s-Secret, SOPS-verschluesselt im
 * Infra-Repo) und liegt nie in der Datenbank. Wer nur einen Datenbank-Dump hat -
 * etwa aus dem OneDrive-Backup - kann die Passwoerter damit nicht lesen.
 *
 * <p>GCM statt CBC, weil es die Chiffrate zugleich authentifiziert: ein
 * manipulierter Datensatz faellt beim Entschluesseln auf, statt stillschweigend
 * Unsinn zu liefern. Der Nonce ist je Datensatz zufaellig und wird mitgespeichert -
 * er ist kein Geheimnis, darf sich bei GCM aber nie wiederholen.
 */
@ApplicationScoped
public class Verschluesselung {

    private static final String FORMAT = "aesgcm1";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();

    @ConfigProperty(name = "triage.crypto.key")
    Optional<String> keyBase64;

    /**
     * Liefert den Schluessel oder scheitert mit einer Anweisung statt einer
     * NullPointerException - ohne Schluessel ist kein Konto benutzbar.
     */
    private SecretKeySpec key() {
        String b64 = keyBase64.filter(s -> !s.isBlank()).orElseThrow(() -> new IllegalStateException(
                "TRIAGE_CRYPTO_KEY ist nicht gesetzt - ohne ihn koennen Kontopasswoerter weder "
                        + "gespeichert noch gelesen werden. Neuen Schluessel erzeugen mit: "
                        + "openssl rand -base64 32"));
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(b64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TRIAGE_CRYPTO_KEY ist kein gueltiges Base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException(
                    "TRIAGE_CRYPTO_KEY muss 32 Byte lang sein (AES-256), ist aber " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }

    /** Format: {@code aesgcm1:<nonce base64>:<chiffrat base64>}. */
    public String verschluessle(String klartext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ct = cipher.doFinal(klartext.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder enc = Base64.getEncoder();
            return FORMAT + ":" + enc.encodeToString(nonce) + ":" + enc.encodeToString(ct);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Verschluesseln fehlgeschlagen", e);
        }
    }

    public String entschluessle(String gespeichert) {
        String[] teile = gespeichert.split(":", 3);
        if (teile.length != 3 || !FORMAT.equals(teile[0])) {
            throw new IllegalStateException("Unbekanntes Chiffrat-Format in der Datenbank");
        }
        try {
            Base64.Decoder dec = Base64.getDecoder();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, dec.decode(teile[1])));
            return new String(cipher.doFinal(dec.decode(teile[2])), StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // Haeufigster Fall: der Schluessel wurde gewechselt. Das ist keine Panne im
            // Code, sondern verlangt neu eingegebene Passwoerter - deshalb explizit sagen.
            throw new IllegalStateException("Entschluesseln fehlgeschlagen - passt TRIAGE_CRYPTO_KEY "
                    + "noch zu den gespeicherten Konten?", e);
        }
    }
}
