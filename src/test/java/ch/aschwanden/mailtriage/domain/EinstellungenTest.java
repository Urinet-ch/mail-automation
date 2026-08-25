package ch.aschwanden.mailtriage.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EinstellungenTest {

    private static Einstellungen mitUrl(String url) {
        return new Einstellungen(url, "gemma", 0.0, 0.7, 50, List.of(), List.of());
    }

    @Test
    @DisplayName("Abschliessende Schraegstriche fallen weg - sonst entstuende /v1 doppelt getrennt")
    void urlOhneSchraegstrich() {
        assertEquals("http://host:1234", mitUrl("http://host:1234//").modellUrl());
    }

    @Test
    @DisplayName("Adresse ohne Schema wird mit verstaendlicher Meldung abgewiesen")
    void urlBrauchtSchema() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mitUrl("192.168.1.50:1234"));
        assertTrue(e.getMessage().contains("http://"), e.getMessage());
    }

    @Test
    @DisplayName("Kommaliste wird zerlegt, getrimmt und entdoppelt")
    void listeZerlegen() {
        assertEquals(List.of("news.aldi", "shop@"),
                Einstellungen.ausListe("  news.aldi , shop@ , news.aldi , "));
        assertEquals(List.of(), Einstellungen.ausListe(""));
        assertEquals(List.of(), Einstellungen.ausListe(null));
    }

    @Test
    @DisplayName("Listen ueberstehen die Rundreise durch die Anzeigeform")
    void rundreiseAnzeige() {
        Einstellungen e = new Einstellungen("http://h:1", "m", 0.0, 0.7, 50,
                List.of("a", "b"), List.of());
        assertEquals("a, b", e.werbungAlsListe());
        assertEquals(List.of("a", "b"), Einstellungen.ausListe(e.werbungAlsListe()));
    }

    @Test
    @DisplayName("Werte ausserhalb des sinnvollen Bereichs werden abgewiesen")
    void grenzen() {
        assertThrows(IllegalArgumentException.class,
                () -> new Einstellungen("http://h:1", "m", 0.0, 1.5, 50, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Einstellungen("http://h:1", "m", 3.0, 0.7, 50, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Einstellungen("http://h:1", "m", 0.0, 0.7, 0, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Einstellungen("http://h:1", "  ", 0.0, 0.7, 50, List.of(), List.of()));
    }
}
