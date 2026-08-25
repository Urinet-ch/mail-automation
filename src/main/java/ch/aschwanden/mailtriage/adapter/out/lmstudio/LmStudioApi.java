package ch.aschwanden.mailtriage.adapter.out.lmstudio;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * OpenAI-kompatible API von LM Studio (lms server).
 *
 * <p>Bewusst ohne {@code @RegisterRestClient}: die Adresse des Inferenz-Rechners ist eine
 * Betriebseinstellung und zur Laufzeit aenderbar. Ein konfigurierter Client haette sie
 * beim Start festgeschrieben, ein Wechsel haette einen Neustart verlangt. Der Adapter
 * baut den Client stattdessen je Adresse ueber den RestClientBuilder.
 */
@Path("/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface LmStudioApi {

    @POST
    @Path("/chat/completions")
    JsonNode chatCompletions(Map<String, Object> request);

    @GET
    @Path("/models")
    JsonNode models();
}
