package com.docgalaxy.persistence;

import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.model.Note;
import com.docgalaxy.model.NoteStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndexStoreTest {

    @TempDir
    Path tempDir;

    private IndexStore store;

    @BeforeEach
    void setUp() {
        store = new IndexStore(tempDir);
    }

    // ----------------------------------------------------------------
    // load() on missing file → empty index, no exception
    // ----------------------------------------------------------------
    @Test
    void load_missingFile_returnsEmptyIndex() throws IOException {
        NoteIndex index = store.load();
        assertNotNull(index);
        assertNotNull(index.getNotes());
        assertTrue(index.getNotes().isEmpty());
    }

    // ----------------------------------------------------------------
    // save() then load() round-trip
    // ----------------------------------------------------------------
    @Test
    void saveAndLoad_roundTrip() throws IOException {
        NoteIndex index = new NoteIndex();
        Note note = new Note("notes/test.md", "test.md");
        note.setContentHash("sha256:abc123");
        note.setFileSize(512L);
        note.setLastModified(1_000_000L);
        note.setEmbeddingOffset(3);
        note.setStatus(NoteStatus.ACTIVE);
        note.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        index.getNotes().add(NoteIndex.NoteRecord.fromNote(note));
        store.save(index);

        NoteIndex loaded = store.load();
        assertEquals(1, loaded.getNotes().size());

        NoteIndex.NoteRecord rec = loaded.getNotes().get(0);
        assertEquals(note.getId(),          rec.getId());
        assertEquals("notes/test.md",       rec.getFilePath());
        assertEquals("test.md",             rec.getFileName());
        assertEquals("sha256:abc123",       rec.getContentHash());
        assertEquals(512L,                  rec.getFileSize());
        assertEquals(1_000_000L,            rec.getLastModified());
        assertEquals(3,                     rec.getEmbeddingOffset());
        assertEquals("ACTIVE",              rec.getStatus());
    }

    // ----------------------------------------------------------------
    // Multiple notes survive round-trip
    // ----------------------------------------------------------------
    @Test
    void saveAndLoad_multipleNotes() throws IOException {
        NoteIndex index = new NoteIndex();
        for (int i = 0; i < 5; i++) {
            Note n = new Note("notes/note" + i + ".md", "note" + i + ".md");
            n.setStatus(NoteStatus.ACTIVE);
            index.getNotes().add(NoteIndex.NoteRecord.fromNote(n));
        }
        store.save(index);

        NoteIndex loaded = store.load();
        assertEquals(5, loaded.getNotes().size());
    }

    // ----------------------------------------------------------------
    // saveKnowledgeBase + populateKnowledgeBase
    // ----------------------------------------------------------------
    @Test
    void saveAndPopulateKnowledgeBase_roundTrip() throws IOException {
        Path kbRoot = tempDir.resolve("kb");
        kbRoot.toFile().mkdirs();
        KnowledgeBase kb = new KnowledgeBase(kbRoot);

        Note a = new Note("a.md", "a.md");
        a.setStatus(NoteStatus.ACTIVE);
        Note b = new Note("b.md", "b.md");
        b.setStatus(NoteStatus.ORPHANED);
        kb.addNote(a);
        kb.addNote(b);

        store.saveKnowledgeBase(kb);

        KnowledgeBase kb2 = new KnowledgeBase(kbRoot);
        store.populateKnowledgeBase(kb2);

        assertEquals(2, kb2.getNoteCount());
        assertNotNull(kb2.getNote(a.getId()));
        assertNotNull(kb2.getNote(b.getId()));
        assertEquals(NoteStatus.ORPHANED, kb2.getNote(b.getId()).getStatus());
    }

    // ----------------------------------------------------------------
    // Atomic write creates backup
    // ----------------------------------------------------------------
    @Test
    void save_createsBackupOnSecondWrite() throws IOException {
        NoteIndex first = new NoteIndex();
        store.save(first);                    // no backup yet (no existing file to copy)

        NoteIndex second = new NoteIndex();
        second.getNotes().add(NoteIndex.NoteRecord.fromNote(
            new Note("x.md", "x.md")));
        store.save(second);                   // should create backup

        Path backup = tempDir.resolve("backup/index.json.bak");
        assertTrue(backup.toFile().exists(), "Backup file should exist after second write");
    }

    // ----------------------------------------------------------------
    // Defensive deserialization: ORPHANED status preserved
    // ----------------------------------------------------------------
    @Test
    void noteRecord_toNote_orphanedStatus() {
        NoteIndex.NoteRecord rec = new NoteIndex.NoteRecord();
        rec.setId("some-id");
        rec.setFilePath("x.md");
        rec.setFileName("x.md");
        rec.setStatus("ORPHANED");
        rec.setCreatedAt(Instant.now().toString());

        Note note = rec.toNote();
        assertEquals(NoteStatus.ORPHANED, note.getStatus());
    }

    // ----------------------------------------------------------------
    // Defensive deserialization: unknown status → ACTIVE
    // ----------------------------------------------------------------
    @Test
    void noteRecord_toNote_unknownStatus_defaultsToActive() {
        NoteIndex.NoteRecord rec = new NoteIndex.NoteRecord();
        rec.setId("id");
        rec.setFilePath("y.md");
        rec.setFileName("y.md");
        rec.setStatus("BOGUS_STATUS");

        Note note = rec.toNote();
        assertEquals(NoteStatus.ACTIVE, note.getStatus());
    }
}
