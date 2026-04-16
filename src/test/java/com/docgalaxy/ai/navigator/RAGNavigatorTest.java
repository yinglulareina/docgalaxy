package com.docgalaxy.ai.navigator;

import com.docgalaxy.ai.AIServiceException;
import com.docgalaxy.ai.ChatProvider;
import com.docgalaxy.ai.ChatResponse;
import com.docgalaxy.ai.EmbeddingProvider;
import com.docgalaxy.ai.VectorDatabase;
import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.model.Note;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link RAGNavigator}.
 *
 * <p>All dependencies are lightweight in-process stubs — no real network calls
 * or API keys. The {@link VectorDatabase} singleton is cleared before each test.
 *
 * <p>Coverage areas:
 * <ul>
 *   <li>Constructor null guards</li>
 *   <li>navigate() happy path: content, route steps (noteId/reason/order), summary,
 *       estimatedTime appended, call counts, chatWithSystem used</li>
 *   <li>navigate() JSON edge cases: missing summary/estimatedTime/route fields,
 *       null estimatedTime, steps without reason/order, single-step route</li>
 *   <li>navigate() markdown fence stripping (with/without language tag,
 *       leading whitespace)</li>
 *   <li>navigate() fallback: embedding failure, chat exception, chat failure
 *       response, malformed JSON, empty route array, empty DB, all-orphan
 *       candidates; fallback route carries topK ids collected before failure</li>
 *   <li>navigate() never-throws contract</li>
 *   <li>searchSimilar(): result Notes, topK limits, empty DB, topK larger than
 *       DB, orphan IDs skipped, no chat call, embed called per invocation</li>
 * </ul>
 */
class RAGNavigatorTest {

    // -----------------------------------------------------------------------
    // Stubs
    // -----------------------------------------------------------------------

    static class FixedEmbeddingProvider implements EmbeddingProvider {
        private final double[] vector;
        int embedCallCount = 0;

        FixedEmbeddingProvider(double[] vector) { this.vector = vector; }

        @Override public double[] embed(String text) { embedCallCount++; return vector.clone(); }
        @Override public List<double[]> batchEmbed(List<String> texts) { return List.of(vector.clone()); }
        @Override public int getDimension() { return vector.length; }
        @Override public String getModelName() { return "stub"; }
    }

    static class FailingEmbeddingProvider implements EmbeddingProvider {
        @Override public double[] embed(String text) throws AIServiceException {
            throw new AIServiceException("embed failed", 500, true);
        }
        @Override public List<double[]> batchEmbed(List<String> texts) { return List.of(); }
        @Override public int getDimension() { return 3; }
        @Override public String getModelName() { return "fail"; }
    }

    static class FixedChatProvider implements ChatProvider {
        private final ChatResponse response;
        int callCount = 0;

        FixedChatProvider(ChatResponse response) { this.response = response; }

        @Override public ChatResponse chat(String prompt) { callCount++; return response; }
        @Override public ChatResponse chatWithSystem(String sys, String user) { callCount++; return response; }
    }

    static class FailingChatProvider implements ChatProvider {
        @Override public ChatResponse chat(String p) throws AIServiceException {
            throw new AIServiceException("chat failed", 500, true);
        }
        @Override public ChatResponse chatWithSystem(String s, String u) throws AIServiceException {
            throw new AIServiceException("chat failed", 500, true);
        }
    }

    /** Records the system and user prompts passed to chatWithSystem. */
    static class RecordingChatProvider implements ChatProvider {
        final AtomicReference<String> capturedSystem = new AtomicReference<>();
        final AtomicReference<String> capturedUser   = new AtomicReference<>();
        private final ChatResponse response;

        RecordingChatProvider(ChatResponse response) { this.response = response; }

        @Override public ChatResponse chat(String prompt) { return response; }
        @Override public ChatResponse chatWithSystem(String sys, String user) {
            capturedSystem.set(sys);
            capturedUser.set(user);
            return response;
        }
    }

    // -----------------------------------------------------------------------
    // JSON helpers
    // -----------------------------------------------------------------------

    /** Full well-formed response with summary, estimatedTime, and route. */
    static String jsonResponse(String summary, List<String> noteIds) {
        return jsonResponse(summary, "10 minutes", noteIds);
    }

    static String jsonResponse(String summary, String estimatedTime, List<String> noteIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"summary\":\"").append(summary).append("\"");
        if (estimatedTime != null) {
            sb.append(",\"estimatedTime\":\"").append(estimatedTime).append("\"");
        }
        sb.append(",\"route\":[");
        for (int i = 0; i < noteIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"noteId\":\"").append(noteIds.get(i))
              .append("\",\"reason\":\"reason ").append(i)
              .append("\",\"order\":").append(i).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Route step with no reason or order fields. */
    static String jsonWithBareStep(String noteId) {
        return "{\"summary\":\"s\",\"route\":[{\"noteId\":\"" + noteId + "\"}]}";
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private static final double[] UNIT_VEC = {1.0, 0.0, 0.0};

    private VectorDatabase db;
    private KnowledgeBase kb;
    private Note noteA, noteB, noteC;
    private FixedEmbeddingProvider embedProvider;

    @BeforeEach
    void setUp() {
        VectorDatabase.getInstance().clear();
        db = VectorDatabase.getInstance();

        kb = new KnowledgeBase(Path.of("/tmp/test"));

        noteA = new Note("id-a", "/notes/a.md", "alpha.md");
        noteB = new Note("id-b", "/notes/b.md", "beta.md");
        noteC = new Note("id-c", "/notes/c.md", "gamma.md");
        kb.addNote(noteA);
        kb.addNote(noteB);
        kb.addNote(noteC);

        db.add("id-a", UNIT_VEC);
        db.add("id-b", UNIT_VEC);
        db.add("id-c", UNIT_VEC);

        embedProvider = new FixedEmbeddingProvider(UNIT_VEC);
    }

    private RAGNavigator navigator(ChatProvider chat) {
        return new RAGNavigator(embedProvider, chat, kb, db);
    }

    // -----------------------------------------------------------------------
    // Constructor guards
    // -----------------------------------------------------------------------

    @Test
    void constructor_succeedsWithAllValidArgs() {
        assertDoesNotThrow(() -> new RAGNavigator(
                embedProvider, new FixedChatProvider(ChatResponse.failure("x")), kb, db));
    }

    @Test
    void constructorThrowsOnNullEmbeddingProvider() {
        assertThrows(IllegalArgumentException.class,
                () -> new RAGNavigator(null,
                        new FixedChatProvider(ChatResponse.failure("x")), kb, db));
    }

    @Test
    void constructorThrowsOnNullChatProvider() {
        assertThrows(IllegalArgumentException.class,
                () -> new RAGNavigator(embedProvider, null, kb, db));
    }

    @Test
    void constructorThrowsOnNullKnowledgeBase() {
        assertThrows(IllegalArgumentException.class,
                () -> new RAGNavigator(embedProvider,
                        new FixedChatProvider(ChatResponse.failure("x")), null, db));
    }

    @Test
    void constructorThrowsOnNullVectorDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> new RAGNavigator(embedProvider,
                        new FixedChatProvider(ChatResponse.failure("x")), kb, null));
    }

    // -----------------------------------------------------------------------
    // navigate() — happy path: result shape
    // -----------------------------------------------------------------------

    @Test
    void navigate_happyPath_returnsNonFallbackResult() throws AIServiceException {
        String json = jsonResponse("Learn from A to C", List.of("id-a", "id-b", "id-c"));
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(json, 50)))
                .navigate("machine learning", LearningStyle.LINEAR);

        assertFalse(result.isFallback());
        assertTrue(result.getSummary().startsWith("Learn from A to C"));
    }

    @Test
    void navigate_happyPath_routeIsNotNull() throws AIServiceException {
        String json = jsonResponse("s", List.of("id-a"));
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(json, 5)))
                .navigate("q", LearningStyle.LINEAR);

        assertNotNull(result.getRoute());
    }

    @Test
    void navigate_happyPath_routeStepCount() throws AIServiceException {
        String json = jsonResponse("s", List.of("id-a", "id-b"));
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(json, 10)))
                .navigate("q", LearningStyle.LINEAR);

        assertEquals(2, result.getRoute().size());
    }

    @Test
    void navigate_happyPath_routeStepsHaveCorrectNoteIds() throws AIServiceException {
        String json = jsonResponse("summary", List.of("id-b", "id-a"));
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(json, 20)))
                .navigate("topic", LearningStyle.OVERVIEW_FIRST);

        List<RouteStep> steps = result.getRoute();
        assertEquals("id-b", steps.get(0).getNoteId());
        assertEquals("id-a", steps.get(1).getNoteId());
    }

    @Test
    void navigate_happyPath_routeStepsHaveCorrectOrder() throws AIServiceException {
        String json = jsonResponse("s", List.of("id-a", "id-c"));
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(json, 10)))
                .navigate("q", LearningStyle.ASSOCIATIVE);

        assertEquals(0, result.getRoute().get(0).getOrder());
        assertEquals(1, result.getRoute().get(1).getOrder());
    }

    @Test
    void navigate_happyPath_routeStepsHaveCorrectReason() throws AIServiceException {
        String json = jsonResponse("s", List.of("id-a", "id-b"));
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(json, 10)))
                .navigate("q", LearningStyle.LINEAR);

        assertEquals("reason 0", result.getRoute().get(0).getReason());
        assertEquals("reason 1", result.getRoute().get(1).getReason());
    }

    @Test
    void navigate_singleNoteRoute_works() throws AIServiceException {
        String json = jsonResponse("one note", List.of("id-a"));
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(json, 5)))
                .navigate("q", LearningStyle.LINEAR);

        assertFalse(result.isFallback());
        assertEquals(1, result.getRoute().size());
        assertEquals("id-a", result.getRoute().get(0).getNoteId());
    }

    // -----------------------------------------------------------------------
    // navigate() — summary and estimatedTime parsing
    // -----------------------------------------------------------------------

    @Test
    void navigate_estimatedTime_appendedToSummary() throws AIServiceException {
        String json = jsonResponse("My summary", "25 minutes", List.of("id-a"));
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(json, 5)))
                .navigate("q", LearningStyle.LINEAR);

        assertTrue(result.getSummary().contains("25 minutes"),
                "estimatedTime must be appended to the summary");
    }

    @Test
    void navigate_missingEstimatedTime_summaryUnchanged() throws AIServiceException {
        // Build JSON without estimatedTime field
        String json = "{\"summary\":\"Clean summary\",\"route\":[" +
                "{\"noteId\":\"id-a\",\"reason\":\"r\",\"order\":0}]}";
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(json, 5)))
                .navigate("q", LearningStyle.LINEAR);

        assertEquals("Clean summary", result.getSummary(),
                "Summary must be unchanged when estimatedTime is absent");
    }

    @Test
    void navigate_nullEstimatedTime_summaryUnchanged() throws AIServiceException {
        String json = "{\"summary\":\"Clean summary\",\"estimatedTime\":null,\"route\":[" +
                "{\"noteId\":\"id-a\",\"reason\":\"r\",\"order\":0}]}";
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(json, 5)))
                .navigate("q", LearningStyle.LINEAR);

        assertEquals("Clean summary", result.getSummary());
    }

    @Test
    void navigate_missingSummaryField_usesDefaultSummary() throws AIServiceException {
        String json = "{\"route\":[{\"noteId\":\"id-a\",\"reason\":\"r\",\"order\":0}]}";
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(json, 5)))
                .navigate("q", LearningStyle.LINEAR);

        assertFalse(result.isFallback());
        assertNotNull(result.getSummary());
        assertFalse(result.getSummary().isBlank(),
                "A default summary must be used when the JSON has no summary field");
    }

    // -----------------------------------------------------------------------
    // navigate() — route step field defaults
    // -----------------------------------------------------------------------

    @Test
    void navigate_stepWithNoReasonField_reasonIsEmptyString() throws AIServiceException {
        String json = "{\"summary\":\"s\",\"route\":[{\"noteId\":\"id-a\",\"order\":0}]}";
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(json, 5)))
                .navigate("q", LearningStyle.LINEAR);

        assertEquals("", result.getRoute().get(0).getReason(),
                "Missing reason field must default to empty string");
    }

    @Test
    void navigate_stepWithNoOrderField_orderDefaultsToListPosition() throws AIServiceException {
        String json = "{\"summary\":\"s\",\"route\":[" +
                "{\"noteId\":\"id-a\",\"reason\":\"r\"}," +
                "{\"noteId\":\"id-b\",\"reason\":\"r\"}" +
                "]}";
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(json, 5)))
                .navigate("q", LearningStyle.LINEAR);

        assertEquals(0, result.getRoute().get(0).getOrder());
        assertEquals(1, result.getRoute().get(1).getOrder());
    }

    // -----------------------------------------------------------------------
    // navigate() — call counts and which ChatProvider method is used
    // -----------------------------------------------------------------------

    @Test
    void navigate_embeddingProviderCalledOnce() throws AIServiceException {
        String json = jsonResponse("s", List.of("id-a"));
        navigator(new FixedChatProvider(ChatResponse.success(json, 5)))
                .navigate("q", LearningStyle.LINEAR);

        assertEquals(1, embedProvider.embedCallCount);
    }

    @Test
    void navigate_chatProviderCalledOnce() throws AIServiceException {
        String json = jsonResponse("s", List.of("id-a"));
        FixedChatProvider chat = new FixedChatProvider(ChatResponse.success(json, 5));
        navigator(chat).navigate("q", LearningStyle.LINEAR);

        assertEquals(1, chat.callCount);
    }

    @Test
    void navigate_usesChatWithSystem_notPlainChat() throws AIServiceException {
        // Use a recording provider that counts chatWithSystem vs chat calls separately
        AtomicReference<String> capturedSystem = new AtomicReference<>();
        ChatProvider recording = new ChatProvider() {
            @Override public ChatResponse chat(String prompt) {
                fail("chat() must not be called — chatWithSystem() is required");
                return ChatResponse.failure("wrong");
            }
            @Override public ChatResponse chatWithSystem(String sys, String user) {
                capturedSystem.set(sys);
                return ChatResponse.success(jsonResponse("s", List.of("id-a")), 5);
            }
        };

        navigator(recording).navigate("q", LearningStyle.LINEAR);

        assertNotNull(capturedSystem.get(), "chatWithSystem must have been called");
    }

    @Test
    void navigate_systemPromptIsPromptBuilderConstant() throws AIServiceException {
        RecordingChatProvider recording = new RecordingChatProvider(
                ChatResponse.success(jsonResponse("s", List.of("id-a")), 5));

        navigator(recording).navigate("q", LearningStyle.LINEAR);

        assertEquals(PromptBuilder.SYSTEM_PROMPT, recording.capturedSystem.get(),
                "navigate must pass PromptBuilder.SYSTEM_PROMPT as the system prompt");
    }

    // -----------------------------------------------------------------------
    // navigate() — markdown fence stripping
    // -----------------------------------------------------------------------

    @Test
    void navigate_jsonWithMarkdownFences_parsedSuccessfully() throws AIServiceException {
        String inner = jsonResponse("fenced", List.of("id-a"));
        String fenced = "```json\n" + inner + "\n```";
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(fenced, 10)))
                .navigate("q", LearningStyle.LINEAR);

        assertFalse(result.isFallback());
        assertTrue(result.getSummary().startsWith("fenced"));
    }

    @Test
    void navigate_jsonWithPlainMarkdownFences_parsedSuccessfully() throws AIServiceException {
        String inner = jsonResponse("plain fenced", List.of("id-a"));
        String fenced = "```\n" + inner + "\n```";
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(fenced, 10)))
                .navigate("q", LearningStyle.LINEAR);

        assertFalse(result.isFallback());
        assertTrue(result.getSummary().startsWith("plain fenced"));
    }

    @Test
    void navigate_jsonWithLeadingAndTrailingWhitespace_parsedSuccessfully() throws AIServiceException {
        String json = "  \n  " + jsonResponse("whitespace padded", List.of("id-a")) + "\n  ";
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.success(json, 5)))
                .navigate("q", LearningStyle.LINEAR);

        assertFalse(result.isFallback());
        assertTrue(result.getSummary().startsWith("whitespace padded"));
    }

    // -----------------------------------------------------------------------
    // navigate() — fallback scenarios
    // -----------------------------------------------------------------------

    @Test
    void navigate_embeddingFails_returnsFallback() throws AIServiceException {
        RAGNavigator nav = new RAGNavigator(
                new FailingEmbeddingProvider(),
                new FixedChatProvider(ChatResponse.failure("x")), kb, db);

        assertTrue(nav.navigate("q", LearningStyle.LINEAR).isFallback());
    }

    @Test
    void navigate_embeddingFails_fallbackRouteIsEmpty() throws AIServiceException {
        // No topKIds are collected when embedding fails immediately
        RAGNavigator nav = new RAGNavigator(
                new FailingEmbeddingProvider(),
                new FixedChatProvider(ChatResponse.failure("x")), kb, db);

        NavigationResult result = nav.navigate("q", LearningStyle.LINEAR);

        assertTrue(result.isFallback());
        assertTrue(result.getRoute().isEmpty(),
                "When embedding fails, no topKIds exist — fallback route must be empty");
    }

    @Test
    void navigate_chatThrows_returnsFallback() throws AIServiceException {
        assertTrue(navigator(new FailingChatProvider())
                .navigate("q", LearningStyle.LINEAR).isFallback());
    }

    @Test
    void navigate_chatThrows_fallbackRouteContainsTopKNoteIds() throws AIServiceException {
        // topKIds are collected before chat fails — they should appear in fallback
        NavigationResult result = navigator(new FailingChatProvider())
                .navigate("q", LearningStyle.LINEAR);

        assertTrue(result.isFallback());
        assertFalse(result.getRoute().isEmpty(),
                "TopK ids collected before chat failure must populate fallback route");
    }

    @Test
    void navigate_chatReturnsFailureResponse_returnsFallback() throws AIServiceException {
        assertTrue(navigator(new FixedChatProvider(ChatResponse.failure("model error")))
                .navigate("q", LearningStyle.LINEAR).isFallback());
    }

    @Test
    void navigate_malformedJson_returnsFallback() throws AIServiceException {
        assertTrue(navigator(new FixedChatProvider(ChatResponse.success("not-json", 5)))
                .navigate("q", LearningStyle.LINEAR).isFallback());
    }

    @Test
    void navigate_malformedJson_fallbackRouteContainsTopKIds() throws AIServiceException {
        NavigationResult result = navigator(
                new FixedChatProvider(ChatResponse.success("not-json", 5)))
                .navigate("q", LearningStyle.LINEAR);

        assertFalse(result.getRoute().isEmpty(),
                "TopK ids must still populate the fallback route after JSON parse failure");
    }

    @Test
    void navigate_emptyRouteArray_returnsFallback() throws AIServiceException {
        String json = "{\"summary\":\"empty\",\"estimatedTime\":\"0 minutes\",\"route\":[]}";
        assertTrue(navigator(new FixedChatProvider(ChatResponse.success(json, 5)))
                .navigate("q", LearningStyle.LINEAR).isFallback());
    }

    @Test
    void navigate_missingRouteField_returnsFallback() throws AIServiceException {
        String json = "{\"summary\":\"no route field\",\"estimatedTime\":\"5 minutes\"}";
        assertTrue(navigator(new FixedChatProvider(ChatResponse.success(json, 5)))
                .navigate("q", LearningStyle.LINEAR).isFallback());
    }

    @Test
    void navigate_emptyVectorDatabase_returnsFallbackWithEmptyRoute() throws AIServiceException {
        db.clear();
        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.failure("x")))
                .navigate("q", LearningStyle.LINEAR);

        assertTrue(result.isFallback());
        assertTrue(result.getRoute().isEmpty());
    }

    @Test
    void navigate_allCandidatesOrphaned_returnsFallback() throws AIServiceException {
        // Vectors in DB with IDs that have no matching Note in KnowledgeBase
        db.clear();
        db.add("orphan-1", UNIT_VEC);
        db.add("orphan-2", UNIT_VEC);

        NavigationResult result = navigator(new FixedChatProvider(ChatResponse.failure("x")))
                .navigate("q", LearningStyle.LINEAR);

        assertTrue(result.isFallback(),
                "When every vector candidate is orphaned (no Note in KB), must fall back");
    }

    @Test
    void navigate_fallbackSummaryIsNotNull() throws AIServiceException {
        NavigationResult result = navigator(new FailingChatProvider())
                .navigate("q", LearningStyle.LINEAR);

        assertNotNull(result.getSummary());
    }

    @Test
    void navigate_fallbackSummaryIndicatesAiUnavailable() throws AIServiceException {
        NavigationResult result = navigator(new FailingChatProvider())
                .navigate("q", LearningStyle.LINEAR);

        assertTrue(result.getSummary().toLowerCase().contains("ai unavailable"),
                "Fallback summary must mention AI unavailability");
    }

    // -----------------------------------------------------------------------
    // navigate() — never-throws contract
    // -----------------------------------------------------------------------

    @Test
    void navigate_neverThrows_whenEmbeddingFails() {
        RAGNavigator nav = new RAGNavigator(
                new FailingEmbeddingProvider(),
                new FixedChatProvider(ChatResponse.failure("x")), kb, db);

        assertDoesNotThrow(() -> nav.navigate("q", LearningStyle.LINEAR),
                "navigate() must never propagate an exception — always returns a result");
    }

    @Test
    void navigate_neverThrows_whenChatFails() {
        assertDoesNotThrow(() -> navigator(new FailingChatProvider())
                .navigate("q", LearningStyle.LINEAR));
    }

    @Test
    void navigate_neverThrows_whenJsonMalformed() {
        assertDoesNotThrow(() -> navigator(
                new FixedChatProvider(ChatResponse.success("{bad json{{", 1)))
                .navigate("q", LearningStyle.LINEAR));
    }

    @Test
    void navigate_resultIsNeverNull() throws AIServiceException {
        assertNotNull(navigator(new FailingChatProvider()).navigate("q", LearningStyle.LINEAR));
    }

    // -----------------------------------------------------------------------
    // navigate() — all learning styles work
    // -----------------------------------------------------------------------

    @Test
    void navigate_allLearningStyles_returnResult() {
        String json = jsonResponse("s", List.of("id-a"));
        RAGNavigator nav = navigator(new FixedChatProvider(ChatResponse.success(json, 5)));

        for (LearningStyle style : LearningStyle.values()) {
            assertDoesNotThrow(() -> nav.navigate("q", style),
                    "navigate must work for style: " + style);
        }
    }

    // -----------------------------------------------------------------------
    // searchSimilar() — happy path
    // -----------------------------------------------------------------------

    @Test
    void searchSimilar_returnsNonEmptyList() {
        List<Note> results = navigator(new FixedChatProvider(ChatResponse.failure("x")))
                .searchSimilar("query", 3);

        assertFalse(results.isEmpty());
    }

    @Test
    void searchSimilar_allReturnedNotesAreNonNull() {
        List<Note> results = navigator(new FixedChatProvider(ChatResponse.failure("x")))
                .searchSimilar("query", 3);

        results.forEach(n -> assertNotNull(n, "Returned Note must not be null"));
    }

    @Test
    void searchSimilar_respectsTopKLimit() {
        // Add 10 extra notes so DB has 13 total
        for (int i = 0; i < 10; i++) {
            String id = "extra-" + i;
            kb.addNote(new Note(id, "/notes/" + id + ".md", id + ".md"));
            db.add(id, UNIT_VEC);
        }

        List<Note> results = navigator(new FixedChatProvider(ChatResponse.failure("x")))
                .searchSimilar("query", 2);

        assertTrue(results.size() <= 2,
                "Result count must not exceed topK");
    }

    @Test
    void searchSimilar_topK1_returnsAtMostOne() {
        List<Note> results = navigator(new FixedChatProvider(ChatResponse.failure("x")))
                .searchSimilar("query", 1);

        assertTrue(results.size() <= 1);
    }

    @Test
    void searchSimilar_topKLargerThanDatabase_returnsAllAvailable() {
        // DB has 3 notes; ask for 100
        List<Note> results = navigator(new FixedChatProvider(ChatResponse.failure("x")))
                .searchSimilar("query", 100);

        assertEquals(3, results.size(),
                "When topK > DB size, all indexed notes must be returned");
    }

    @Test
    void searchSimilar_emptyDatabase_returnsEmptyList() {
        db.clear();

        List<Note> results = navigator(new FixedChatProvider(ChatResponse.failure("x")))
                .searchSimilar("query", 5);

        assertTrue(results.isEmpty());
    }

    @Test
    void searchSimilar_orphanIdsSkipped() {
        db.add("orphan-id", UNIT_VEC);

        List<Note> results = navigator(new FixedChatProvider(ChatResponse.failure("x")))
                .searchSimilar("query", 10);

        boolean hasOrphan = results.stream().anyMatch(n -> "orphan-id".equals(n.getId()));
        assertFalse(hasOrphan, "IDs not in KnowledgeBase must be silently skipped");
    }

    // -----------------------------------------------------------------------
    // searchSimilar() — no LLM interaction
    // -----------------------------------------------------------------------

    @Test
    void searchSimilar_doesNotCallChatProvider() {
        FixedChatProvider chat = new FixedChatProvider(ChatResponse.failure("x"));
        navigator(chat).searchSimilar("query", 3);

        assertEquals(0, chat.callCount,
                "searchSimilar must never call the ChatProvider");
    }

    @Test
    void searchSimilar_calledTwice_embedCalledTwiceTotal() {
        RAGNavigator nav = navigator(new FixedChatProvider(ChatResponse.failure("x")));
        nav.searchSimilar("first", 3);
        nav.searchSimilar("second", 3);

        assertEquals(2, embedProvider.embedCallCount,
                "Each searchSimilar call must independently embed the query");
    }

    // -----------------------------------------------------------------------
    // searchSimilar() — error resilience
    // -----------------------------------------------------------------------

    @Test
    void searchSimilar_embeddingFails_returnsEmptyList() {
        RAGNavigator nav = new RAGNavigator(
                new FailingEmbeddingProvider(),
                new FixedChatProvider(ChatResponse.failure("x")), kb, db);

        List<Note> results = nav.searchSimilar("query", 5);

        assertTrue(results.isEmpty());
    }

    @Test
    void searchSimilar_neverThrows_whenEmbeddingFails() {
        RAGNavigator nav = new RAGNavigator(
                new FailingEmbeddingProvider(),
                new FixedChatProvider(ChatResponse.failure("x")), kb, db);

        assertDoesNotThrow(() -> nav.searchSimilar("query", 5),
                "searchSimilar must swallow exceptions and return an empty list");
    }

    @Test
    void searchSimilar_resultIsNeverNull() {
        List<Note> results = navigator(new FixedChatProvider(ChatResponse.failure("x")))
                .searchSimilar("query", 5);

        assertNotNull(results);
    }
}
