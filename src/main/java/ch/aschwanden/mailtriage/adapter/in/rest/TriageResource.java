package ch.aschwanden.mailtriage.adapter.in.rest;

import java.util.Map;

import ch.aschwanden.mailtriage.application.DigestService;
import ch.aschwanden.mailtriage.application.TriageService;
import ch.aschwanden.mailtriage.port.ClassifierPort;
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
    ClassifierPort classifier;

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
        return Map.of(
                "classifierErreichbar", classifier.isAvailable(),
                "pending", store.pendingCount(),
                "mailboxState", store.mailboxState()
                        .map(s -> Map.of("uidValidity", s.uidValidity(), "lastUid", s.lastUid()))
                        .orElse(Map.of()));
    }
}
