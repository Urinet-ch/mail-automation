package ch.aschwanden.mailtriage.application;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import ch.aschwanden.mailtriage.domain.Einstellungen;
import ch.aschwanden.mailtriage.port.EinstellungenPort;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Haelt die zur Laufzeit aenderbaren Einstellungen und macht sie ohne Neustart wirksam.
 *
 * <p>Die Werte aus der Umgebung sind nur noch die <em>Vorgabe</em>: Solange nichts
 * gespeichert wurde, gelten sie unveraendert - ein bestehendes Deployment verhaelt sich
 * also wie zuvor. Sobald in der UI gespeichert wird, gewinnt die Datenbank. Die
 * Umgebungsvariable danach zu aendern hat keine Wirkung mehr; das steht so in der UI,
 * damit niemand vergeblich am Deployment dreht.
 *
 * <p>Zwischengehalten wird bewusst: die Einstellungen werden bei jeder Mail gelesen,
 * eine Datenbankabfrage je Mail waere Verschwendung. {@code volatile} genuegt, weil
 * immer der ganze Satz als unveraenderlicher Record ausgetauscht wird - ein Leser sieht
 * nie eine halb aktualisierte Fassung.
 */
@ApplicationScoped
public class EinstellungenService {

    private static final Logger LOG = Logger.getLogger(EinstellungenService.class);

    @Inject
    EinstellungenPort store;

    @ConfigProperty(name = "lmstudio.url", defaultValue = "http://localhost:1234")
    String vorgabeUrl;

    @ConfigProperty(name = "lmstudio.model")
    String vorgabeModell;

    @ConfigProperty(name = "lmstudio.temperature", defaultValue = "0.0")
    double vorgabeTemperatur;

    @ConfigProperty(name = "triage.confidence-schwelle", defaultValue = "0.7")
    double vorgabeSchwelle;

    @ConfigProperty(name = "triage.batch-max", defaultValue = "50")
    int vorgabeBatchMax;

    @ConfigProperty(name = "triage.vorfilter.werbung-absender")
    java.util.Optional<List<String>> vorgabeWerbung;

    @ConfigProperty(name = "triage.vorfilter.benachrichtigung-absender")
    java.util.Optional<List<String>> vorgabeBenachrichtigung;

    private volatile Einstellungen aktuell;

    @PostConstruct
    void laden() {
        aktuell = store.laden().orElseGet(this::ausUmgebung);
    }

    private Einstellungen ausUmgebung() {
        return new Einstellungen(vorgabeUrl, vorgabeModell, vorgabeTemperatur, vorgabeSchwelle,
                vorgabeBatchMax, vorgabeWerbung.orElse(List.of()),
                vorgabeBenachrichtigung.orElse(List.of()));
    }

    public Einstellungen aktuelle() {
        // Kann nur null sein, wenn @PostConstruct noch nicht lief - dann lieber die
        // Vorgabe als eine NullPointerException im Triage-Lauf.
        Einstellungen e = aktuell;
        return e == null ? ausUmgebung() : e;
    }

    /** Gilt sofort fuer den naechsten Triage-Lauf; kein Neustart noetig. */
    public Einstellungen speichere(Einstellungen neu) {
        store.speichern(neu);
        aktuell = neu;
        LOG.infof("Einstellungen geaendert: Modell %s auf %s, Schwelle %.2f, Batch %d",
                neu.modellName(), neu.modellUrl(), neu.confidenceSchwelle(), neu.batchMax());
        return neu;
    }

    /** Setzt auf die Werte aus der Umgebung zurueck, ohne sie zu speichern. */
    public Einstellungen vorgaben() {
        return ausUmgebung();
    }
}
