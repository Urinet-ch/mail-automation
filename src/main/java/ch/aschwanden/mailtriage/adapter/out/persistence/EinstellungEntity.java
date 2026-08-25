package ch.aschwanden.mailtriage.adapter.out.persistence;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Eine Betriebseinstellung als Schluessel/Wert. Getippt wird in der Domaene. */
@Entity
@Table(name = "einstellung")
public class EinstellungEntity extends PanacheEntityBase {

    @Id
    @Column(name = "schluessel")
    public String schluessel;

    @Column(name = "wert")
    public String wert;

    @Column(name = "geaendert_am", nullable = false)
    public Instant geaendertAm;
}
