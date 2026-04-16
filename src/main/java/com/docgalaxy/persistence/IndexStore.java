package com.docgalaxy.persistence;

import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.model.Note;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;

/**
 * Reads and writes index.json using Gson.
 * Uses atomic write + backup from AbstractStore.
 * Defensive deserialization: missing fields receive safe defaults.
 */
public class IndexStore extends AbstractStore<NoteIndex> {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create();

    /**
     * @param storeDir  the .docgalaxy directory for this knowledge base
     */
    public IndexStore(Path storeDir) {
        super(storeDir.resolve("index.json"),
              storeDir.resolve("backup/index.json.bak"));
    }

    // ----------------------------------------------------------------
    // AbstractStore contract
    // ----------------------------------------------------------------

    @Override
    public NoteIndex load() throws IOException {
        if (!Files.exists(filePath)) {
            return emptyIndex();
        }
        String json = Files.readString(filePath, StandardCharsets.UTF_8);
        NoteIndex index = GSON.fromJson(json, NoteIndex.class);
        return sanitize(index);
    }

    @Override
    public void save(NoteIndex data) throws IOException {
        data.setLastUpdated(Instant.now().toString());
        String json = GSON.toJson(data);
        Files.createDirectories(filePath.getParent());
        if (backupPath != null) {
            Files.createDirectories(backupPath.getParent());
        }
        atomicWrite(filePath, json.getBytes(StandardCharsets.UTF_8));
    }

    // ----------------------------------------------------------------
    // Convenience helpers used by KnowledgeBaseManager
    // ----------------------------------------------------------------

    /**
     * Serialise the entire KnowledgeBase into a NoteIndex and save it.
     */
    public void saveKnowledgeBase(KnowledgeBase kb) throws IOException {
        NoteIndex index = new NoteIndex();
        for (Note note : kb.getAllNotes()) {
            index.getNotes().add(NoteIndex.NoteRecord.fromNote(note));
        }
        save(index);
    }

    /**
     * Load index.json (with fallback to backup) and populate the KnowledgeBase.
     * Silently skips corrupt entries.
     */
    public void populateKnowledgeBase(KnowledgeBase kb) {
        NoteIndex index = loadWithFallback();
        if (index == null) return;
        for (NoteIndex.NoteRecord record : index.getNotes()) {
            try {
                kb.addNote(record.toNote());
            } catch (Exception ignored) {
                // Skip malformed records – don't crash startup
            }
        }
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private static NoteIndex emptyIndex() {
        NoteIndex idx = new NoteIndex();
        idx.setLastUpdated(Instant.now().toString());
        return idx;
    }

    /** Ensure no null lists that would cause NPEs downstream. */
    private static NoteIndex sanitize(NoteIndex index) {
        if (index == null) return emptyIndex();
        if (index.getNotes() == null) index.setNotes(new ArrayList<>());
        return index;
    }
}
