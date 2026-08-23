package ch.aschwanden.mailtriage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ch.aschwanden.mailtriage.domain.Aktion;
import ch.aschwanden.mailtriage.domain.EmailMessage;
import ch.aschwanden.mailtriage.domain.Kategorie;
import ch.aschwanden.mailtriage.domain.PreFilter;
import ch.aschwanden.mailtriage.domain.TriageResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reiner Unit-Test ohne Quarkus - die Vorfilter-Regeln sind pures Java. */
class PreFilterTest {

    private final PreFilter filter = new PreFilter(
            List.of("news.aldi", "shop@"),
            List.of("noreply-accounts@google.com"));

    private EmailMessage mail(String from, boolean listUnsubscribe, boolean self) {
        return new EmailMessage(1, "<x@y>", from, "Betreff", Instant.now(), "Text", listUnsubscribe, self);
    }

    @Test
    void selbstMailIstImmerErledigen() {
        Optional<TriageResult> r = filter.pruefe(mail("Ich <me@example.com>", false, true));
        assertTrue(r.isPresent());
        assertEquals(Aktion.ERLEDIGEN, r.get().aktion());
        assertEquals(Kategorie.PERSOENLICH, r.get().kategorie());
    }

    @Test
    void selbstMailSchlaegtNewsletterHeader() {
        // Auch mit List-Unsubscribe-Header gewinnt die Selbst-Mail-Regel
        Optional<TriageResult> r = filter.pruefe(mail("me@example.com", true, true));
        assertTrue(r.isPresent());
        assertEquals(Aktion.ERLEDIGEN, r.get().aktion());
    }

    @Test
    void bekannterWerbeAbsenderWirdOhneModellEntschieden() {
        Optional<TriageResult> r = filter.pruefe(mail("ALDI <no-reply@news.aldi-suisse.ch>", true, false));
        assertTrue(r.isPresent());
        assertEquals(Kategorie.WERBUNG, r.get().kategorie());
        assertEquals(Aktion.KEINE, r.get().aktion());
    }

    @Test
    void listUnsubscribeWirdNewsletter() {
        Optional<TriageResult> r = filter.pruefe(mail("anne@appcamps.de", true, false));
        assertTrue(r.isPresent());
        assertEquals(Kategorie.NEWSLETTER, r.get().kategorie());
    }

    @Test
    void unbekannterAbsenderGehtAnsModell() {
        assertTrue(filter.pruefe(mail("hans@example.org", false, false)).isEmpty());
    }

    @Test
    void vorfilterErgebnisIstImmerVollConfident() {
        Optional<TriageResult> r = filter.pruefe(mail("me@x.ch", false, true));
        assertEquals(1.0, r.orElseThrow().confidence());
        assertEquals(TriageResult.Quelle.VORFILTER, r.orElseThrow().quelle());
    }
}
