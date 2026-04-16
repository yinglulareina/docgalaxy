package com.docgalaxy.ai.navigator;

import com.docgalaxy.ai.AIServiceException;
import com.docgalaxy.ai.ChatProvider;
import com.docgalaxy.ai.ChatResponse;
import com.docgalaxy.ai.EmbeddingProvider;
import com.docgalaxy.ai.Neighbor;
import com.docgalaxy.ai.VectorDatabase;
import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.model.Note;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RAG-based implementation of {@link NavigatorService}.
 *
 * <p>{@link #navigate} embeds the query, retrieves the top-20 semantically
 * similar notes, asks the LLM to arrange them into a learning route, and
 * parses the JSON response into a {@link NavigationResult}.  Any failure at
 * any stage returns {@link NavigationResult#fallback} so the UI always gets
 * a usable result.
 *
 * <p>{@link #searchSimilar} performs only the embed + vector search step —
 * no LLM call is made.
 */
public class RAGNavigator implements NavigatorService {

    private static final Logger LOG = Logger.getLogger(RAGNavigator.class.getName());
    private static final int TOP_K_NAVIGATE = 20;

    private final EmbeddingProvider embeddingProvider;
    private final ChatProvider chatProvider;
    private final KnowledgeBase knowledgeBase;
    private final VectorDatabase vectorDatabase;
    private final Gson gson;

    /**
     * @param embeddingProvider used to embed queries
     * @param chatProvider      used for the LLM route-planning call
     * @param knowledgeBase     source of {@link Note} metadata by id
     * @param vectorDatabase    source of embeddings for similarity search
     */
    public RAGNavigator(EmbeddingProvider embeddingProvider,
                        ChatProvider chatProvider,
                        KnowledgeBase knowledgeBase,
                        VectorDatabase vectorDatabase) {
        if (embeddingProvider == null) throw new IllegalArgumentException("embeddingProvider must not be null");
        if (chatProvider == null)      throw new IllegalArgumentException("chatProvider must not be null");
        if (knowledgeBase == null)     throw new IllegalArgumentException("knowledgeBase must not be null");
        if (vectorDatabase == null)    throw new IllegalArgumentException("vectorDatabase must not be null");
        this.embeddingProvider = embeddingProvider;
        this.chatProvider = chatProvider;
        this.knowledgeBase = knowledgeBase;
        this.vectorDatabase = vectorDatabase;
        this.gson = new Gson();
    }

    /**
     * Plans a learning route for {@code query} using RAG + LLM.
     *
     * <ol>
     *   <li>Embeds the query via {@link EmbeddingProvider}.</li>
     *   <li>Retrieves up to 20 candidate notes from {@link VectorDatabase}.</li>
     *   <li>Builds a prompt via {@link PromptBuilder} and calls {@link ChatProvider}.</li>
     *   <li>Parses the JSON response into {@link NavigationResult}.</li>
     * </ol>
     *
     * On any exception the method returns {@link NavigationResult#fallback} using
     * the top-K note ids collected before the failure.
     *
     * @param query the user's navigation query (non-null, non-blank)
     * @param style the preferred learning style
     * @return a {@link NavigationResult} — never null, never throws
     * @throws AIServiceException never thrown; all errors are captured as fallback
     */
    @Override
    public NavigationResult navigate(String query, LearningStyle style) throws AIServiceException {
        List<String> topKIds = new ArrayList<>();
        try {
            double[] queryVector = embeddingProvider.embed(query);
            List<Neighbor> neighbors = vectorDatabase.searchTopK(queryVector, TOP_K_NAVIGATE);

            for (Neighbor n : neighbors) {
                topKIds.add(n.getNoteId());
            }

            List<Note> candidates = resolveNotes(topKIds);
            if (candidates.isEmpty()) {
                return NavigationResult.fallback(topKIds);
            }

            String userPrompt = PromptBuilder.buildRoutePrompt(query, candidates, style);
            ChatResponse response = chatProvider.chatWithSystem(
                    PromptBuilder.SYSTEM_PROMPT, userPrompt);

            if (!response.isSuccess()) {
                LOG.warning("ChatProvider returned failure: " + response.getErrorMessage());
                return NavigationResult.fallback(topKIds);
            }

            return parseNavigationResult(response.getContent(), topKIds);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "RAGNavigator.navigate failed, using fallback", e);
            return NavigationResult.fallback(topKIds);
        }
    }

    /**
     * Returns the top-{@code topK} notes most semantically similar to {@code query}.
     * No LLM call is made.
     *
     * @param query the search query
     * @param topK  maximum number of results to return
     * @return list of matching {@link Note} objects (may be shorter than {@code topK}
     *         if fewer notes are indexed), or an empty list on any error
     */
    @Override
    public List<Note> searchSimilar(String query, int topK) {
        try {
            double[] queryVector = embeddingProvider.embed(query);
            List<Neighbor> neighbors = vectorDatabase.searchTopK(queryVector, topK);
            List<String> ids = neighbors.stream().map(Neighbor::getNoteId).toList();
            return resolveNotes(ids);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "RAGNavigator.searchSimilar failed", e);
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Maps note IDs to Note objects, silently dropping IDs not found in KnowledgeBase. */
    private List<Note> resolveNotes(List<String> ids) {
        List<Note> notes = new ArrayList<>(ids.size());
        for (String id : ids) {
            Note note = knowledgeBase.getNote(id);
            if (note != null) {
                notes.add(note);
            }
        }
        return notes;
    }

    /**
     * Parses the LLM JSON response into a {@link NavigationResult}.
     * Falls back to {@link NavigationResult#fallback} if the JSON is malformed.
     */
    private NavigationResult parseNavigationResult(String json, List<String> fallbackIds) {
        try {
            // Strip potential markdown code fences if the model wraps its output
            String cleaned = json.trim();
            if (cleaned.startsWith("```")) {
                int start = cleaned.indexOf('{');
                int end = cleaned.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    cleaned = cleaned.substring(start, end + 1);
                }
            }

            JsonObject root = gson.fromJson(cleaned, JsonObject.class);
            String summary = root.has("summary") ? root.get("summary").getAsString()
                                                 : "Learning route generated by AI.";
            if (root.has("estimatedTime") && !root.get("estimatedTime").isJsonNull()) {
                summary = summary + " (Estimated time: " + root.get("estimatedTime").getAsString() + ")";
            }

            JsonArray routeArray = root.getAsJsonArray("route");
            List<RouteStep> route = new ArrayList<>();
            if (routeArray != null) {
                for (JsonElement el : routeArray) {
                    JsonObject step = el.getAsJsonObject();
                    String noteId = step.get("noteId").getAsString();
                    String reason = step.has("reason") ? step.get("reason").getAsString() : "";
                    int order = step.has("order") ? step.get("order").getAsInt() : route.size();
                    route.add(new RouteStep(noteId, reason, order));
                }
            }

            if (route.isEmpty()) {
                return NavigationResult.fallback(fallbackIds);
            }
            return new NavigationResult(route, summary, false);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse LLM navigation response, using fallback", e);
            return NavigationResult.fallback(fallbackIds);
        }
    }
}
