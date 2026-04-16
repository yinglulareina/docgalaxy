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
 * Tests for OllamaChatProvider using an in-process HttpServer — no real Ollama required.
 */
class OllamaChatProviderTest {

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

    /** Builds a valid Ollama /api/chat response. */
    private static String chatResponse(String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", content);
        JsonObject root = new JsonObject();
        root.add("message", message);
        return GSON.toJson(root);
    }

    private OllamaChatProvider provider(String endpoint) {
        return new OllamaChatProvider(fastClient, endpoint);
    }

    // -----------------------------------------------------------------------
    // chat() — happy path
    // -----------------------------------------------------------------------

    @Test
    void chat_happyPath_returnsSuccess() throws Exception {
        handle("/api/chat", 200, chatResponse("Hello!"));

        ChatResponse response = provider(baseUrl + "/api/chat").chat("Hi");

        assertTrue(response.isSuccess());
        assertEquals("Hello!", response.getContent());
    }

    @Test
    void chat_happyPath_errorMessageIsNull() throws Exception {
        handle("/api/chat", 200, chatResponse("ok"));

        ChatResponse response = provider(baseUrl + "/api/chat").chat("q");

        assertNull(response.getErrorMessage());
    }

    @Test
    void chat_happyPath_tokensUsedIsZero() throws Exception {
        handle("/api/chat", 200, chatResponse("answer"));

        ChatResponse response = provider(baseUrl + "/api/chat").chat("q");

        assertEquals(0, response.getTokensUsed());
    }

    @Test
    void chat_emptyContent_returnedSuccessfully() throws Exception {
        handle("/api/chat", 200, chatResponse(""));

        ChatResponse response = provider(baseUrl + "/api/chat").chat("q");

        assertTrue(response.isSuccess());
        assertEquals("", response.getContent());
    }

    @Test
    void chat_contentWithSpecialCharacters_preservedExactly() throws Exception {
        String special = "Line1\nLine2\t\"quoted\" \u00e9";
        handle("/api/chat", 200, chatResponse(special));

        ChatResponse response = provider(baseUrl + "/api/chat").chat("q");

        assertEquals(special, response.getContent());
    }

    // -----------------------------------------------------------------------
    // chat() — request structure
    // -----------------------------------------------------------------------

    @Test
    void chat_usesPostMethod() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        server.createContext("/api/chat", exchange -> {
            method.set(exchange.getRequestMethod());
            respond(exchange, 200, chatResponse("ok"));
        });

        provider(baseUrl + "/api/chat").chat("test");

        assertEquals("POST", method.get());
    }

    @Test
    void chat_sendsContentTypeApplicationJson() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        server.createContext("/api/chat", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            respond(exchange, 200, chatResponse("ok"));
        });

        provider(baseUrl + "/api/chat").chat("test");

        assertNotNull(contentType.get());
        assertTrue(contentType.get().contains("application/json"));
    }

    @Test
    void chat_requestBodyContainsModelLlama3() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/api/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, chatResponse("ok"));
        });

        provider(baseUrl + "/api/chat").chat("q");

        assertTrue(requestBody.get().contains("llama3"),
                "Request body must specify the llama3 model");
    }

    @Test
    void chat_requestBodyContainsStreamFalse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/api/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, chatResponse("ok"));
        });

        provider(baseUrl + "/api/chat").chat("q");

        JsonObject sent = GSON.fromJson(requestBody.get(), JsonObject.class);
        assertFalse(sent.get("stream").getAsBoolean(), "stream must be false");
    }

    @Test
    void chat_requestBodyContainsUserRoleAndPrompt() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/api/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, chatResponse("ok"));
        });

        provider(baseUrl + "/api/chat").chat("my prompt text");

        String body = requestBody.get();
        assertTrue(body.contains("my prompt text"), "Body must contain the prompt text");
        assertTrue(body.contains("\"user\""), "Body must contain user role");
    }

    @Test
    void chat_requestBodyOmitsSystemRole() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/api/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, chatResponse("ok"));
        });

        provider(baseUrl + "/api/chat").chat("no system");

        assertFalse(requestBody.get().contains("\"system\""),
                "chat() must not include a system message");
    }

    // -----------------------------------------------------------------------
    // chatWithSystem() — happy path
    // -----------------------------------------------------------------------

    @Test
    void chatWithSystem_happyPath_returnsContent() throws Exception {
        handle("/api/chat", 200, chatResponse("System response"));

        ChatResponse response = provider(baseUrl + "/api/chat")
                .chatWithSystem("You are helpful.", "Tell me something");

        assertTrue(response.isSuccess());
        assertEquals("System response", response.getContent());
    }

    @Test
    void chatWithSystem_requestBodyContainsBothMessages() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/api/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, chatResponse("ok"));
        });

        provider(baseUrl + "/api/chat").chatWithSystem("sys instruction", "user query");

        String body = requestBody.get();
        assertTrue(body.contains("sys instruction"), "System prompt must be in body");
        assertTrue(body.contains("user query"), "User prompt must be in body");
        assertTrue(body.contains("\"system\""), "system role must be present");
        assertTrue(body.contains("\"user\""), "user role must be present");
    }

    @Test
    void chatWithSystem_systemMessageAppearsBeforeUserMessage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/api/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, chatResponse("ok"));
        });

        provider(baseUrl + "/api/chat").chatWithSystem("SYSTEM_MARKER", "USER_MARKER");

        String body = requestBody.get();
        assertTrue(body.indexOf("SYSTEM_MARKER") < body.indexOf("USER_MARKER"),
                "System message must appear before user message in the JSON body");
    }

    @Test
    void chatWithSystem_nullSystemPrompt_treatedAsNoSystemMessage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/api/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, chatResponse("ok"));
        });

        provider(baseUrl + "/api/chat").chatWithSystem(null, "user only");

        assertFalse(requestBody.get().contains("\"system\""),
                "Null system prompt must not add a system message");
        assertTrue(requestBody.get().contains("user only"));
    }

    @Test
    void chatWithSystem_blankSystemPrompt_treatedAsNoSystemMessage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/api/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, chatResponse("ok"));
        });

        provider(baseUrl + "/api/chat").chatWithSystem("   ", "user only");

        assertFalse(requestBody.get().contains("\"system\""),
                "Blank system prompt must not add a system message");
    }

    // -----------------------------------------------------------------------
    // Response parsing edge cases
    // -----------------------------------------------------------------------

    @Test
    void chat_missingMessageField_throwsAIServiceException() {
        handle("/api/chat", 200, "{\"model\":\"llama3\"}");

        assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/chat").chat("text"));
    }

    // -----------------------------------------------------------------------
    // Connection refused — returns failure, never throws
    // -----------------------------------------------------------------------

    @Test
    void chat_connectionRefused_returnsFailureNotThrows() throws Exception {
        OllamaChatProvider p = new OllamaChatProvider(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                "http://localhost:19999/api/chat");

        ChatResponse response = p.chat("text");

        assertFalse(response.isSuccess());
        assertEquals("Ollama is not running", response.getErrorMessage());
    }

    @Test
    void chat_connectionRefused_contentIsNull() throws Exception {
        OllamaChatProvider p = new OllamaChatProvider(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                "http://localhost:19999/api/chat");

        ChatResponse response = p.chat("text");

        assertNull(response.getContent());
    }

    @Test
    void chatWithSystem_connectionRefused_returnsFailure() throws Exception {
        OllamaChatProvider p = new OllamaChatProvider(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                "http://localhost:19999/api/chat");

        ChatResponse response = p.chatWithSystem("sys", "user");

        assertFalse(response.isSuccess());
        assertEquals("Ollama is not running", response.getErrorMessage());
    }

    // -----------------------------------------------------------------------
    // HTTP error codes — throw AIServiceException
    // -----------------------------------------------------------------------

    @Test
    void chat_non200Response_throwsAIServiceException() {
        handle("/api/chat", 500, "internal error");

        assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/chat").chat("text"));
    }

    @Test
    void chat_404Response_throwsAIServiceException() {
        handle("/api/chat", 404, "not found");

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/chat").chat("text"));

        assertEquals(404, ex.getStatusCode());
        assertFalse(ex.isRetryable());
    }

    @Test
    void chat_500Response_throwsWithStatusCode500() {
        handle("/api/chat", 500, "server error");

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/chat").chat("text"));

        assertEquals(500, ex.getStatusCode());
    }

    @Test
    void chat_errorMessageContainsHttpStatus() {
        handle("/api/chat", 503, "unavailable");

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/chat").chat("text"));

        assertTrue(ex.getMessage().contains("503"));
    }

    @Test
    void chat_httpErrors_areNotRetryable() {
        handle("/api/chat", 500, "error");

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/chat").chat("text"));

        assertFalse(ex.isRetryable(),
                "Ollama errors should not be retryable (no retry logic implemented)");
    }

    @Test
    void chat_httpError_callsServerExactlyOnce() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/api/chat", exchange -> {
            callCount.incrementAndGet();
            respond(exchange, 500, "error");
        });

        assertThrows(AIServiceException.class,
                () -> provider(baseUrl + "/api/chat").chat("text"));

        assertEquals(1, callCount.get(), "No retry — must call server exactly once");
    }
}
