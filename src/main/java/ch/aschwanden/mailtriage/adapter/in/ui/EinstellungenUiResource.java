package ch.aschwanden.mailtriage.adapter.in.ui;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.jboss.logging.Logger;

import ch.aschwanden.mailtriage.application.EinstellungenService;
import ch.aschwanden.mailtriage.domain.Einstellungen;
import ch.aschwanden.mailtriage.port.ClassifierPort;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Betriebseinstellungen in der Oberflaeche - vor allem die Adresse des Inferenz-Rechners.
 *
 * <p>Bewusst getrennt von den Deployment-Werten: Was hier gespeichert wird, gilt sofort
 * und ueberstimmt die Umgebungsvariablen. Das steht auch auf der Seite, damit niemand
 * anschliessend vergeblich am Deployment dreht.
 */
@Path("/ui/einstellungen")
public class EinstellungenUiResource {

    private static final Logger LOG = Logger.getLogger(EinstellungenUiResource.class);

    @Inject
    EinstellungenService service;

    @Inject
    ClassifierPort classifier;

    @CheckedTemplate(basePath = "KontoUi")
    static class Templates {
        static native TemplateInstance einstellungen(Einstellungen e, boolean erreichbar,
                String meldung, String fehler);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance seite(@QueryParam("meldung") String meldung,
            @QueryParam("fehler") String fehler) {
        Einstellungen e = service.aktuelle();
        return Templates.einstellungen(e, classifier.isAvailable(e.modellUrl()), meldung, fehler);
    }

    @POST
    @Produces(MediaType.TEXT_HTML)
    public Response speichern(@FormParam("modellUrl") String modellUrl,
            @FormParam("modellName") String modellName,
            @FormParam("temperatur") String temperatur,
            @FormParam("confidenceSchwelle") String confidenceSchwelle,
            @FormParam("batchMax") String batchMax,
            @FormParam("werbungAbsender") String werbungAbsender,
            @FormParam("benachrichtigungAbsender") String benachrichtigungAbsender) {
        Einstellungen neu;
        try {
            neu = new Einstellungen(modellUrl, modellName,
                    zahl(temperatur, "Temperatur"),
                    zahl(confidenceSchwelle, "Confidence-Schwelle"),
                    (int) zahl(batchMax, "Batch-Groesse"),
                    Einstellungen.ausListe(werbungAbsender),
                    Einstellungen.ausListe(benachrichtigungAbsender));
        } catch (IllegalArgumentException e) {
            return zurueck(null, e.getMessage());
        }

        service.speichere(neu);

        // Erreichbarkeit erst NACH dem Speichern pruefen und nur berichten, nicht
        // erzwingen: ein schlafender Mac ist Normalbetrieb (die Mails warten dann in
        // der Queue). Waere der Test eine Bedingung, liesse sich die Adresse eines
        // ausgeschalteten Rechners nie eintragen - genau dann, wenn man es braucht.
        if (classifier.isAvailable(neu.modellUrl())) {
            return zurueck("Gespeichert. Modell unter " + neu.modellUrl() + " erreichbar.", null);
        }
        return zurueck("Gespeichert - das Modell ist unter " + neu.modellUrl()
                + " aber gerade nicht erreichbar. Mails sammeln sich so lange in der "
                + "Warteschlange und werden nachgeholt.", null);
    }

    @POST
    @Path("/test")
    public Response test(@FormParam("modellUrl") String modellUrl) {
        String url = modellUrl == null ? "" : modellUrl.trim();
        if (url.isBlank()) {
            return zurueck(null, "Keine Adresse angegeben.");
        }
        boolean ok = classifier.isAvailable(url);
        LOG.infof("Verbindungstest gegen %s: %s", url, ok ? "erreichbar" : "nicht erreichbar");
        return ok
                ? zurueck(url + " ist erreichbar.", null)
                : zurueck(null, url + " antwortet nicht. Laeuft LM Studio, und ist der Server "
                        + "an die Netzwerkschnittstelle gebunden (nicht nur 127.0.0.1)?");
    }

    @POST
    @Path("/vorgaben")
    public Response vorgaben() {
        Einstellungen v = service.speichere(service.vorgaben());
        return zurueck("Auf die Werte aus dem Deployment zurueckgesetzt (Modell: "
                + v.modellUrl() + ").", null);
    }

    private static double zahl(String eingabe, String feld) {
        try {
            return Double.parseDouble(eingabe == null ? "" : eingabe.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(feld + " ist keine gueltige Zahl: " + eingabe);
        }
    }

    private static Response zurueck(String meldung, String fehler) {
        StringBuilder ziel = new StringBuilder("/ui/einstellungen");
        if (meldung != null) {
            ziel.append("?meldung=").append(URLEncoder.encode(meldung, StandardCharsets.UTF_8));
        } else if (fehler != null) {
            ziel.append("?fehler=").append(URLEncoder.encode(fehler, StandardCharsets.UTF_8));
        }
        return Response.seeOther(URI.create(ziel.toString())).build();
    }
}
