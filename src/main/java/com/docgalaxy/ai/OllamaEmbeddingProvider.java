package com.docgalaxy.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * EmbeddingProvider backed by a local Ollama instance (nomic-embed-text, 768 dims).
 * No API key required. Expects Ollama to be running at {@code http://localhost:11434}.
 * Throws a non-retryable {@link AIServiceException} if Ollama is not running.
 */
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    private static final String DEFAULT_ENDPOINT = "http://localhost:11434/api/embeddings";
    private static final String MODEL = "nomic-embed-text";
    private static final int DIMENSION = 768;

    private final HttpClient httpClient;
    private final Gson gson;
    /** Endpoint URL — overridable for tests. */
    final String endpoint;

    public OllamaEmbeddingProvider() {
        this(null, DEFAULT_ENDPOINT);
    }

    /**
     * Package-private constructor for testing: inject a pre-built HttpClient
     * and redirect to a local test endpoint.
     */
    OllamaEmbeddingProvider(HttpClient httpClient, String endpoint) {
        this.httpClient = (httpClient != null) ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        this.endpoint = endpoint;
    }

    @Override
    public double[] embed(String text) throws AIServiceException {
        if (text == null || text.isBlank()) {
            throw new AIServiceException("Input text must not be blank", 0, false);
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("prompt", text);
        JsonObject response = post(body);
        return extractEmbedding(response);
    }

    @Override
    public List<double[]> batchEmbed(List<String> texts) throws AIServiceException {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<double[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    @Override
    public int getDimension() {
        return DIMENSION;
    }

    @Override
    public String getModelName() {
        return MODEL;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private JsonObject post(JsonObject body) throws AIServiceException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status == 200) {
                return gson.fromJson(response.body(), JsonObject.class);
            }
            throw new AIServiceException(
                    "Ollama API error: HTTP " + status + " — " + response.body(),
                    status, false);

        } catch (AIServiceException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof ConnectException) {
                throw new AIServiceException("Ollama is not running", 0, false);
            }
            throw new AIServiceException("HTTP request failed: " + e.getMessage(), e);
        }
    }

    private double[] extractEmbedding(JsonObject response) throws AIServiceException {
        JsonArray arr = response.getAsJsonArray("embedding");
        if (arr == null || arr.isEmpty()) {
            throw new AIServiceException("Ollama response contained no embedding data", 200, false);
        }
        double[] result = new double[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            result[i] = arr.get(i).getAsDouble();
        }
        return result;
    }
}
