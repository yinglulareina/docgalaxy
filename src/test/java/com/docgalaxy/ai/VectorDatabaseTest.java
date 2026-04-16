package com.docgalaxy.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VectorDatabaseTest {

    private VectorDatabase db;

    @BeforeEach
    void setUp() {
        db = VectorDatabase.getInstance();
        db.clear();
    }

    // -----------------------------------------------------------------------
    // Singleton
    // -----------------------------------------------------------------------

    @Test
    void getInstance_returnsSameInstance() {
        assertSame(VectorDatabase.getInstance(), VectorDatabase.getInstance());
    }

    // -----------------------------------------------------------------------
    // add / get
    // -----------------------------------------------------------------------

    @Test
    void add_thenGet_returnsStoredVector() {
        double[] vec = {0.1, 0.2, 0.3};
        db.add("note1", vec);

        assertArrayEquals(vec, db.get("note1"), 1e-12);
    }

    @Test
    void add_storesDefensiveCopy_mutatingOriginalDoesNotAffectStore() {
        double[] original = {1.0, 2.0};
        db.add("n1", original);
        original[0] = 999.0;

        assertArrayEquals(new double[]{1.0, 2.0}, db.get("n1"), 1e-12);
    }

    @Test
    void add_replacesExistingEntry() {
        db.add("n1", new double[]{1.0});
        db.add("n1", new double[]{9.0});

        assertArrayEquals(new double[]{9.0}, db.get("n1"), 1e-12);
    }

    @Test
    void add_duplicateKey_doesNotIncrementSize() {
        db.add("n1", new double[]{1.0});
        db.add("n1", new double[]{2.0});

        assertEquals(1, db.size());
    }

    @Test
    void add_emptyStringKey_isAllowed() {
        db.add("", new double[]{1.0});

        assertArrayEquals(new double[]{1.0}, db.get(""), 1e-12);
        assertEquals(1, db.size());
    }

    @Test
    void add_throwsOnNullNoteId() {
        assertThrows(IllegalArgumentException.class, () -> db.add(null, new double[]{1.0}));
    }

    @Test
    void add_throwsOnNullVector() {
        assertThrows(IllegalArgumentException.class, () -> db.add("n1", null));
    }

    @Test
    void add_throwsOnNullNoteId_doesNotPartiallyModifyStore() {
        assertThrows(IllegalArgumentException.class, () -> db.add(null, new double[]{1.0}));
        assertEquals(0, db.size());
    }

    // -----------------------------------------------------------------------
    // get
    // -----------------------------------------------------------------------

    @Test
    void get_returnsNullForUnknownId() {
        assertNull(db.get("does-not-exist"));
    }

    @Test
    void get_returnsDefensiveCopy_mutationDoesNotAffectStore() {
        db.add("n1", new double[]{1.0, 2.0});
        double[] copy = db.get("n1");
        copy[0] = 999.0;

        assertArrayEquals(new double[]{1.0, 2.0}, db.get("n1"), 1e-12);
    }

    @Test
    void get_afterDelete_returnsNull() {
        db.add("n1", new double[]{1.0});
        db.delete("n1");

        assertNull(db.get("n1"));
    }

    // -----------------------------------------------------------------------
    // delete
    // -----------------------------------------------------------------------

    @Test
    void delete_removesEntry() {
        db.add("n1", new double[]{1.0});
        db.delete("n1");

        assertNull(db.get("n1"));
        assertEquals(0, db.size());
    }

    @Test
    void delete_nonExistentId_isNoOp() {
        assertDoesNotThrow(() -> db.delete("ghost"));
        assertEquals(0, db.size());
    }

    @Test
    void delete_nullId_isNoOp() {
        db.add("n1", new double[]{1.0});
        assertDoesNotThrow(() -> db.delete(null));
        assertEquals(1, db.size());
    }

    @Test
    void delete_onlyTargetEntryIsRemoved() {
        db.add("keep", new double[]{1.0});
        db.add("gone", new double[]{2.0});
        db.delete("gone");

        assertEquals(1, db.size());
        assertNotNull(db.get("keep"));
        assertNull(db.get("gone"));
    }

    // -----------------------------------------------------------------------
    // size
    // -----------------------------------------------------------------------

    @Test
    void size_emptyStore_isZero() {
        assertEquals(0, db.size());
    }

    @Test
    void size_reflectsAddAndDelete() {
        db.add("a", new double[]{1.0});
        db.add("b", new double[]{2.0});
        assertEquals(2, db.size());

        db.delete("a");
        assertEquals(1, db.size());
    }

    // -----------------------------------------------------------------------
    // clear
    // -----------------------------------------------------------------------

    @Test
    void clear_resetsSize() {
        db.add("a", new double[]{1.0});
        db.add("b", new double[]{2.0});
        db.clear();

        assertEquals(0, db.size());
    }

    @Test
    void clear_removesAllEntries_getReturnsNull() {
        db.add("a", new double[]{1.0});
        db.clear();

        assertNull(db.get("a"));
    }

    @Test
    void clear_thenAdd_works() {
        db.add("a", new double[]{1.0});
        db.clear();
        db.add("b", new double[]{2.0});

        assertEquals(1, db.size());
        assertArrayEquals(new double[]{2.0}, db.get("b"), 1e-12);
    }

    // -----------------------------------------------------------------------
    // getAll
    // -----------------------------------------------------------------------

    @Test
    void getAll_emptyStore_returnsEmptyMap() {
        assertTrue(db.getAll().isEmpty());
    }

    @Test
    void getAll_containsAllAddedEntries() {
        db.add("x", new double[]{1.0, 0.0});
        db.add("y", new double[]{0.0, 1.0});

        Map<String, double[]> all = db.getAll();
        assertEquals(2, all.size());
        assertTrue(all.containsKey("x"));
        assertTrue(all.containsKey("y"));
    }

    @Test
    void getAll_returnsUnmodifiableMap() {
        db.add("n1", new double[]{1.0});
        assertThrows(UnsupportedOperationException.class,
                () -> db.getAll().put("hack", new double[]{0.0}));
    }

    @Test
    void getAll_valuesAreDefensiveCopies() {
        db.add("n1", new double[]{1.0, 2.0});
        db.getAll().get("n1")[0] = 999.0;

        assertArrayEquals(new double[]{1.0, 2.0}, db.get("n1"), 1e-12);
    }

    @Test
    void getAll_isSnapshot_laterAddDoesNotAppear() {
        db.add("before", new double[]{1.0});
        Map<String, double[]> snapshot = db.getAll();

        db.add("after", new double[]{2.0});

        assertFalse(snapshot.containsKey("after"),
                "snapshot taken before the add should not reflect later mutations");
    }

    @Test
    void getAll_isSnapshot_laterDeleteDoesNotDisappear() {
        db.add("n1", new double[]{1.0});
        Map<String, double[]> snapshot = db.getAll();

        db.delete("n1");

        assertTrue(snapshot.containsKey("n1"),
                "snapshot taken before the delete should still contain the entry");
    }

    // -----------------------------------------------------------------------
    // searchTopK — argument validation
    // -----------------------------------------------------------------------

    @Test
    void searchTopK_throwsOnNullQuery() {
        assertThrows(IllegalArgumentException.class, () -> db.searchTopK(null, 5));
    }

    @Test
    void searchTopK_throwsOnKEqualsZero() {
        assertThrows(IllegalArgumentException.class,
                () -> db.searchTopK(new double[]{1.0}, 0));
    }

    @Test
    void searchTopK_throwsOnNegativeK() {
        assertThrows(IllegalArgumentException.class,
                () -> db.searchTopK(new double[]{1.0}, -1));
    }

    // -----------------------------------------------------------------------
    // searchTopK — happy path
    // -----------------------------------------------------------------------

    @Test
    void searchTopK_emptyStore_returnsEmptyList() {
        assertTrue(db.searchTopK(new double[]{1.0, 0.0}, 5).isEmpty());
    }

    @Test
    void searchTopK_singleEntry_returnsThatEntry() {
        db.add("only", new double[]{1.0, 0.0});
        List<Neighbor> result = db.searchTopK(new double[]{1.0, 0.0}, 1);

        assertEquals(1, result.size());
        assertEquals("only", result.get(0).getNoteId());
        assertEquals(1.0, result.get(0).getSimilarity(), 1e-9);
    }

    @Test
    void searchTopK_returnsCorrectOrder_highestSimilarityFirst() {
        db.add("a", new double[]{1.0, 0.0}); // sim = 1.0
        db.add("b", new double[]{0.0, 1.0}); // sim = 0.0
        db.add("c", new double[]{1.0, 1.0}); // sim ≈ 0.707

        List<Neighbor> result = db.searchTopK(new double[]{1.0, 0.0}, 3);

        assertEquals(3, result.size());
        assertEquals("a", result.get(0).getNoteId());
        assertEquals("c", result.get(1).getNoteId());
        assertEquals("b", result.get(2).getNoteId());
    }

    @Test
    void searchTopK_kEqualsSize_returnsAllEntries() {
        db.add("p", new double[]{1.0, 0.0});
        db.add("q", new double[]{0.0, 1.0});

        List<Neighbor> result = db.searchTopK(new double[]{1.0, 0.0}, 2); // k == size

        assertEquals(2, result.size());
    }

    @Test
    void searchTopK_kLargerThanSize_returnsAllEntries() {
        db.add("p", new double[]{1.0, 0.0});
        db.add("q", new double[]{0.0, 1.0});

        List<Neighbor> result = db.searchTopK(new double[]{1.0, 0.0}, 100);

        assertEquals(2, result.size());
    }

    @Test
    void searchTopK_kEqualsOne_returnsBestMatch() {
        db.add("best",  new double[]{1.0, 0.0}); // sim = 1.0
        db.add("worst", new double[]{0.0, 1.0}); // sim = 0.0

        List<Neighbor> result = db.searchTopK(new double[]{1.0, 0.0}, 1);

        assertEquals(1, result.size());
        assertEquals("best", result.get(0).getNoteId());
    }

    @Test
    void searchTopK_similarityValuesAreCorrect() {
        db.add("identical", new double[]{1.0, 0.0});
        db.add("opposite",  new double[]{-1.0, 0.0});

        List<Neighbor> result = db.searchTopK(new double[]{1.0, 0.0}, 2);

        assertEquals( 1.0, result.get(0).getSimilarity(), 1e-9);
        assertEquals(-1.0, result.get(1).getSimilarity(), 1e-9);
    }

    @Test
    void searchTopK_orthogonalVector_similarityIsZero() {
        db.add("orth", new double[]{0.0, 1.0});

        List<Neighbor> result = db.searchTopK(new double[]{1.0, 0.0}, 1);

        assertEquals(0.0, result.get(0).getSimilarity(), 1e-9);
    }

    @Test
    void searchTopK_zeroNormQuery_allSimilaritiesAreZero() {
        db.add("a", new double[]{1.0, 0.0});
        db.add("b", new double[]{0.0, 1.0});

        List<Neighbor> result = db.searchTopK(new double[]{0.0, 0.0}, 2);

        assertEquals(2, result.size());
        for (Neighbor n : result) {
            assertEquals(0.0, n.getSimilarity(), 1e-9);
        }
    }

    @Test
    void searchTopK_skipsDimensionMismatch() {
        db.add("dim2", new double[]{1.0, 0.0});       // 2-dim — skipped
        db.add("dim3", new double[]{1.0, 0.0, 0.0});  // 3-dim — matches query

        List<Neighbor> result = db.searchTopK(new double[]{1.0, 0.0, 0.0}, 5);

        assertEquals(1, result.size());
        assertEquals("dim3", result.get(0).getNoteId());
    }

    @Test
    void searchTopK_allDimensionMismatch_returnsEmpty() {
        db.add("dim2a", new double[]{1.0, 0.0});
        db.add("dim2b", new double[]{0.0, 1.0});

        List<Neighbor> result = db.searchTopK(new double[]{1.0, 0.0, 0.0}, 5); // query is 3-dim

        assertTrue(result.isEmpty());
    }

    @Test
    void searchTopK_afterDelete_deletedEntryAbsent() {
        db.add("stay",   new double[]{1.0, 0.0});
        db.add("remove", new double[]{0.9, 0.1});
        db.delete("remove");

        List<Neighbor> result = db.searchTopK(new double[]{1.0, 0.0}, 5);

        assertEquals(1, result.size());
        assertEquals("stay", result.get(0).getNoteId());
    }

    @Test
    void searchTopK_returnsUnmodifiableList() {
        db.add("n1", new double[]{1.0, 0.0});
        List<Neighbor> result = db.searchTopK(new double[]{1.0, 0.0}, 1);

        assertThrows(UnsupportedOperationException.class,
                () -> result.add(new Neighbor("hack", 0.0)));
    }

    @Test
    void searchTopK_largeStore_returnsCorrectTopK() {
        // Insert 50 entries; note "winner" is most similar
        for (int i = 0; i < 50; i++) {
            db.add("note" + i, new double[]{i * 0.01, 1.0});
        }
        db.add("winner", new double[]{1.0, 0.0});

        List<Neighbor> result = db.searchTopK(new double[]{1.0, 0.0}, 3);

        assertEquals("winner", result.get(0).getNoteId());
        assertEquals(3, result.size());
    }

    // -----------------------------------------------------------------------
    // Thread safety
    // -----------------------------------------------------------------------

    @Test
    void concurrentAdds_allPersist() throws InterruptedException {
        int threads = 8;
        int perThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    db.add("t" + tid + "-" + i, new double[]{tid, i});
                }
                latch.countDown();
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(threads * perThread, db.size());
    }

    @Test
    void concurrentReads_doNotThrow() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            db.add("n" + i, new double[]{i, i + 1.0});
        }

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 50; i++) {
                        db.get("n" + (i % 20));
                        db.size();
                        db.getAll();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(0, errors.get());
    }

    @Test
    void concurrentAddsAndSearches_searchNeverThrows() throws InterruptedException {
        // Pre-populate so searches have something to scan
        for (int i = 0; i < 20; i++) {
            db.add("seed" + i, new double[]{i * 0.05, 1.0 - i * 0.05});
        }

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);
        double[] query = {1.0, 0.0};

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 30; i++) {
                        if (tid % 2 == 0) {
                            db.add("dyn-t" + tid + "-" + i, new double[]{i * 0.01, 0.5});
                        } else {
                            db.searchTopK(query, 5);
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(0, errors.get());
    }

    @Test
    void concurrentAddsAndDeletes_sizeNeverNegative() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            db.add("n" + i, new double[]{i});
        }

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Integer> sizes = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            pool.submit(() -> {
                for (int i = 0; i < 50; i++) {
                    if (tid % 2 == 0) {
                        db.add("new-t" + tid + "-" + i, new double[]{i});
                    } else {
                        db.delete("n" + i);
                    }
                    synchronized (sizes) {
                        sizes.add(db.size());
                    }
                }
                latch.countDown();
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        assertTrue(sizes.stream().allMatch(s -> s >= 0),
                "size() must never return a negative value");
    }
}
