package com.docgalaxy.ai.navigator;

import com.docgalaxy.model.Note;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link PromptBuilder}.
 *
 * <p>PromptBuilder is package-private so this test lives in the same package.
 *
 * <p>Coverage areas:
 * <ul>
 *   <li>SYSTEM_PROMPT constant — wording, JSON schema fields, no-markdown instruction</li>
 *   <li>buildRoutePrompt — query placement and label</li>
 *   <li>buildRoutePrompt — learning style (name + description, all enum values)</li>
 *   <li>buildRoutePrompt — section ordering (style before notes, query near start)</li>
 *   <li>buildRoutePrompt — note titles, ids, numbering, ordering</li>
 *   <li>buildRoutePrompt — content snippets: happy path, truncation boundaries,
 *       whitespace stripping, missing file, null filePath, empty file</li>
 *   <li>buildRoutePrompt — edge cases: empty list, single note, large list, special chars</li>
 * </ul>
 */
class PromptBuilderTest {

    @TempDir
    Path tempDir;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Note noteWithFile(String id, String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return new Note(id, file.toString(), fileName);
    }

    /** Note whose filePath points to a non-existent path. */
    private Note noteWithNoFile(String id, String fileName) {
        return new Note(id, "/nonexistent/path/" + fileName, fileName);
    }

    /** Note with a null filePath. */
    private Note noteWithNullPath(String id, String fileName) {
        return new Note(id, null, fileName);
    }

    // -----------------------------------------------------------------------
    // SYSTEM_PROMPT — existence and basic contract
    // -----------------------------------------------------------------------

    @Test
    void systemPrompt_isNotNull() {
        assertNotNull(PromptBuilder.SYSTEM_PROMPT);
    }

    @Test
    void systemPrompt_isNotBlank() {
        assertFalse(PromptBuilder.SYSTEM_PROMPT.isBlank());
    }

    @Test
    void systemPrompt_startsWithExpectedIntro() {
        assertTrue(PromptBuilder.SYSTEM_PROMPT.startsWith(
                "You are a knowledge navigation assistant."),
                "System prompt must open with the exact required sentence");
    }

    @Test
    void systemPrompt_mentionsJsonFormat() {
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("JSON"),
                "System prompt must reference JSON response format");
    }

    @Test
    void systemPrompt_instructsNoMarkdown() {
        String lc = PromptBuilder.SYSTEM_PROMPT.toLowerCase();
        assertTrue(lc.contains("no markdown") || lc.contains("no extra text"),
                "System prompt must tell the model not to wrap JSON in markdown");
    }

    @Test
    void systemPrompt_containsSummaryField() {
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("summary"));
    }

    @Test
    void systemPrompt_containsEstimatedTimeField() {
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("estimatedTime"));
    }

    @Test
    void systemPrompt_containsRouteField() {
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("route"));
    }

    @Test
    void systemPrompt_containsNoteIdField() {
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("noteId"));
    }

    @Test
    void systemPrompt_containsReasonField() {
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("reason"));
    }

    @Test
    void systemPrompt_containsOrderField() {
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("order"));
    }

    @Test
    void systemPrompt_orderStartsAtZero() {
        // The schema example must show order starting at 0
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("0"),
                "Schema example should show order starting at 0");
    }

    // -----------------------------------------------------------------------
    // SNIPPET_LENGTH constant
    // -----------------------------------------------------------------------

    @Test
    void snippetLength_isPositive() {
        assertTrue(PromptBuilder.SNIPPET_LENGTH > 0);
    }

    @Test
    void snippetLength_is200() {
        assertEquals(200, PromptBuilder.SNIPPET_LENGTH);
    }

    // -----------------------------------------------------------------------
    // buildRoutePrompt — query
    // -----------------------------------------------------------------------

    @Test
    void buildRoutePrompt_containsQuery() {
        Note n = noteWithNoFile("id-1", "note.md");
        String prompt = PromptBuilder.buildRoutePrompt(
                "machine learning basics", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("machine learning basics"),
                "Prompt must contain the exact query string");
    }

    @Test
    void buildRoutePrompt_queryLabelPresent() {
        Note n = noteWithNoFile("id-1", "note.md");
        String prompt = PromptBuilder.buildRoutePrompt("my query", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("Query:"),
                "Prompt must include a 'Query:' label");
    }

    @Test
    void buildRoutePrompt_queryAppearsBeforeNotesList() {
        Note n = noteWithNoFile("id-x", "x.md");
        String prompt = PromptBuilder.buildRoutePrompt("THE_QUERY", List.of(n), LearningStyle.LINEAR);

        int queryIdx = prompt.indexOf("THE_QUERY");
        int noteIdx  = prompt.indexOf("id-x");
        assertTrue(queryIdx < noteIdx,
                "Query must appear in the prompt before the notes list");
    }

    @Test
    void buildRoutePrompt_queryAppearsBeforeLearningStyleSection() {
        Note n = noteWithNoFile("id-1", "note.md");
        String prompt = PromptBuilder.buildRoutePrompt(
                "QUERY_MARKER", List.of(n), LearningStyle.LINEAR);

        int queryIdx = prompt.indexOf("QUERY_MARKER");
        int styleIdx = prompt.indexOf("Learning style:");
        assertTrue(queryIdx < styleIdx,
                "Query must appear before the learning style section");
    }

    // -----------------------------------------------------------------------
    // buildRoutePrompt — learning style
    // -----------------------------------------------------------------------

    @Test
    void buildRoutePrompt_containsLearningStyleName_linear() {
        Note n = noteWithNoFile("id-1", "note.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("LINEAR"));
    }

    @Test
    void buildRoutePrompt_containsLearningStyleName_overviewFirst() {
        Note n = noteWithNoFile("id-1", "note.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.OVERVIEW_FIRST);

        assertTrue(prompt.contains("OVERVIEW_FIRST"));
    }

    @Test
    void buildRoutePrompt_containsLearningStyleName_associative() {
        Note n = noteWithNoFile("id-1", "note.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.ASSOCIATIVE);

        assertTrue(prompt.contains("ASSOCIATIVE"));
    }

    @Test
    void buildRoutePrompt_containsLearningStyleDescription_allValues() {
        Note n = noteWithNoFile("id-1", "note.md");
        for (LearningStyle style : LearningStyle.values()) {
            String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), style);
            assertTrue(prompt.contains(style.getDescription()),
                    "Description missing for style: " + style);
        }
    }

    @Test
    void buildRoutePrompt_learningStyleLabelPresent() {
        Note n = noteWithNoFile("id-1", "note.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("Learning style:"),
                "Prompt must include a 'Learning style:' label");
    }

    @Test
    void buildRoutePrompt_learningStyleAppearsBeforeNotesList() {
        Note n = noteWithNoFile("id-x", "x.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        int styleIdx = prompt.indexOf("Learning style:");
        int noteIdx  = prompt.indexOf("id-x");
        assertTrue(styleIdx < noteIdx,
                "Learning style section must appear before the notes list");
    }

    // -----------------------------------------------------------------------
    // buildRoutePrompt — note titles, ids, structure labels
    // -----------------------------------------------------------------------

    @Test
    void buildRoutePrompt_containsNoteFileName() {
        Note n = noteWithNoFile("id-42", "neural-networks.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("neural-networks.md"),
                "Prompt must include the note file name as its title");
    }

    @Test
    void buildRoutePrompt_containsNoteId() {
        Note n = noteWithNoFile("abc-123", "note.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("abc-123"),
                "Prompt must include the note id");
    }

    @Test
    void buildRoutePrompt_titleLabelPresent() {
        Note n = noteWithNoFile("id-1", "note.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("Title:"),
                "Each note entry must be introduced with a 'Title:' label");
    }

    @Test
    void buildRoutePrompt_candidateNotesSectionHeaderPresent() {
        Note n = noteWithNoFile("id-1", "note.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("Candidate notes"),
                "Prompt must include a 'Candidate notes' section header");
    }

    @Test
    void buildRoutePrompt_singleNote_numberedOne() {
        Note n = noteWithNoFile("id-1", "solo.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("1."),
                "Single note must be numbered '1.'");
    }

    @Test
    void buildRoutePrompt_multipleNotes_allTitlesPresent() {
        List<Note> notes = List.of(
                noteWithNoFile("id-a", "alpha.md"),
                noteWithNoFile("id-b", "beta.md"),
                noteWithNoFile("id-c", "gamma.md"));
        String prompt = PromptBuilder.buildRoutePrompt("q", notes, LearningStyle.LINEAR);

        assertTrue(prompt.contains("alpha.md"));
        assertTrue(prompt.contains("beta.md"));
        assertTrue(prompt.contains("gamma.md"));
    }

    @Test
    void buildRoutePrompt_multipleNotes_allIdsPresent() {
        List<Note> notes = List.of(
                noteWithNoFile("id-a", "alpha.md"),
                noteWithNoFile("id-b", "beta.md"));
        String prompt = PromptBuilder.buildRoutePrompt("q", notes, LearningStyle.LINEAR);

        assertTrue(prompt.contains("id-a"));
        assertTrue(prompt.contains("id-b"));
    }

    @Test
    void buildRoutePrompt_multipleNotes_numberedSequentially() {
        List<Note> notes = List.of(
                noteWithNoFile("id-a", "first.md"),
                noteWithNoFile("id-b", "second.md"),
                noteWithNoFile("id-c", "third.md"));
        String prompt = PromptBuilder.buildRoutePrompt("q", notes, LearningStyle.LINEAR);

        assertTrue(prompt.contains("1."), "First note must be numbered 1");
        assertTrue(prompt.contains("2."), "Second note must be numbered 2");
        assertTrue(prompt.contains("3."), "Third note must be numbered 3");
    }

    @Test
    void buildRoutePrompt_multipleNotes_preservesInputOrder() {
        Note first  = noteWithNoFile("id-first",  "AAAA.md");
        Note second = noteWithNoFile("id-second", "ZZZZ.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(first, second), LearningStyle.LINEAR);

        int idxFirst  = prompt.indexOf("AAAA.md");
        int idxSecond = prompt.indexOf("ZZZZ.md");
        assertTrue(idxFirst < idxSecond,
                "Notes must appear in the same order they were provided");
    }

    @Test
    void buildRoutePrompt_twentyNotes_allTitlesPresent() {
        List<Note> notes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            notes.add(noteWithNoFile("id-" + i, "note-" + i + ".md"));
        }
        String prompt = PromptBuilder.buildRoutePrompt("q", notes, LearningStyle.OVERVIEW_FIRST);

        for (int i = 0; i < 20; i++) {
            assertTrue(prompt.contains("note-" + i + ".md"),
                    "Missing title for note " + i);
        }
    }

    @Test
    void buildRoutePrompt_noteWithSpecialCharactersInFileName_appearsInPrompt() {
        Note n = noteWithNoFile("id-1", "intro & overview (v2).md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("intro & overview (v2).md"));
    }

    // -----------------------------------------------------------------------
    // buildRoutePrompt — content snippets: happy path
    // -----------------------------------------------------------------------

    @Test
    void buildRoutePrompt_existingFile_includesPreviewLabel() throws IOException {
        Note n = noteWithFile("id-1", "note.md", "Some content here.");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("Preview:"),
                "Prompt must include a 'Preview:' label for readable files");
    }

    @Test
    void buildRoutePrompt_existingFile_snippetContentPresent() throws IOException {
        Note n = noteWithFile("id-1", "note.md", "This is important content about AI.");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("This is important content about AI."));
    }

    @Test
    void buildRoutePrompt_shortContent_notTruncated() throws IOException {
        String content = "Short note.";
        Note n = noteWithFile("id-1", "short.md", content);
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains(content));
    }

    @Test
    void buildRoutePrompt_exactlySnippetLength_notTruncated() throws IOException {
        String content = "B".repeat(PromptBuilder.SNIPPET_LENGTH);
        Note n = noteWithFile("id-1", "exact.md", content);
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains(content),
                "Content of exactly SNIPPET_LENGTH chars must appear in full");
    }

    // -----------------------------------------------------------------------
    // buildRoutePrompt — content snippets: truncation boundary
    // -----------------------------------------------------------------------

    @Test
    void buildRoutePrompt_contentOneCharOverLimit_truncatedToSnippetLength() throws IOException {
        String content = "C".repeat(PromptBuilder.SNIPPET_LENGTH + 1);
        Note n = noteWithFile("id-1", "oneOver.md", content);
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("C".repeat(PromptBuilder.SNIPPET_LENGTH)),
                "First SNIPPET_LENGTH chars must be present");
        assertFalse(prompt.contains(content),
                "Full content must NOT appear — it is one char over the limit");
    }

    @Test
    void buildRoutePrompt_longContent_truncatedAtSnippetLength() throws IOException {
        String longContent = "A".repeat(500);
        Note n = noteWithFile("id-1", "long.md", longContent);
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertFalse(prompt.contains(longContent),
                "Full 500-char content must not appear in prompt");
        assertTrue(prompt.contains("A".repeat(PromptBuilder.SNIPPET_LENGTH)),
                "First " + PromptBuilder.SNIPPET_LENGTH + " chars must appear");
    }

    // -----------------------------------------------------------------------
    // buildRoutePrompt — content snippets: whitespace stripping
    // -----------------------------------------------------------------------

    @Test
    void buildRoutePrompt_contentWithLeadingWhitespace_stripped() throws IOException {
        Note n = noteWithFile("id-1", "ws.md", "   \n  Actual content");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("Actual content"),
                "Leading whitespace must be stripped before including snippet");
    }

    @Test
    void buildRoutePrompt_contentWithTrailingWhitespace_stripped() throws IOException {
        Note n = noteWithFile("id-1", "ws.md", "Actual content   \n  ");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("Actual content"),
                "Trailing whitespace must be stripped from snippet");
    }

    @Test
    void buildRoutePrompt_whitespaceOnlyFileContent_noPreviewLabel() throws IOException {
        Note n = noteWithFile("id-1", "blank.md", "   \n\t  \n   ");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertFalse(prompt.contains("Preview:"),
                "Whitespace-only content strips to empty — no Preview line should appear");
    }

    @Test
    void buildRoutePrompt_emptyFileContent_noPreviewLabel() throws IOException {
        Note n = noteWithFile("id-1", "empty.md", "");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertFalse(prompt.contains("Preview:"),
                "Empty file must not produce a Preview line");
    }

    // -----------------------------------------------------------------------
    // buildRoutePrompt — content snippets: error resilience
    // -----------------------------------------------------------------------

    @Test
    void buildRoutePrompt_missingFile_noExceptionThrown() {
        Note n = noteWithNoFile("id-missing", "missing.md");
        assertDoesNotThrow(() ->
                PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR));
    }

    @Test
    void buildRoutePrompt_missingFile_stillContainsTitle() {
        Note n = noteWithNoFile("id-missing", "missing.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("missing.md"),
                "Title must appear even when the file cannot be read");
    }

    @Test
    void buildRoutePrompt_missingFile_noPreviewLabel() {
        Note n = noteWithNoFile("id-missing", "missing.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertFalse(prompt.contains("Preview:"),
                "No Preview label must appear when the file is missing");
    }

    @Test
    void buildRoutePrompt_nullFilePath_noExceptionThrown() {
        Note n = noteWithNullPath("id-null", "null-path.md");
        assertDoesNotThrow(() ->
                PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR));
    }

    @Test
    void buildRoutePrompt_nullFilePath_stillContainsFileName() {
        Note n = noteWithNullPath("id-null", "null-path.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.contains("null-path.md"));
    }

    @Test
    void buildRoutePrompt_nullFilePath_noPreviewLabel() {
        Note n = noteWithNullPath("id-null", "null-path.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertFalse(prompt.contains("Preview:"),
                "Null filePath must not produce a Preview line");
    }

    @Test
    void buildRoutePrompt_mixedReadableAndMissingNotes_eachHandledIndependently()
            throws IOException {
        Note readable = noteWithFile("id-r", "readable.md", "Some real content.");
        Note missing  = noteWithNoFile("id-m", "missing.md");

        String prompt = PromptBuilder.buildRoutePrompt(
                "q", List.of(readable, missing), LearningStyle.LINEAR);

        assertTrue(prompt.contains("readable.md"),  "Readable note title must appear");
        assertTrue(prompt.contains("missing.md"),   "Missing note title must still appear");
        assertTrue(prompt.contains("Some real content."), "Readable note snippet must appear");
        // "Preview:" appears exactly once (only for the readable note)
        int count = 0;
        int idx = -1;
        while ((idx = prompt.indexOf("Preview:", idx + 1)) != -1) count++;
        assertEquals(1, count, "Only the readable note should produce a Preview line");
    }

    // -----------------------------------------------------------------------
    // buildRoutePrompt — closing instruction
    // -----------------------------------------------------------------------

    @Test
    void buildRoutePrompt_instructsReturnJsonOnly() {
        Note n = noteWithNoFile("id-1", "note.md");
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(n), LearningStyle.LINEAR);

        assertTrue(prompt.toLowerCase().contains("json"),
                "Prompt must remind the model to return only JSON");
    }

    // -----------------------------------------------------------------------
    // buildRoutePrompt — empty note list
    // -----------------------------------------------------------------------

    @Test
    void buildRoutePrompt_emptyNoteList_doesNotThrow() {
        assertDoesNotThrow(() ->
                PromptBuilder.buildRoutePrompt("q", List.of(), LearningStyle.LINEAR));
    }

    @Test
    void buildRoutePrompt_emptyNoteList_stillContainsQuery() {
        String prompt = PromptBuilder.buildRoutePrompt(
                "some query", List.of(), LearningStyle.LINEAR);

        assertTrue(prompt.contains("some query"));
    }

    @Test
    void buildRoutePrompt_emptyNoteList_noPreviewLabel() {
        String prompt = PromptBuilder.buildRoutePrompt("q", List.of(), LearningStyle.LINEAR);

        assertFalse(prompt.contains("Preview:"),
                "Empty list must produce no Preview lines");
    }
}
