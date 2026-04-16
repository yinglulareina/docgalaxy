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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link OpenAIChatProvider}.
 *
 * <p>HTTP behaviour is exercised via an in-process {@link HttpServer} so no
 * real network or API key is required. The package-private constructor is used
 * to inject a pre-built {@link HttpClient} and a zero-delay retry base so
 * retries complete instantly.
 *
 * <p>Coverage areas:
 * <ul>
 *   <li>Construction guards (null / blank API key)</li>
 *   <li>chat() happy path: content, tokens, headers, request body, HTTP method</li>
 *   <li>chatWithSystem() message ordering, null/blank system prompt handling</li>
 *   <li>Response parsing edge cases: no usage, null usage, multiple choices, empty content</li>
 *   <li>Retry on 429 / 5xx; no-retry on 4xx; exact retry count</li>
 *   <li>Exception properties: statusCode, isRetryable, message text</li>
 * </ul>
 */
class OpenAIChatProviderTest {

    private static final String API_KEY = "sk-test-key";
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

    /** Full well-formed OpenAI chat completions response. */
    private static String chatResponse(String content, int totalTokens) {
        JsonObject root = new JsonObject();

        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", content);

        JsonObject choice = new JsonObject();
        choice.add("message", message);

        JsonArray choices = new JsonArray();
        choices.add(choice);
        root.add("choices", choices);

        JsonObject usage = new JsonObject();
        usage.addProperty("total_tokens", totalTokens);
        root.add("usage", usage);

        return GSON.toJson(root);
    }

    /** Response with two choices — only the first should be used. */
    private static String twoChoiceResponse(String first, String second) {
        JsonObject root = new JsonObject();
        JsonArray choices = new JsonArray();
        for (String c : new String[]{first, second}) {
            JsonObject msg = new JsonObject();
            msg.addProperty("role", "assistant");
            msg.addProperty("content", c);
            JsonObject ch = new JsonObject();
            ch.add("message", msg);
            choices.add(ch);
        }
        root.add("choices", choices);
        JsonObject usage = new JsonObject();
        usage.addProperty("total_tokens", 10);
        root.add("usage", usage);
        return GSON.toJson(root);
    }

    /** Response with no usage field at all. */
    private static String chatResponseNoUsage(String content) {
        JsonObject root = new JsonObject();
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", content);
        JsonObject choice = new JsonObject();
        choice.add("message", message);
        JsonArray choices = new JsonArray();
        choices.add(choice);
        root.add("choices", choices);
        return GSON.toJson(root);
    }

    /** Response with usage: null. */
    private static String chatResponseNullUsage(String content) {
        JsonObject root = new JsonObject();
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", content);
        JsonObject choice = new JsonObject();
        choice.add("message", message);
        JsonArray choices = new JsonArray();
        choices.add(choice);
        root.add("choices", choices);
        root.add("usage", null);
        return GSON.toJson(root);
    }

    /** Response with usage object but no total_tokens field. */
    private static String chatResponseUsageWithoutTotalTokens(String content) {
        JsonObject root = new JsonObject();
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", content);
        JsonObject choice = new JsonObject();
        choice.add("message", message);
        JsonArray choices = new JsonArray();
        choices.add(choice);
        root.add("choices", choices);
        JsonObject usage = new JsonObject();
        usage.addProperty("prompt_tokens", 5);  // only partial usage
        root.add("usage", usage);
        return GSON.toJson(root);
    }

    private OpenAIChatProvider provider(String endpoint) {
        return new OpenAIChatProvider(API_KEY, fastClient, 0L, endpoint);
    }

    // -----------------------------------------------------------------------
    // Construction guards
    // -----------------------------------------------------------------------

    @Test
    void constructorThrowsWhenApiKeyIsNull() {
        assertThrows(IllegalStateException.class,
                () -> new OpenAIChatProvider(null));
    }

    @Test
    void constructorThrowsWhenApiKeyIsBlank() {
        assertThrows(IllegalStateException.class,
                () -> new OpenAIChatProvider("  "));
    }

    @Test
    void constructorThrowsWhenApiKeyIsEmptyString() {
        assertThrows(IllegalStateException.class,
                () -> new OpenAIChatProvider(""));
    }

    @Test
    void constructorSucceedsWithValidKey() {
        assertDoesNotThrow(() -> new OpenAIChatProvider(API_KEY));
    }

    // -----------------------------------------------------------------------
    // chat() — happy path
    // -----------------------------------------------------------------------

    @Test
    void chat_happyPath_returnsSuccessWithContent() throws Exception {
        handle("/v1/chat/completions", 200, chatResponse("Hello there!", 10));

        ChatResponse response = provider(baseUrl + "/v1/chat/completions").chat("Hi");

        assertTrue(response.isSuccess());
        assertEquals("Hello there!", response.getContent());
    }

    @Test
    void chat_happyPath_returnsTokensUsed() throws Exception {
        handle("/v1/chat/completions", 200, chatResponse("Answer", 42));

        ChatResponse response = provider(baseUrl + "/v1/chat/completions").chat("Question");

        assertEquals(42, response.getTokensUsed());
    }

    @Test
    void chat_happyPath_errorMessageIsNull() throws Exception {
        handle("/v1/chat/completions", 200, chatResponse("ok", 1));

        ChatResponse response = provider(baseUrl + "/v1/chat/completions").chat("q");

        assertNull(response.getErrorMessage());
    }

    @Test
    void chat_emptyContentString_returnedSuccessfully() throws Exception {
        handle("/v1/chat/completions", 200, chatResponse("", 3));

        ChatResponse response = provider(baseUrl + "/v1/chat/completions").chat("q");

        assertTrue(response.isSuccess());
        assertEquals("", response.getContent());
    }

    @Test
    void chat_contentWithSpecialCharacters_preservedExactly() throws Exception {
        String special = "Line1\nLine2\t\"quoted\" \u00e9";
        handle("/v1/chat/completions", 200, chatResponse(special, 5));

        ChatResponse response = provider(baseUrl + "/v1/chat/completions").chat("q");

        assertEquals(special, response.getContent());
    }

    // -----------------------------------------------------------------------
    // chat() — request structure
    // -----------------------------------------------------------------------

    @Test
    void chat_usesPostMethod() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            method.set(exchange.getRequestMethod());
            respond(exchange, 200, chatResponse("ok", 1));
        });

        provider(baseUrl + "/v1/chat/completions").chat("test");

        assertEquals("POST", method.get());
    }

    @Test
    void chat_sendsAuthorizationHeader() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, chatResponse("ok", 5));
        });

        provider(baseUrl + "/v1/chat/completions").chat("test");

        assertEquals("Bearer " + API_KEY, authHeader.get());
    }

    @Test
    void chat_sendsContentTypeApplicationJson() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            respond(exchange, 200, chatResponse("ok", 5));
        });

        provider(baseUrl + "/v1/chat/completions").chat("test");

        assertNotNull(contentType.get());
        assertTrue(contentType.get().contains("application/json"));
    }

    @Test
    void chat_requestBodyContainsModel() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, chatResponse("ok", 5));
        });

        provider(baseUrl + "/v1/chat/completions").chat("q");

        assertTrue(requestBody.get().contains("gpt-4o-mini"),
                "Request body must specify the gpt-4o-mini model");
    }

    @Test
    void chat_requestBodyContainsUserRoleAndPrompt() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, chatResponse("ok", 5));
        });

        provider(baseUrl + "/v1/chat/completions").chat("my prompt text");

        String body = requestBody.get();
        assertTrue(body.contains("my prompt text"), "Body must contain the prompt text");
        assertTrue(body.contains("\"user\""), "Body must contain user role");
    }

    @Test
    void chat_requestBodyOmitsSystemRole() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, chatResponse("ok", 5));
        });

        provider(baseUrl + "/v1/chat/completions").chat("no system here");

        assertFalse(requestBody.get().contains("\"system\""),
                "chat() must not include a system message");
    }

    // -----------------------------------------------------------------------
    // chatWithSystem() — happy path
    // -----------------------------------------------------------------------

    @Test
    void chatWithSystem_happyPath_returnsContent() throws Exception {
        handle("/v1/chat/completions", 200, chatResponse("System response", 20));

        ChatResponse response = provider(baseUrl + "/v1/chat/completions")
                .chatWithSystem("You are helpful.", "Tell me something");

        assertTrue(response.isSuccess());
        assertEquals("System response", response.getContent());
    }

    @Test
    void chatWithSystem_requestBodyContainsBothMessages() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, chatResponse("ok", 5));
        });

        provider(baseUrl + "/v1/chat/completions")
                .chatWithSystem("sys instruction", "user query");

        String body = requestBody.get();
        assertTrue(body.contains("sys instruction"), "System prompt must be in request body");
        assertTrue(body.contains("user query"), "User prompt must be in request body");
        assertTrue(body.contains("\"system\""), "system role must be present");
        assertTrue(body.contains("\"user\""), "user role must be present");
    }

    @Test
    void chatWithSystem_systemMessageAppearsBeforeUserMessage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, chatResponse("ok", 5));
        });

        provider(baseUrl + "/v1/chat/completions")
                .chatWithSystem("SYSTEM_MARKER", "USER_MARKER");

        String body = requestBody.get();
        int systemIdx = body.indexOf("SYSTEM_MARKER");
        int userIdx = body.indexOf("USER_MARKER");
        assertTrue(systemIdx < userIdx,
                "System message must appear before user message in the JSON body");
    }

    @Test
    void chatWithSystem_nullSystemPrompt_treatedAsNoSystemMessage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, chatResponse("ok", 5));
        });

        provider(baseUrl + "/v1/chat/completions")
                .chatWithSystem(null, "user only");

        assertFalse(requestBody.get().contains("\"system\""),
                "Null system prompt must not add a system message");
        assertTrue(requestBody.get().contains("user only"));
    }

    @Test
    void chatWithSystem_blankSystemPrompt_treatedAsNoSystemMessage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, chatResponse("ok", 5));
        });

        provider(baseUrl + "/v1/chat/completions")
                .chatWithSystem("   ", "user only");

        assertFalse(requestBody.get().contains("\"system\""),
                "Blank system prompt must not add a system message");
    }

    // -----------------------------------------------------------------------
    // Response parsing — edge cases
    // -----------------------------------------------------------------------

    @Test
    void chat_responseWithNoUsageField_returnsZeroTokens() throws Exception {
        handle("/v1/chat/completions", 200, chatResponseNoUsage("content"));

        ChatResponse response = provider(baseUrl + "/v1/chat/completions").chat("q");

        assertTrue(response.isSuccess());
        assertEquals(0, response.getTokensUsed(),
                "Missing usage field should result in 0 tokensUsed");
    }

    @Test
    void chat_responseWithNullUsage_returnsZeroTokens() throws Exception {
        handle("/v1/chat/completions", 200, chatResponseNullUsage("content"));

        ChatResponse response = provider(baseUrl + "/v1/chat/completions").chat("q");

        assertTrue(response.isSuccess());
        assertEquals(0, response.getTokensUsed(),
                "Null usage field should result in 0 tokensUsed");
    }

    @Test
    void chat_responseUsageWithoutTotalTokens_returnsZeroTokens() throws Exception {
        handle("/v1/chat/completions", 200, chatResponseUsageWithoutTotalTokens("content"));

        ChatResponse response = provider(baseUrl + "/v1/chat/completions").chat("q");

        assertTrue(response.isSuccess());
        assertEquals(0, response.getTokensUsed(),
                "Usage without total_tokens should result in 0 tokensUsed");
    }

    @Test
    void chat_multipleChoices_onlyFirstChoiceContentReturned() throws Exception {
        handle("/v1/chat/completions", 200, twoChoiceResponse("first answer", "second answer"));

        ChatResponse response = provider(baseUrl + "/v1/chat/completions").chat("q");

        assertEquals("first answer", response.getContent(),
                "Only the first choice should be used");
    }

    @Test
    void chat_emptyChoicesArray_throwsAIServiceException() {
        JsonObject root = new JsonObject();
        root.add("choices", new JsonArray());
        handle("/v1/chat/completions", 200, GSON.toJson(root));

        assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/chat/completions").chat("text"));
    }

    @Test
    void chat_emptyChoicesArray_exceptionIsNonRetryable() {
        JsonObject root = new JsonObject();
        root.add("choices", new JsonArray());
        handle("/v1/chat/completions", 200, GSON.toJson(root));

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/chat/completions").chat("text"));

        assertFalse(ex.isRetryable(), "Empty choices should be a non-retryable error");
        assertEquals(200, ex.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // Retry — retryable status codes
    // -----------------------------------------------------------------------

    @Test
    void chat_retryOn429_succeedsAfterOneRetry() throws Exception {
        handleSequence("/v1/chat/completions",
                new int[]{429, 200},
                new String[]{"rate limited", chatResponse("Retry success", 5)});

        ChatResponse response = provider(baseUrl + "/v1/chat/completions").chat("text");

        assertTrue(response.isSuccess());
        assertEquals("Retry success", response.getContent());
    }

    @Test
    void chat_retryOn500_succeedsAfterOneRetry() throws Exception {
        handleSequence("/v1/chat/completions",
                new int[]{500, 200},
                new String[]{"internal error", chatResponse("After 500", 5)});

        assertTrue(provider(baseUrl + "/v1/chat/completions").chat("text").isSuccess());
    }

    @Test
    void chat_retryOn502_succeedsAfterOneRetry() throws Exception {
        handleSequence("/v1/chat/completions",
                new int[]{502, 200},
                new String[]{"bad gateway", chatResponse("After 502", 5)});

        assertTrue(provider(baseUrl + "/v1/chat/completions").chat("text").isSuccess());
    }

    @Test
    void chat_retryOn503_succeedsAfterOneRetry() throws Exception {
        handleSequence("/v1/chat/completions",
                new int[]{503, 200},
                new String[]{"unavailable", chatResponse("After 503", 5)});

        assertTrue(provider(baseUrl + "/v1/chat/completions").chat("text").isSuccess());
    }

    @Test
    void chat_retryTwice_succeedsOnThirdAttempt() throws Exception {
        handleSequence("/v1/chat/completions",
                new int[]{429, 429, 200},
                new String[]{"rl", "rl", chatResponse("third time", 5)});

        ChatResponse response = provider(baseUrl + "/v1/chat/completions").chat("text");

        assertTrue(response.isSuccess());
        assertEquals("third time", response.getContent());
    }

    @Test
    void chat_exhaustsRetries_throwsAIServiceException() {
        handle("/v1/chat/completions", 429, "always rate limited");

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/chat/completions").chat("text"));

        assertEquals(429, ex.getStatusCode());
        assertTrue(ex.isRetryable());
    }

    @Test
    void chat_exhaustsRetriesOn500_exceptionCarries500StatusCode() {
        handle("/v1/chat/completions", 500, "error");

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/chat/completions").chat("text"));

        assertEquals(500, ex.getStatusCode());
        assertTrue(ex.isRetryable());
    }

    @Test
    void chat_maxRetriesExactly3_callsServerFourTimes() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/chat/completions", exchange -> {
            callCount.incrementAndGet();
            respond(exchange, 500, "error");
        });

        assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/chat/completions").chat("text"));

        // 1 initial attempt + 3 retries = 4 total calls
        assertEquals(4, callCount.get());
    }

    // -----------------------------------------------------------------------
    // Retry — non-retryable status codes
    // -----------------------------------------------------------------------

    @Test
    void chat_nonRetryable400_throwsImmediately() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/chat/completions", exchange -> {
            callCount.incrementAndGet();
            respond(exchange, 400, "bad request");
        });

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/chat/completions").chat("text"));

        assertEquals(400, ex.getStatusCode());
        assertFalse(ex.isRetryable());
        assertEquals(1, callCount.get(), "Must not retry on 400");
    }

    @Test
    void chat_nonRetryable401_throwsImmediately() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/chat/completions", exchange -> {
            callCount.incrementAndGet();
            respond(exchange, 401, "unauthorized");
        });

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/chat/completions").chat("text"));

        assertEquals(401, ex.getStatusCode());
        assertFalse(ex.isRetryable());
        assertEquals(1, callCount.get());
    }

    @Test
    void chat_nonRetryable403_throwsImmediately() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/chat/completions", exchange -> {
            callCount.incrementAndGet();
            respond(exchange, 403, "forbidden");
        });

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/chat/completions").chat("text"));

        assertEquals(403, ex.getStatusCode());
        assertFalse(ex.isRetryable());
        assertEquals(1, callCount.get(), "Must not retry on 403");
    }

    @Test
    void chat_nonRetryable422_throwsImmediately() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/chat/completions", exchange -> {
            callCount.incrementAndGet();
            respond(exchange, 422, "unprocessable");
        });

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/chat/completions").chat("text"));

        assertEquals(422, ex.getStatusCode());
        assertFalse(ex.isRetryable());
        assertEquals(1, callCount.get(), "Must not retry on 422");
    }

    // -----------------------------------------------------------------------
    // Exception properties
    // -----------------------------------------------------------------------

    @Test
    void chat_exceptionMessageContainsStatusCode() {
        handle("/v1/chat/completions", 429, "rate limited");

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/chat/completions").chat("text"));

        assertTrue(ex.getMessage().contains("429"),
                "Exception message should contain the HTTP status code");
    }

    @Test
    void chat_400Exception_isRetryableFalse() {
        handle("/v1/chat/completions", 400, "bad request");

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/chat/completions").chat("text"));

        assertFalse(ex.isRetryable());
    }

    @Test
    void chat_500Exception_isRetryableTrue() {
        handle("/v1/chat/completions", 500, "error");

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/chat/completions").chat("text"));

        assertTrue(ex.isRetryable());
    }

    // -----------------------------------------------------------------------
    // chatWithSystem() — retry mirrors chat()
    // -----------------------------------------------------------------------

    @Test
    void chatWithSystem_retryOn429_succeedsAfterOneRetry() throws Exception {
        handleSequence("/v1/chat/completions",
                new int[]{429, 200},
                new String[]{"rate limited", chatResponse("OK after retry", 8)});

        ChatResponse response = provider(baseUrl + "/v1/chat/completions")
                .chatWithSystem("sys", "user");

        assertTrue(response.isSuccess());
        assertEquals("OK after retry", response.getContent());
    }

    @Test
    void chatWithSystem_nonRetryable401_throwsImmediately() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/chat/completions", exchange -> {
            callCount.incrementAndGet();
            respond(exchange, 401, "unauthorized");
        });

        assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/v1/chat/completions")
                        .chatWithSystem("sys", "user"));

        assertEquals(1, callCount.get());
    }
}
