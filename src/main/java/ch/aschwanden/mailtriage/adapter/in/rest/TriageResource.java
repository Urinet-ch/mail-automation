package ch.aschwanden.mailtriage.adapter.in.rest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ch.aschwanden.mailtriage.application.DigestService;
import ch.aschwanden.mailtriage.application.EinstellungenService;
import ch.aschwanden.mailtriage.application.TriageService;
import ch.aschwanden.mailtriage.domain.Mailkonto;
import ch.aschwanden.mailtriage.port.ClassifierPort;
import ch.aschwanden.mailtriage.port.KontoStorePort;
import ch.aschwanden.mailtriage.port.TriageStorePort;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Manuelle Ausloesung und Statusblick. Nur im internen Netz erreichbar
 * (kein Ingress noetig - port-forward oder Cluster-DNS reicht).
 */
@Path("/triage")
@Produces(MediaType.APPLICATION_JSON)
public class TriageResource {

    @Inject
    TriageService triage;

    @Inject
    DigestService digest;

    @Inject
    TriageStorePort store;

    @Inject
    KontoStorePort konten;

    @Inject
    ClassifierPort classifier;

    @Inject
    EinstellungenService einstellungen;

    @POST
    @Path("/run")
    public Map<String, Object> run() {
        int verarbeitet = triage.run();
        int nachgeholt = triage.retryPending();
        return Map.of("verarbeitet", verarbeitet, "nachgeholt", nachgeholt);
    }

    @POST
    @Path("/digest")
    public Map<String, Object> digest() {
        digest.sendeDigest();
        return Map.of("status", "gesendet");
    }

    @GET
    @Path("/status")
    public Map<String, Object> status() {
        List<Map<String, Object>> proKonto = konten.alle().stream()
                .map(this::kontoStatus)
                .toList();
        return Map.of(
                "classifierErreichbar", classifier.isAvailable(einstellungen.aktuelle().modellUrl()),
                "pending", store.pendingCountGesamt(),
                "konten", proKonto);
    }

    private Map<String, Object> kontoStatus(Mailkonto konto) {
        TriageStorePort.KontoStatistik s = store.statistik(konto);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", konto.email());
        m.put("aktiv", konto.aktiv());
        m.put("verarbeitet", s.verarbeitet());
        m.put("pending", s.pending());
        // Nullable Werte: Map.of vertraegt kein null, deshalb LinkedHashMap.
        m.put("lastUid", s.lastUid().orElse(null));
        m.put("letzterLauf", s.letzterLauf().map(Object::toString).orElse(null));
        return m;
    }
}
