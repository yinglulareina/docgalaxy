package com.docgalaxy.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * ChatProvider backed by the OpenAI gpt-4o-mini model.
 * Reads the API key from the {@code OPENAI_API_KEY} environment variable.
 * Retries up to 3 times on HTTP 429 (rate-limit) or 5xx (server error).
 */
public class OpenAIChatProvider implements ChatProvider {

    private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BASE_MS = 1000L;

    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiKey;
    /** Base delay between retries in ms — overridable for tests. */
    final long retryBaseMs;
    /** Endpoint URL — overridable for tests to point at a local server. */
    final String endpoint;

    public OpenAIChatProvider() {
        this(System.getenv("OPENAI_API_KEY"), null, RETRY_BASE_MS, ENDPOINT);
    }

    /** Package-private constructor for testing (allows injecting a custom key). */
    OpenAIChatProvider(String apiKey) {
        this(apiKey, null, RETRY_BASE_MS, ENDPOINT);
    }

    /**
     * Package-private constructor for testing: inject a pre-built HttpClient,
     * override retry delay, and redirect to a local test endpoint.
     */
    OpenAIChatProvider(String apiKey, HttpClient httpClient, long retryBaseMs, String endpoint) {
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

    /**
     * Sends a single user message and returns the model's response.
     *
     * @param prompt the user message
     * @return ChatResponse.success with content, or ChatResponse.failure on error
     * @throws AIServiceException on unrecoverable API errors
     */
    @Override
    public ChatResponse chat(String prompt) throws AIServiceException {
        JsonObject body = buildBody(null, prompt);
        return postWithRetry(body);
    }

    /**
     * Sends a system prompt followed by a user message and returns the model's response.
     *
     * @param systemPrompt the system instruction
     * @param userPrompt   the user message
     * @return ChatResponse.success with content, or ChatResponse.failure on error
     * @throws AIServiceException on unrecoverable API errors
     */
    @Override
    public ChatResponse chatWithSystem(String systemPrompt, String userPrompt) throws AIServiceException {
        JsonObject body = buildBody(systemPrompt, userPrompt);
        return postWithRetry(body);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private JsonObject buildBody(String systemPrompt, String userPrompt) {
        JsonArray messages = new JsonArray();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", systemPrompt);
            messages.add(systemMsg);
        }
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.add("messages", messages);
        return body;
    }

    private ChatResponse postWithRetry(JsonObject body) throws AIServiceException {
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
                    return parseSuccess(response.body());
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

    private ChatResponse parseSuccess(String responseBody) throws AIServiceException {
        JsonObject root = gson.fromJson(responseBody, JsonObject.class);
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new AIServiceException("OpenAI response contained no choices", 200, false);
        }
        String content = choices.get(0)
                .getAsJsonObject()
                .getAsJsonObject("message")
                .get("content")
                .getAsString();

        int tokensUsed = 0;
        if (root.has("usage") && !root.get("usage").isJsonNull()) {
            JsonObject usage = root.getAsJsonObject("usage");
            if (usage.has("total_tokens")) {
                tokensUsed = usage.get("total_tokens").getAsInt();
            }
        }
        return ChatResponse.success(content, tokensUsed);
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(retryBaseMs * (1L << (attempt - 1))); // exponential back-off
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
