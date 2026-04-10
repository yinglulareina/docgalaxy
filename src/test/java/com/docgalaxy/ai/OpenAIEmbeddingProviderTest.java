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
 * Comprehensive tests for OpenAIEmbeddingProvider.
 *
 * <p>HTTP behaviour is exercised via an in-process {@link HttpServer} so no
 * real network or API key is required.  Tests that need network stubbing use
 * a package-private constructor that accepts a pre-built {@link HttpClient}
 * and a zero-delay retry base so retries complete instantly.
 */
class OpenAIEmbeddingProviderTest {

    private static final String API_KEY = "sk-test-key";
    private static final Gson GSON = new Gson();

    /** Spins up on a random port; torn down after each test. */
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

    /** Registers a handler that always responds with the given status + body. */
    private void handle(String path, int status, String body) {
        server.createContext(path, exchange -> respond(exchange, status, body));
    }

    /** Responds with a sequence of (status, body) pairs, then repeats the last. */
    private void handleSequence(String path, int[] statuses, String[] bodies) {
        AtomicInteger idx = new AtomicInteger(0);
        server.createContext(path, exchange -> {
            int i = Math.min(idx.getAndIncrement(), statuses.length - 1);
            respond(exchange, statuses[i], bodies[i]);
        });
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes();
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Builds a valid OpenAI embeddings JSON response for a single vector. */
    private static String singleEmbeddingResponse(double[] vec) {
        JsonObject root = new JsonObject();
        JsonArray data = new JsonArray();
        JsonObject entry = new JsonObject();
        JsonArray arr = new JsonArray();
        for (double v : vec) arr.add(v);
        entry.add("embedding", arr);
        data.add(entry);
        root.add("data", data);
        return GSON.toJson(root);
    }

    /** Builds a valid response with multiple embeddings. */
    private static String multiEmbeddingResponse(double[]... vecs) {
        JsonObject root = new JsonObject();
        JsonArray data = new JsonArray();
        for (double[] vec : vecs) {
            JsonObject entry = new JsonObject();
            JsonArray arr = new JsonArray();
            for (double v : vec) arr.add(v);
            entry.add("embedding", arr);
            data.add(entry);
        }
        root.add("data", data);
        return GSON.toJson(root);
    }

    private OpenAIEmbeddingProvider provider(String endpoint) {
        return new OpenAIEmbeddingProvider(API_KEY, fastClient, 0L, endpoint);
    }

    // -----------------------------------------------------------------------
    // Construction guards
    // -----------------------------------------------------------------------

    @Test
    void constructorThrowsWhenApiKeyIsNull() {
        assertThrows(IllegalStateException.class,
                () -> new OpenAIEmbeddingProvider(null));
    }

    @Test
    void constructorThrowsWhenApiKeyIsBlank() {
        assertThrows(IllegalStateException.class,
                () -> new OpenAIEmbeddingProvider("  "));
    }

    @Test
    void constructorSucceedsWithValidKey() {
        assertDoesNotThrow(() -> new OpenAIEmbeddingProvider(API_KEY));
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    @Test
    void getDimension_returns1536() {
        assertEquals(1536, new OpenAIEmbeddingProvider(API_KEY).getDimension());
    }

    @Test
    void getModelName_returnsTextEmbedding3Small() {
        assertEquals("text-embedding-3-small",
                new OpenAIEmbeddingProvider(API_KEY).getModelName());
    }

    // -----------------------------------------------------------------------
    // embed() — input validation (no network)
    // -----------------------------------------------------------------------

    @Test
    void embed_throwsOnNullText() {
        assertThrows(AIServiceException.class,
                () -> new OpenAIEmbeddingProvider(API_KEY).embed(null));
    }

    @Test
    void embed_throwsOnBlankText() {
        assertThrows(AIServiceException.class,
                () -> new OpenAIEmbeddingProvider(API_KEY).embed("   "));
    }

    // -----------------------------------------------------------------------
    // embed() — happy path
    // -----------------------------------------------------------------------

    @Test
    void embed_happyPath_returnsCorrectVectorValues() throws Exception {
        double[] expected = new double[1536];
        for (int i = 0; i < expected.length; i++) expected[i] = i * 0.001;
        handle("/v1/embeddings", 200, singleEmbeddingResponse(expected));

        double[] actual = provider(baseUrl + "/v1/embeddings").embed("hello world");

        assertArrayEquals(expected, actual, 1e-9);
    }

    @Test
    void embed_happyPath_returnsVectorOfDimension1536() throws Exception {
        double[] vec = new double[1536];
        handle("/v1/embeddings", 200, singleEmbeddingResponse(vec));

        double[] result = provider(baseUrl + "/v1/embeddings").embed("hello");

        assertEquals(1536, result.length);
    }

    @Test
    void embed_sendsAuthorizationHeader() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();
        server.createContext("/v1/embeddings", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, singleEmbeddingResponse(new double[1536]));
        });

        provider(baseUrl + "/v1/embeddings").embed("test");

        assertEquals("Bearer " + API_KEY, authHeader.get());
    }

    @Test
    void embed_sendsContentTypeApplicationJson() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        server.createContext("/v1/embeddings", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            respond(exchange, 200, singleEmbeddingResponse(new double[1536]));
        });

        provider(baseUrl + "/v1/embeddings").embed("test");

        assertNotNull(contentType.get());
        assertTrue(contentType.get().contains("application/json"));
    }

    // -----------------------------------------------------------------------
    // embed() — error / retry scenarios
    // -----------------------------------------------------------------------

    @Test
    void embed_retryOn429_succeedsAfterOneRetry() throws Exception {
        double[] expected = {0.1, 0.2, 0.3};
        handleSequence("/v1/embeddings",
                new int[]{429, 200},
                new String[]{"rate limited", singleEmbeddingResponse(expected)});

        double[] result = provider(baseUrl + "/v1/embeddings").embed("text");

        assertArrayEquals(expected, result, 1e-9);
    }

    @Test
    void embed_retryOn500_succeedsAfterOneRetry() throws Exception {
        double[] expected = {1.0, 0.5};
        handleSequence("/v1/embeddings",
                new int[]{500, 200},
                new String[]{"internal error", singleEmbeddingResponse(expected)});

        double[] result = provider(baseUrl + "/v1/embeddings").embed("text");

        assertArrayEquals(expected, result, 1e-9);
    }

    @Test
    void embed_exhaustsRetries_throwsAIServiceException() {
        handle("/v1/embeddings", 429, "always rate limited");

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/embeddings").embed("text"));

        assertEquals(429, ex.getStatusCode());
        assertTrue(ex.isRetryable());
    }

    @Test
    void embed_nonRetryable400_throwsImmediatelyWithoutRetrying() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/embeddings", exchange -> {
            callCount.incrementAndGet();
            respond(exchange, 400, "bad request");
        });

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/embeddings").embed("text"));

        assertEquals(400, ex.getStatusCode());
        assertFalse(ex.isRetryable());
        assertEquals(1, callCount.get(), "Should not retry on 400");
    }

    @Test
    void embed_401_throwsNonRetryableImmediately() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/embeddings", exchange -> {
            callCount.incrementAndGet();
            respond(exchange, 401, "unauthorized");
        });

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/embeddings").embed("text"));

        assertEquals(401, ex.getStatusCode());
        assertFalse(ex.isRetryable());
        assertEquals(1, callCount.get());
    }

    @Test
    void embed_maxRetriesExactly3_callsServerFourTimes() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/embeddings", exchange -> {
            callCount.incrementAndGet();
            respond(exchange, 500, "error");
        });

        assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/embeddings").embed("text"));

        // 1 initial attempt + 3 retries = 4 total calls
        assertEquals(4, callCount.get());
    }

    @Test
    void embed_emptyDataArrayInResponse_throwsAIServiceException() throws Exception {
        JsonObject root = new JsonObject();
        root.add("data", new JsonArray()); // empty data array
        handle("/v1/embeddings", 200, GSON.toJson(root));

        assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/embeddings").embed("text"));
    }

    // -----------------------------------------------------------------------
    // batchEmbed() — fast paths (no network)
    // -----------------------------------------------------------------------

    @Test
    void batchEmbed_nullList_returnsEmpty() throws AIServiceException {
        assertTrue(new OpenAIEmbeddingProvider(API_KEY).batchEmbed(null).isEmpty());
    }

    @Test
    void batchEmbed_emptyList_returnsEmpty() throws AIServiceException {
        assertTrue(new OpenAIEmbeddingProvider(API_KEY).batchEmbed(List.of()).isEmpty());
    }

    // -----------------------------------------------------------------------
    // batchEmbed() — happy path
    // -----------------------------------------------------------------------

    @Test
    void batchEmbed_singleText_returnsOneVector() throws Exception {
        double[] vec = {0.1, 0.2, 0.3};
        handle("/v1/embeddings", 200, multiEmbeddingResponse(vec));

        List<double[]> result = provider(baseUrl + "/v1/embeddings")
                .batchEmbed(List.of("one"));

        assertEquals(1, result.size());
        assertArrayEquals(vec, result.get(0), 1e-9);
    }

    @Test
    void batchEmbed_multipleTexts_returnsCorrectCount() throws Exception {
        handle("/v1/embeddings", 200,
                multiEmbeddingResponse(new double[]{1, 0}, new double[]{0, 1}, new double[]{1, 1}));

        List<double[]> result = provider(baseUrl + "/v1/embeddings")
                .batchEmbed(List.of("a", "b", "c"));

        assertEquals(3, result.size());
    }

    @Test
    void batchEmbed_vectorValuesMatchResponseOrder() throws Exception {
        double[] v0 = {1.0, 2.0};
        double[] v1 = {3.0, 4.0};
        handle("/v1/embeddings", 200, multiEmbeddingResponse(v0, v1));

        List<double[]> result = provider(baseUrl + "/v1/embeddings")
                .batchEmbed(List.of("first", "second"));

        assertArrayEquals(v0, result.get(0), 1e-9);
        assertArrayEquals(v1, result.get(1), 1e-9);
    }

    @Test
    void batchEmbed_retryOn429_succeedsAfterOneRetry() throws Exception {
        double[] vec = {0.5, 0.6};
        handleSequence("/v1/embeddings",
                new int[]{429, 200},
                new String[]{"rate limited", multiEmbeddingResponse(vec)});

        List<double[]> result = provider(baseUrl + "/v1/embeddings")
                .batchEmbed(List.of("text"));

        assertEquals(1, result.size());
        assertArrayEquals(vec, result.get(0), 1e-9);
    }

    // -----------------------------------------------------------------------
    // AIServiceException contract
    // -----------------------------------------------------------------------

    @Test
    void aiServiceException_retryableStatusAndFlag() {
        AIServiceException ex = new AIServiceException("rate limited", 429, true);
        assertEquals(429, ex.getStatusCode());
        assertTrue(ex.isRetryable());
    }

    @Test
    void aiServiceException_nonRetryableStatus() {
        AIServiceException ex = new AIServiceException("bad request", 400, false);
        assertEquals(400, ex.getStatusCode());
        assertFalse(ex.isRetryable());
    }

    @Test
    void aiServiceException_withCause_statusIsZeroNonRetryable() {
        RuntimeException cause = new RuntimeException("network down");
        AIServiceException ex = new AIServiceException("wrapped", cause);
        assertEquals(0, ex.getStatusCode());
        assertFalse(ex.isRetryable());
        assertSame(cause, ex.getCause());
    }
}
