package com.docgalaxy.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OllamaEmbeddingProvider using an in-process HttpServer — no real Ollama required.
 */
class OllamaEmbeddingProviderTest {

    private static final Gson GSON = new Gson();

    private HttpServer server;
    private String baseUrl;
    private HttpClient fastClient;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        int port = server.getAddress().getPort();
        baseUrl = "http://localhost:" + port;
        fastClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void handle(String path, int status, String body) {
        server.createContext(path, exchange -> respond(exchange, status, body));
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes();
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Builds a valid Ollama embeddings JSON response. */
    private static String embeddingResponse(double[] vec) {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (double v : vec) arr.add(v);
        root.add("embedding", arr);
        return GSON.toJson(root);
    }

    private OllamaEmbeddingProvider provider(String endpoint) {
        return new OllamaEmbeddingProvider(fastClient, endpoint);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    @Test
    void getDimension_returns768() {
        assertEquals(768, new OllamaEmbeddingProvider(fastClient, baseUrl).getDimension());
    }

    @Test
    void getModelName_returnsNomicEmbedText() {
        assertEquals("nomic-embed-text",
                new OllamaEmbeddingProvider(fastClient, baseUrl).getModelName());
    }

    // -----------------------------------------------------------------------
    // embed() — input validation
    // -----------------------------------------------------------------------

    @Test
    void embed_throwsOnNullText() {
        assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/embeddings").embed(null));
    }

    @Test
    void embed_throwsOnBlankText() {
        assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/embeddings").embed("   "));
    }

    // -----------------------------------------------------------------------
    // embed() — happy path
    // -----------------------------------------------------------------------

    @Test
    void embed_happyPath_returnsCorrectVector() throws Exception {
        double[] expected = new double[768];
        for (int i = 0; i < expected.length; i++) expected[i] = i * 0.001;
        handle("/api/embeddings", 200, embeddingResponse(expected));

        double[] actual = provider(baseUrl + "/api/embeddings").embed("hello world");

        assertArrayEquals(expected, actual, 1e-9);
    }

    @Test
    void embed_happyPath_returnsVectorOfDimension768() throws Exception {
        handle("/api/embeddings", 200, embeddingResponse(new double[768]));

        double[] result = provider(baseUrl + "/api/embeddings").embed("test");

        assertEquals(768, result.length);
    }

    @Test
    void embed_sendsPromptFieldInBody() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/api/embeddings", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, embeddingResponse(new double[768]));
        });

        provider(baseUrl + "/api/embeddings").embed("my text");

        JsonObject sent = GSON.fromJson(capturedBody.get(), JsonObject.class);
        assertEquals("my text", sent.get("prompt").getAsString());
        assertEquals("nomic-embed-text", sent.get("model").getAsString());
    }

    @Test
    void embed_sendsContentTypeApplicationJson() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        server.createContext("/api/embeddings", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            respond(exchange, 200, embeddingResponse(new double[768]));
        });

        provider(baseUrl + "/api/embeddings").embed("test");

        assertNotNull(contentType.get());
        assertTrue(contentType.get().contains("application/json"));
    }

    // -----------------------------------------------------------------------
    // embed() — error scenarios
    // -----------------------------------------------------------------------

    @Test
    void embed_non200Response_throwsNonRetryableException() {
        handle("/api/embeddings", 500, "internal error");

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/embeddings").embed("text"));

        assertEquals(500, ex.getStatusCode());
        assertFalse(ex.isRetryable());
    }

    @Test
    void embed_404Response_throwsNonRetryableException() {
        handle("/api/embeddings", 404, "not found");

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/embeddings").embed("text"));

        assertEquals(404, ex.getStatusCode());
        assertFalse(ex.isRetryable());
    }

    @Test
    void embed_emptyEmbeddingArray_throwsAIServiceException() throws Exception {
        JsonObject root = new JsonObject();
        root.add("embedding", new JsonArray());
        handle("/api/embeddings", 200, GSON.toJson(root));

        assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/embeddings").embed("text"));
    }

    @Test
    void embed_connectionRefused_throwsWithOllamaNotRunningMessage() {
        // Use a port nothing is listening on
        OllamaEmbeddingProvider p = new OllamaEmbeddingProvider(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                "http://localhost:19999/api/embeddings");

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> p.embed("text"));

        assertEquals("Ollama is not running", ex.getMessage());
        assertFalse(ex.isRetryable());
    }

    // -----------------------------------------------------------------------
    // batchEmbed()
    // -----------------------------------------------------------------------

    @Test
    void batchEmbed_nullList_returnsEmpty() throws AIServiceException {
        assertTrue(provider(baseUrl).batchEmbed(null).isEmpty());
    }

    @Test
    void batchEmbed_emptyList_returnsEmpty() throws AIServiceException {
        assertTrue(provider(baseUrl).batchEmbed(List.of()).isEmpty());
    }

    @Test
    void batchEmbed_multipleTexts_returnsOneVectorPerText() throws Exception {
        double[] v1 = {0.1, 0.2};
        double[] v2 = {0.3, 0.4};
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/api/embeddings", exchange -> {
            int idx = callCount.getAndIncrement();
            respond(exchange, 200, embeddingResponse(idx == 0 ? v1 : v2));
        });

        List<double[]> result = provider(baseUrl + "/api/embeddings")
                .batchEmbed(List.of("first", "second"));

        assertEquals(2, result.size());
        assertArrayEquals(v1, result.get(0), 1e-9);
        assertArrayEquals(v2, result.get(1), 1e-9);
    }

    @Test
    void batchEmbed_singleText_returnsOneVector() throws Exception {
        double[] vec = {1.0, 2.0, 3.0};
        handle("/api/embeddings", 200, embeddingResponse(vec));

        List<double[]> result = provider(baseUrl + "/api/embeddings")
                .batchEmbed(List.of("hello"));

        assertEquals(1, result.size());
        assertArrayEquals(vec, result.get(0), 1e-9);
    }

    // -----------------------------------------------------------------------
    // embed() — additional edge cases
    // -----------------------------------------------------------------------

    @Test
    void embed_emptyString_throwsAIServiceException() {
        assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/embeddings").embed(""));
    }

    @Test
    void embed_singleCharText_succeeds() throws Exception {
        double[] vec = {0.5, 0.5};
        handle("/api/embeddings", 200, embeddingResponse(vec));

        double[] result = provider(baseUrl + "/api/embeddings").embed("x");

        assertArrayEquals(vec, result, 1e-9);
    }

    @Test
    void embed_missingEmbeddingField_throwsAIServiceException() throws Exception {
        // Response body has no "embedding" key at all
        handle("/api/embeddings", 200, "{\"model\":\"nomic-embed-text\"}");

        assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/embeddings").embed("text"));
    }

    @Test
    void embed_negativeAndFractionalValues_preservedExactly() throws Exception {
        double[] vec = {-0.123456789, 0.0, -1.0, 0.999999999};
        handle("/api/embeddings", 200, embeddingResponse(vec));

        double[] result = provider(baseUrl + "/api/embeddings").embed("negative test");

        assertArrayEquals(vec, result, 1e-9);
    }

    @Test
    void embed_errorMessageContainsHttpStatus() {
        handle("/api/embeddings", 503, "service unavailable");

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/embeddings").embed("text"));

        assertEquals(503, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("503"));
    }

    @Test
    void embed_connectionRefused_statusCodeIsZero() {
        OllamaEmbeddingProvider p = new OllamaEmbeddingProvider(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                "http://localhost:19999/api/embeddings");

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> p.embed("text"));

        assertEquals(0, ex.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // batchEmbed() — additional edge cases
    // -----------------------------------------------------------------------

    @Test
    void batchEmbed_propagatesException_whenEmbedFails() {
        handle("/api/embeddings", 500, "error");

        assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/embeddings")
                        .batchEmbed(List.of("a", "b", "c")));
    }

    @Test
    void batchEmbed_threeTexts_preservesOrder() throws Exception {
        double[] v0 = {1.0, 0.0};
        double[] v1 = {0.0, 1.0};
        double[] v2 = {0.5, 0.5};
        AtomicInteger callCount = new AtomicInteger(0);
        double[][] responses = {v0, v1, v2};
        server.createContext("/api/embeddings", exchange -> {
            int idx = callCount.getAndIncrement();
            respond(exchange, 200, embeddingResponse(responses[idx]));
        });

        List<double[]> result = provider(baseUrl + "/api/embeddings")
                .batchEmbed(List.of("first", "second", "third"));

        assertEquals(3, result.size());
        assertArrayEquals(v0, result.get(0), 1e-9);
        assertArrayEquals(v1, result.get(1), 1e-9);
        assertArrayEquals(v2, result.get(2), 1e-9);
    }

    @Test
    void batchEmbed_callsEmbedOncePerText() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/api/embeddings", exchange -> {
            callCount.incrementAndGet();
            respond(exchange, 200, embeddingResponse(new double[]{0.1}));
        });

        provider(baseUrl + "/api/embeddings").batchEmbed(List.of("a", "b", "c", "d"));

        assertEquals(4, callCount.get());
    }
}
