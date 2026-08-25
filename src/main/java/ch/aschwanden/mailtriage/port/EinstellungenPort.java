package ch.aschwanden.mailtriage.port;

import java.util.Optional;

import ch.aschwanden.mailtriage.domain.Einstellungen;

/** Driven Port fuer die zur Laufzeit aenderbaren Betriebseinstellungen. */
public interface EinstellungenPort {

    /** Leer, solange nie etwas gespeichert wurde - dann gelten die Vorgaben aus der Umgebung. */
    Optional<Einstellungen> laden();

    void speichern(Einstellungen einstellungen);
}
