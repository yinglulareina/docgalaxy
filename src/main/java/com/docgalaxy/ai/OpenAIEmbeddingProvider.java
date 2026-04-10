package com.docgalaxy.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * EmbeddingProvider backed by the OpenAI text-embedding-3-small model (1536 dims).
 * Reads the API key from the {@code OPENAI_API_KEY} environment variable.
 * Retries up to 3 times on HTTP 429 (rate-limit) or 5xx (server error).
 */
public class OpenAIEmbeddingProvider implements EmbeddingProvider {

    private static final String ENDPOINT = "https://api.openai.com/v1/embeddings";
    private static final String MODEL = "text-embedding-3-small";
    private static final int DIMENSION = 1536;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BASE_MS = 1000L;

    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiKey;
    /** Base delay between retries in ms — overridable for tests. */
    final long retryBaseMs;
    /** Endpoint URL — overridable for tests to point at a local server. */
    final String endpoint;

    public OpenAIEmbeddingProvider() {
        this(System.getenv("OPENAI_API_KEY"), null, RETRY_BASE_MS, ENDPOINT);
    }

    /** Package-private constructor for testing (allows injecting a custom key). */
    OpenAIEmbeddingProvider(String apiKey) {
        this(apiKey, null, RETRY_BASE_MS, ENDPOINT);
    }

    /**
     * Package-private constructor for testing: inject a pre-built HttpClient,
     * override retry delay, and redirect to a local test endpoint.
     */
    OpenAIEmbeddingProvider(String apiKey, HttpClient httpClient, long retryBaseMs, String endpoint) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
        }
        this.apiKey = apiKey;
        this.httpClient = (httpClient != null) ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        this.retryBaseMs = retryBaseMs;
        this.endpoint = endpoint;
    }

    @Override
    public double[] embed(String text) throws AIServiceException {
        if (text == null || text.isBlank()) {
            throw new AIServiceException("Input text must not be blank", 0, false);
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("input", text);
        JsonObject response = postWithRetry(body);
        return extractFirstEmbedding(response);
    }

    @Override
    public List<double[]> batchEmbed(List<String> texts) throws AIServiceException {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        JsonArray inputArray = new JsonArray();
        for (String t : texts) {
            inputArray.add(t);
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.add("input", inputArray);

        JsonObject response = postWithRetry(body);
        JsonArray dataArray = response.getAsJsonArray("data");
        List<double[]> results = new ArrayList<>(dataArray.size());
        for (int i = 0; i < dataArray.size(); i++) {
            results.add(toDoubleArray(
                    dataArray.get(i).getAsJsonObject().getAsJsonArray("embedding")));
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

    private JsonObject postWithRetry(JsonObject body) throws AIServiceException {
        AIServiceException lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                sleepBeforeRetry(attempt);
            }
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                        .timeout(Duration.ofSeconds(30))
                        .build();

                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                int status = response.statusCode();
                if (status == 200) {
                    return gson.fromJson(response.body(), JsonObject.class);
                }

                boolean retryable = (status == 429 || status >= 500);
                lastException = new AIServiceException(
                        "OpenAI API error: HTTP " + status + " — " + response.body(),
                        status, retryable);

                if (!retryable) {
                    throw lastException;
                }

            } catch (AIServiceException e) {
                throw e;
            } catch (Exception e) {
                lastException = new AIServiceException("HTTP request failed: " + e.getMessage(), e);
            }
        }
        throw lastException;
    }

    private double[] extractFirstEmbedding(JsonObject response) throws AIServiceException {
        JsonArray data = response.getAsJsonArray("data");
        if (data == null || data.isEmpty()) {
            throw new AIServiceException("OpenAI response contained no embedding data", 200, false);
        }
        return toDoubleArray(data.get(0).getAsJsonObject().getAsJsonArray("embedding"));
    }

    private double[] toDoubleArray(JsonArray arr) {
        double[] result = new double[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            result[i] = arr.get(i).getAsDouble();
        }
        return result;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(retryBaseMs * (1L << (attempt - 1))); // exponential back-off
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
