package ch.aschwanden.mailtriage.adapter.out.persistence;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ch.aschwanden.mailtriage.domain.Einstellungen;
import ch.aschwanden.mailtriage.port.EinstellungenPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/** Postgres-Umsetzung der Betriebseinstellungen. */
@ApplicationScoped
public class PanacheEinstellungenStore implements EinstellungenPort {

    private static final String MODELL_URL = "modell.url";
    private static final String MODELL_NAME = "modell.name";
    private static final String TEMPERATUR = "modell.temperatur";
    private static final String SCHWELLE = "triage.confidence-schwelle";
    private static final String BATCH_MAX = "triage.batch-max";
    private static final String WERBUNG = "vorfilter.werbung-absender";
    private static final String BENACHRICHTIGUNG = "vorfilter.benachrichtigung-absender";

    @Override
    public Optional<Einstellungen> laden() {
        Map<String, String> werte = new HashMap<>();
        for (EinstellungEntity e : EinstellungEntity.<EinstellungEntity>listAll()) {
            werte.put(e.schluessel, e.wert);
        }
        // Die Modell-URL ist der Anker: fehlt sie, wurde nie etwas gespeichert und es
        // gelten die Vorgaben aus der Umgebung. Ein teilweise gefuellter Satz kann
        // nicht entstehen, weil immer alle Werte zusammen geschrieben werden.
        if (!werte.containsKey(MODELL_URL)) {
            return Optional.empty();
        }
        return Optional.of(new Einstellungen(
                werte.get(MODELL_URL),
                werte.get(MODELL_NAME),
                Double.parseDouble(werte.getOrDefault(TEMPERATUR, "0.0")),
                Double.parseDouble(werte.getOrDefault(SCHWELLE, "0.7")),
                Integer.parseInt(werte.getOrDefault(BATCH_MAX, "50")),
                Einstellungen.ausListe(werte.get(WERBUNG)),
                Einstellungen.ausListe(werte.get(BENACHRICHTIGUNG))));
    }

    @Override
    @Transactional
    public void speichern(Einstellungen e) {
        setze(MODELL_URL, e.modellUrl());
        setze(MODELL_NAME, e.modellName());
        setze(TEMPERATUR, String.valueOf(e.temperatur()));
        setze(SCHWELLE, String.valueOf(e.confidenceSchwelle()));
        setze(BATCH_MAX, String.valueOf(e.batchMax()));
        setze(WERBUNG, String.join(",", e.werbungAbsender()));
        setze(BENACHRICHTIGUNG, String.join(",", e.benachrichtigungAbsender()));
    }

    private void setze(String schluessel, String wert) {
        EinstellungEntity vorhanden = EinstellungEntity.findById(schluessel);
        if (vorhanden == null) {
            vorhanden = new EinstellungEntity();
            vorhanden.schluessel = schluessel;
            vorhanden.wert = wert;
            vorhanden.geaendertAm = Instant.now();
            vorhanden.persist();
            return;
        }
        vorhanden.wert = wert;
        vorhanden.geaendertAm = Instant.now();
    }

    /** Nur fuer Tests und Diagnose: alle gespeicherten Schluessel. */
    public List<String> schluessel() {
        return EinstellungEntity.<EinstellungEntity>listAll().stream().map(e -> e.schluessel).toList();
    }
}
