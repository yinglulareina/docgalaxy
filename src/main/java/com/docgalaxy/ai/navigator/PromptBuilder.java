package com.docgalaxy.ai.navigator;

import com.docgalaxy.model.Note;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for building LLM prompts used by the RAG navigation pipeline.
 *
 * <p>The LLM is instructed to return a strict JSON object so the caller can
 * parse it deterministically.  Expected response shape:
 * <pre>
 * {
 *   "summary": "...",
 *   "estimatedTime": "...",
 *   "route": [
 *     { "noteId": "...", "reason": "...", "order": 0 }
 *   ]
 * }
 * </pre>
 */
final class PromptBuilder {

    private static final Logger LOG = Logger.getLogger(PromptBuilder.class.getName());

    /** Maximum characters of note content included in the prompt as a preview. */
    static final int SNIPPET_LENGTH = 200;

    static final String SYSTEM_PROMPT =
            "You are a knowledge navigation assistant. " +
            "Given a query, a learning style, and a list of candidate notes (each with a title " +
            "and a short content preview), select the most relevant notes and arrange them into " +
            "a personalised learning route. " +
            "Respond ONLY with valid JSON in this exact format (no markdown, no extra text):\n" +
            "{\n" +
            "  \"summary\": \"<one-sentence description of the learning route>\",\n" +
            "  \"estimatedTime\": \"<human-readable estimate, e.g. '20 minutes'>\",\n" +
            "  \"route\": [\n" +
            "    { \"noteId\": \"<id>\", \"reason\": \"<why this note fits here>\", \"order\": <integer starting at 0> }\n" +
            "  ]\n" +
            "}";

    private PromptBuilder() {}

    /**
     * Builds the user prompt for the route-planning LLM call.
     *
     * <p>Each note is presented with its title (file name) and up to
     * {@value #SNIPPET_LENGTH} characters of content read from disk.
     * If the file cannot be read the snippet is omitted gracefully.
     *
     * @param query    the user's navigation query
     * @param relevant notes retrieved from the vector database, most-similar first
     * @param style    the preferred learning style
     * @return formatted user prompt string ready to send to the LLM
     */
    static String buildRoutePrompt(String query, List<Note> relevant, LearningStyle style) {
        StringBuilder sb = new StringBuilder();
        sb.append("Query: ").append(query).append("\n\n");
        sb.append("Learning style: ").append(style.name())
          .append(" — ").append(style.getDescription()).append("\n\n");
        sb.append("Candidate notes (most-similar first):\n");

        for (int i = 0; i < relevant.size(); i++) {
            Note note = relevant.get(i);
            sb.append(i + 1).append(". Title: ").append(note.getFileName())
              .append("  [id=").append(note.getId()).append("]\n");
            String snippet = readSnippet(note);
            if (!snippet.isEmpty()) {
                sb.append("   Preview: ").append(snippet).append("\n");
            }
        }

        sb.append("\nSelect the best subset and order them to match the learning style. ");
        sb.append("Return ONLY the JSON object described in your instructions.");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Reads up to {@value #SNIPPET_LENGTH} characters from the note's file on disk.
     * Returns an empty string on any I/O error so callers are never blocked by
     * missing or unreadable files.
     */
    private static String readSnippet(Note note) {
        if (note.getFilePath() == null) return "";
        try {
            String content = Files.readString(Path.of(note.getFilePath()));
            String trimmed = content.strip();
            return trimmed.length() <= SNIPPET_LENGTH
                    ? trimmed
                    : trimmed.substring(0, SNIPPET_LENGTH);
        } catch (IOException e) {
            LOG.log(Level.FINE, "Could not read note file for prompt snippet: {0}", note.getFilePath());
            return "";
        }
    }
}
