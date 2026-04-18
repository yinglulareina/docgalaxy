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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

            return parseNavigationResult(response.getContent(), candidates, topKIds);

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
     * If the JSON is truncated, attempts to salvage complete route steps via regex
     * before falling back to the top-K list.
     */
    private NavigationResult parseNavigationResult(String json, List<Note> candidates, List<String> fallbackIds) {
        String cleaned = cleanJson(json);

        // First attempt: full parse
        try {
            return parseFullJson(cleaned, candidates, fallbackIds);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Full JSON parse failed, attempting truncation recovery", e);
        }

        // Second attempt: salvage complete route steps from a truncated response
        try {
            return recoverTruncated(cleaned, candidates, fallbackIds);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Recovery failed, using fallback", e);
            return NavigationResult.fallback(fallbackIds);
        }
    }

    /**
     * Resolves the noteId returned by the LLM to a real note UUID.
     * The LLM sometimes returns the 1-based numeric index shown in the prompt
     * (e.g. "3") instead of the UUID. In that case, look up the real id from
     * the candidates list. If the string is already a UUID-style id, return it
     * as-is.
     */
    private static String resolveNoteId(String raw, List<Note> candidates) {
        try {
            int index = Integer.parseInt(raw.trim());
            if (index >= 1 && index <= candidates.size()) {
                return candidates.get(index - 1).getId();
            }
        } catch (NumberFormatException ignored) {
            // Not a number — assume it is already a UUID
        }
        return raw;
    }

    /** Strips markdown code fences and locates the outermost JSON object. */
    private static String cleanJson(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('{');
            int end   = s.lastIndexOf('}');
            if (start >= 0 && end > start) return s.substring(start, end + 1);
        }
        return s;
    }

    /** Parses a complete, well-formed JSON object into a NavigationResult. */
    private NavigationResult parseFullJson(String cleaned, List<Note> candidates, List<String> fallbackIds) {
        JsonObject root = gson.fromJson(cleaned, JsonObject.class);
        String summary = root.has("summary") ? root.get("summary").getAsString()
                                             : "Learning route generated by AI.";
        String estimatedTime = (root.has("estimatedTime") && !root.get("estimatedTime").isJsonNull())
                ? root.get("estimatedTime").getAsString() : null;

        JsonArray routeArray = root.getAsJsonArray("route");
        List<RouteStep> route = new ArrayList<>();
        if (routeArray != null) {
            for (JsonElement el : routeArray) {
                JsonObject step = el.getAsJsonObject();
                String noteId = resolveNoteId(step.get("noteId").getAsString(), candidates);
                String reason = step.has("reason") ? step.get("reason").getAsString() : "";
                int order = step.has("order") ? step.get("order").getAsInt() : route.size();
                route.add(new RouteStep(noteId, reason, order));
            }
        }

        if (route.isEmpty()) return NavigationResult.fallback(fallbackIds);
        return new NavigationResult(route, summary, estimatedTime, false);
    }

    /**
     * Extracts every complete {@code {...}} object that contains a {@code "noteId"}
     * field.  Used when the LLM response was truncated mid-stream.
     */
    private NavigationResult recoverTruncated(String raw, List<Note> candidates, List<String> fallbackIds) {
        Pattern stepPattern = Pattern.compile("\\{[^{}]*\"noteId\"[^{}]*\\}");
        Matcher matcher = stepPattern.matcher(raw);

        List<RouteStep> route = new ArrayList<>();
        while (matcher.find()) {
            try {
                JsonObject step = gson.fromJson(matcher.group(), JsonObject.class);
                String noteId = resolveNoteId(step.get("noteId").getAsString(), candidates);
                String reason = step.has("reason") ? step.get("reason").getAsString() : "";
                int order = step.has("order") ? step.get("order").getAsInt() : route.size();
                route.add(new RouteStep(noteId, reason, order));
            } catch (Exception ignored) {}
        }

        if (route.isEmpty()) throw new IllegalStateException("No complete route steps found");
        return new NavigationResult(route, "Learning route (partial response).", null, false);
    }
}
