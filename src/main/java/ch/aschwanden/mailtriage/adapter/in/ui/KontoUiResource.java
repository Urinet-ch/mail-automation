package ch.aschwanden.mailtriage.adapter.in.ui;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import ch.aschwanden.mailtriage.application.EinstellungenService;
import ch.aschwanden.mailtriage.application.KontoService;
import ch.aschwanden.mailtriage.application.KontoService.KontoFehler;
import ch.aschwanden.mailtriage.application.TriageService;
import ch.aschwanden.mailtriage.domain.Mailkonto;
import ch.aschwanden.mailtriage.port.ClassifierPort;
import ch.aschwanden.mailtriage.port.TriageStorePort.KontoStatistik;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Serverseitige Oberflaeche zur Kontoverwaltung.
 *
 * <p>Erreichbar nur ueber den LAN-Ingress hinter Basic Auth bzw. per port-forward.
 * Schreibende Aufrufe pruefen zusaetzlich die Herkunft ({@link HerkunftsPruefung}).
 */
@Path("/ui")
public class KontoUiResource {

    private static final Logger LOG = Logger.getLogger(KontoUiResource.class);

    @Inject
    KontoService konten;

    @Inject
    TriageService triage;

    @Inject
    ClassifierPort classifier;

    @Inject
    EinstellungenService einstellungen;

    @CheckedTemplate(basePath = "KontoUi")
    static class Templates {
        static native TemplateInstance liste(List<KontoZeile> konten, boolean classifierErreichbar,
                String modellUrl, String meldung, String fehler);

        static native TemplateInstance formular(Mailkonto konto, String fehler);
    }

    /** Ein Konto samt Kennzahlen, aufbereitet fuer die Anzeige. */
    public record KontoZeile(long id, String name, String email, String labelPrefix, boolean aktiv,
            long verarbeitet, long pending, String letzterLauf, String lastUid) { }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance liste(@QueryParam("meldung") String meldung,
            @QueryParam("fehler") String fehler) {
        List<KontoZeile> zeilen = konten.alle().stream().map(k -> {
            KontoStatistik s = konten.statistik(k);
            return new KontoZeile(k.id(), k.name(), k.email(), k.labelPrefix(), k.aktiv(),
                    s.verarbeitet(), s.pending(),
                    s.letzterLauf().map(Instant::toString).orElse("noch nie"),
                    s.lastUid().map(String::valueOf).orElse("-"));
        }).toList();
        return Templates.liste(zeilen, classifier.isAvailable(einstellungen.aktuelle().modellUrl()),
                einstellungen.aktuelle().modellUrl(), meldung, fehler);
    }

    @GET
    @Path("/neu")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance neu() {
        return Templates.formular(null, null);
    }

    @GET
    @Path("/{id}/bearbeiten")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance bearbeiten(@PathParam("id") long id) {
        return Templates.formular(konten.finde(id).orElse(null), null);
    }

    @POST
    @Produces(MediaType.TEXT_HTML)
    public Response anlegen(@FormParam("name") String name,
            @FormParam("email") String email,
            @FormParam("appPasswort") String appPasswort,
            @FormParam("labelPrefix") String labelPrefix) {
        try {
            Mailkonto neu = Mailkonto.neuFuerGmail(name, sauber(email), sauber(appPasswort));
            if (labelPrefix != null && !labelPrefix.isBlank()) {
                neu = new Mailkonto(null, neu.name(), neu.email(), neu.appPasswort(),
                        neu.imapHost(), neu.imapPort(), neu.smtpHost(), neu.smtpPort(),
                        labelPrefix, true);
            }
            Mailkonto gespeichert = konten.anlegen(neu);
            return weiter("Konto " + gespeichert.email() + " angelegt und Anmeldung geprueft.", null);
        } catch (KontoFehler | IllegalArgumentException e) {
            return Response.ok(Templates.formular(null, e.getMessage())).build();
        }
    }

    @POST
    @Path("/{id}")
    @Produces(MediaType.TEXT_HTML)
    public Response aendern(@PathParam("id") long id,
            @FormParam("name") String name,
            @FormParam("labelPrefix") String labelPrefix,
            @FormParam("appPasswort") String appPasswort) {
        try {
            konten.aendern(id, name, labelPrefix, sauber(appPasswort));
            return weiter("Konto gespeichert.", null);
        } catch (KontoFehler | IllegalArgumentException e) {
            return Response.ok(Templates.formular(konten.finde(id).orElse(null), e.getMessage())).build();
        }
    }

    @POST
    @Path("/{id}/aktiv")
    public Response aktivSetzen(@PathParam("id") long id, @FormParam("aktiv") boolean aktiv) {
        try {
            Mailkonto k = konten.setzeAktiv(id, aktiv);
            return weiter(k.email() + (aktiv ? " wird wieder triagiert." : " ist pausiert."), null);
        } catch (KontoFehler e) {
            return weiter(null, e.getMessage());
        }
    }

    @POST
    @Path("/{id}/loeschen")
    public Response loeschen(@PathParam("id") long id) {
        Optional<Mailkonto> konto = konten.finde(id);
        konten.loeschen(id);
        return weiter(konto.map(k -> "Konto " + k.email() + " und sein Verlauf wurden geloescht. "
                + "Bereits gesetzte Labels bleiben in der Mailbox.").orElse("Konto geloescht."), null);
    }

    @POST
    @Path("/{id}/lauf")
    public Response lauf(@PathParam("id") long id) {
        Optional<Mailkonto> konto = konten.finde(id);
        if (konto.isEmpty()) {
            return weiter(null, "Konto existiert nicht.");
        }
        try {
            int n = triage.run(konto.get());
            return weiter(n + " Mail(s) verarbeitet fuer " + konto.get().email() + ".", null);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Manueller Lauf fuer Konto %d fehlgeschlagen", id);
            return weiter(null, "Lauf fehlgeschlagen: " + e.getMessage());
        }
    }

    private static String sauber(String s) {
        // Gmail zeigt App-Passwoerter in Vierergruppen an; die Leerzeichen gehoeren
        // nicht zum Passwort und sind der haeufigste Grund fuer "Anmeldung fehlgeschlagen".
        return s == null ? null : s.replace(" ", "").trim();
    }

    /**
     * Nach jedem schreibenden Aufruf umleiten (Post/Redirect/Get), damit ein
     * Neuladen der Seite die Aktion nicht wiederholt.
     */
    private static Response weiter(String meldung, String fehler) {
        StringBuilder ziel = new StringBuilder("/ui");
        if (meldung != null) {
            ziel.append("?meldung=").append(java.net.URLEncoder.encode(meldung,
                    java.nio.charset.StandardCharsets.UTF_8));
        } else if (fehler != null) {
            ziel.append("?fehler=").append(java.net.URLEncoder.encode(fehler,
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        return Response.seeOther(URI.create(ziel.toString())).build();
    }
}
