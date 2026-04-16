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

/**
 * ChatProvider backed by a local Ollama instance using the llama3 model.
 * No API key required. Expects Ollama to be running at {@code http://localhost:11434}.
 *
 * <p>On connection refused, returns {@link ChatResponse#failure(String)} with the
 * message "Ollama is not running" rather than throwing, so callers can degrade
 * gracefully without a try/catch.
 */
public class OllamaChatProvider implements ChatProvider {

    private static final String DEFAULT_ENDPOINT = "http://localhost:11434/api/chat";
    private static final String MODEL = "llama3";

    private final HttpClient httpClient;
    private final Gson gson;
    /** Endpoint URL — overridable for tests. */
    final String endpoint;

    public OllamaChatProvider() {
        this(null, DEFAULT_ENDPOINT);
    }

    /**
     * Package-private constructor for testing: inject a pre-built HttpClient
     * and redirect to a local test endpoint.
     */
    OllamaChatProvider(HttpClient httpClient, String endpoint) {
        this.httpClient = (httpClient != null) ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        this.endpoint = endpoint;
    }

    /**
     * Sends a single user message to Ollama and returns the model's reply.
     *
     * @param prompt the user message
     * @return ChatResponse.success with content, or ChatResponse.failure if Ollama is unreachable
     * @throws AIServiceException on non-connection HTTP errors (e.g. 4xx/5xx from Ollama)
     */
    @Override
    public ChatResponse chat(String prompt) throws AIServiceException {
        return post(buildBody(null, prompt));
    }

    /**
     * Sends a system prompt followed by a user message to Ollama and returns the model's reply.
     *
     * @param systemPrompt the system instruction (null or blank is treated as no system message)
     * @param userPrompt   the user message
     * @return ChatResponse.success with content, or ChatResponse.failure if Ollama is unreachable
     * @throws AIServiceException on non-connection HTTP errors
     */
    @Override
    public ChatResponse chatWithSystem(String systemPrompt, String userPrompt) throws AIServiceException {
        return post(buildBody(systemPrompt, userPrompt));
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
        body.addProperty("stream", false);
        return body;
    }

    private ChatResponse post(JsonObject body) throws AIServiceException {
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
                return parseSuccess(response.body());
            }
            throw new AIServiceException(
                    "Ollama API error: HTTP " + status + " — " + response.body(),
                    status, false);

        } catch (AIServiceException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof ConnectException) {
                return ChatResponse.failure("Ollama is not running");
            }
            throw new AIServiceException("HTTP request failed: " + e.getMessage(), e);
        }
    }

    private ChatResponse parseSuccess(String responseBody) throws AIServiceException {
        JsonObject root = gson.fromJson(responseBody, JsonObject.class);
        JsonObject message = root.getAsJsonObject("message");
        if (message == null) {
            throw new AIServiceException("Ollama response contained no message field", 200, false);
        }
        String content = message.get("content").getAsString();
        return ChatResponse.success(content, 0);
    }
}
