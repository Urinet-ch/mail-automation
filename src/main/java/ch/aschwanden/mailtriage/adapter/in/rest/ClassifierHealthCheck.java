package ch.aschwanden.mailtriage.adapter.in.rest;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import ch.aschwanden.mailtriage.port.ClassifierPort;
import ch.aschwanden.mailtriage.port.TriageStorePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Readiness meldet den Classifier-Zustand, ohne die App unready zu machen -
 * ein schlafender Mac ist Normalbetrieb (Mails bleiben pending), kein Ausfall.
 */
@Readiness
@ApplicationScoped
public class ClassifierHealthCheck implements HealthCheck {

    @Inject
    ClassifierPort classifier;

    @Inject
    TriageStorePort store;

    @Override
    public HealthCheckResponse call() {
        boolean available = classifier.isAvailable();
        return HealthCheckResponse.named("classifier")
                .up() // bewusst immer up - Details in den Daten
                .withData("lmStudioErreichbar", available)
                .withData("pendingMails", store.pendingCountGesamt())
                .build();
    }
}
