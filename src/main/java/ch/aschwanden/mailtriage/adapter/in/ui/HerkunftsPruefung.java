package ch.aschwanden.mailtriage.adapter.in.ui;

import java.net.URI;

import org.jboss.logging.Logger;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Schutz gegen Cross-Site Request Forgery fuer die schreibenden UI-Aufrufe.
 *
 * <p>Die UI haengt hinter Basic Auth im Ingress. Der Browser sendet diese Zugangsdaten
 * bei JEDER Anfrage an den Host automatisch mit - auch bei einem Formular, das eine
 * fremde Seite abschickt. Ein Cookie-Flag wie SameSite hilft hier nicht, weil Basic
 * Auth kein Cookie ist. Deshalb wird bei schreibenden Aufrufen geprueft, dass die
 * Anfrage von der Anwendung selbst ausgeht.
 *
 * <p>Bewusst Origin/Referer statt eines Tokens im Formular: gleiche Wirkung fuer diesen
 * Fall, aber ohne Sitzungszustand in einer Anwendung, die sonst zustandslos ist.
 */
@Provider
public class HerkunftsPruefung implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(HerkunftsPruefung.class);

    @Override
    public void filter(ContainerRequestContext ctx) {
        if (!"POST".equals(ctx.getMethod())) {
            return;
        }
        String pfad = ctx.getUriInfo().getPath();
        if (!pfad.startsWith("ui") && !pfad.startsWith("/ui")) {
            return;
        }

        String herkunft = ctx.getHeaderString("Origin");
        if (herkunft == null) {
            herkunft = ctx.getHeaderString("Referer");
        }
        if (herkunft == null) {
            // Kein Browser-Kontext (curl, Skript). Solche Aufrufe tragen die
            // Basic-Auth-Daten nicht unbeabsichtigt mit sich - also kein CSRF-Risiko.
            return;
        }
        String erwartet = ctx.getUriInfo().getBaseUri().getAuthority();
        String tatsaechlich = URI.create(herkunft).getAuthority();
        if (erwartet != null && erwartet.equals(tatsaechlich)) {
            return;
        }
        LOG.warnf("Schreibender UI-Aufruf von fremder Herkunft abgewiesen: %s (erwartet %s)",
                herkunft, erwartet);
        ctx.abortWith(Response.status(Response.Status.FORBIDDEN)
                .entity("Anfrage von fremder Herkunft abgewiesen.").type("text/plain").build());
    }
}
