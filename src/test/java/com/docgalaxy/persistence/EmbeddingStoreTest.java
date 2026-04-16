package com.docgalaxy.persistence;

import com.docgalaxy.util.VectorMath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingStoreTest {

    @TempDir
    Path tempDir;

    private static final int DIM = 128;   // smaller than 1536 for fast tests
    private EmbeddingStore store;

    @BeforeEach
    void setUp() {
        store = new EmbeddingStore(tempDir, DIM);
    }

    // ----------------------------------------------------------------
    // load() on missing file → empty map
    // ----------------------------------------------------------------
    @Test
    void load_missingFile_returnsEmptyMap() throws IOException {
        Map<String, double[]> result = store.load();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ----------------------------------------------------------------
    // save() then load() round-trip – single vector
    // ----------------------------------------------------------------
    @Test
    void saveAndLoad_singleVector_roundTrip() throws IOException {
        double[] original = randomVector(DIM);
        Map<String, double[]> data = new HashMap<>();
        data.put("note-001", original);

        store.save(data);
        Map<String, double[]> loaded = store.load();

        assertEquals(1, loaded.size());
        assertTrue(loaded.containsKey("note-001"));

        double[] restored = loaded.get("note-001");
        assertEquals(DIM, restored.length);
        assertArrayEquals(original, restored, 1e-12);
    }

    // ----------------------------------------------------------------
    // save() then load() – multiple vectors, all preserved
    // ----------------------------------------------------------------
    @Test
    void saveAndLoad_multipleVectors_allPreserved() throws IOException {
        Map<String, double[]> data = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            data.put("note-" + i, randomVector(DIM));
        }

        store.save(data);
        Map<String, double[]> loaded = store.load();

        assertEquals(10, loaded.size());
        for (Map.Entry<String, double[]> entry : data.entrySet()) {
            assertTrue(loaded.containsKey(entry.getKey()));
            assertArrayEquals(entry.getValue(), loaded.get(entry.getKey()), 1e-12);
        }
    }

    // ----------------------------------------------------------------
    // Cosine similarity is preserved after round-trip
    // ----------------------------------------------------------------
    @Test
    void saveAndLoad_cosineSimilarityPreserved() throws IOException {
        double[] vecA = randomVector(DIM);
        double[] vecB = randomVector(DIM);

        double simBefore = VectorMath.cosineSimilarity(vecA, vecB);

        Map<String, double[]> data = new HashMap<>();
        data.put("a", vecA);
        data.put("b", vecB);
        store.save(data);

        Map<String, double[]> loaded = store.load();
        double simAfter = VectorMath.cosineSimilarity(loaded.get("a"), loaded.get("b"));

        assertEquals(simBefore, simAfter, 1e-12);
    }

    // ----------------------------------------------------------------
    // Dimension mismatch on load → IOException
    // ----------------------------------------------------------------
    @Test
    void load_dimensionMismatch_throwsIOException() throws IOException {
        // Save with DIM=128
        Map<String, double[]> data = new HashMap<>();
        data.put("note-x", randomVector(DIM));
        store.save(data);

        // Load with wrong dimension
        EmbeddingStore wrongDimStore = new EmbeddingStore(tempDir, 256);
        assertThrows(IOException.class, wrongDimStore::load);
    }

    // ----------------------------------------------------------------
    // readStoredDimension() without loading all vectors
    // ----------------------------------------------------------------
    @Test
    void readStoredDimension_returnsCorrectDimension() throws IOException {
        Map<String, double[]> data = new HashMap<>();
        data.put("note-y", randomVector(DIM));
        store.save(data);

        assertEquals(DIM, store.readStoredDimension());
    }

    @Test
    void readStoredDimension_missingFile_returnsMinusOne() {
        assertEquals(-1, store.readStoredDimension());
    }

    // ----------------------------------------------------------------
    // Empty map save then load
    // ----------------------------------------------------------------
    @Test
    void saveAndLoad_emptyMap() throws IOException {
        store.save(new HashMap<>());
        Map<String, double[]> loaded = store.load();
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    // ----------------------------------------------------------------
    // Backup file created on second save
    // ----------------------------------------------------------------
    @Test
    void save_createsBackupOnSecondSave() throws IOException {
        store.save(new HashMap<>());

        Map<String, double[]> second = new HashMap<>();
        second.put("x", randomVector(DIM));
        store.save(second);

        Path backup = tempDir.resolve("backup/embeddings.bin.bak");
        assertTrue(backup.toFile().exists(), "Backup should exist after second save");
    }

    // ----------------------------------------------------------------
    // Helper
    // ----------------------------------------------------------------
    private static double[] randomVector(int dim) {
        Random rng = new Random(42);
        double[] v = new double[dim];
        for (int i = 0; i < dim; i++) v[i] = rng.nextGaussian();
        return v;
    }
}
