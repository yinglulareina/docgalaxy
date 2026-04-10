package com.docgalaxy.ai;

import com.docgalaxy.util.VectorMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory vector store used throughout the application.
 *
 * <p>Thread-safe singleton backed by a {@link ConcurrentHashMap}.  All
 * mutations are safe to call from any thread; {@link #searchTopK} takes a
 * snapshot of the map entries before scanning so the map can be mutated
 * concurrently without affecting an in-progress search.
 *
 * <p>Usage: {@code VectorDatabase.getInstance()}.
 */
public final class VectorDatabase {

    private static volatile VectorDatabase instance;

    private final ConcurrentHashMap<String, double[]> store = new ConcurrentHashMap<>();

    private VectorDatabase() {}

    /**
     * Returns the application-wide singleton, creating it lazily on first call.
     * Double-checked locking ensures at most one instance is ever created.
     */
    public static VectorDatabase getInstance() {
        if (instance == null) {
            synchronized (VectorDatabase.class) {
                if (instance == null) {
                    instance = new VectorDatabase();
                }
            }
        }
        return instance;
    }

    /**
     * Stores (or replaces) the embedding vector for {@code noteId}.
     *
     * @param noteId non-null note identifier
     * @param vector non-null embedding vector (defensive copy is stored)
     * @throws IllegalArgumentException if either argument is null
     */
    public void add(String noteId, double[] vector) {
        if (noteId == null) throw new IllegalArgumentException("noteId must not be null");
        if (vector == null) throw new IllegalArgumentException("vector must not be null");
        store.put(noteId, vector.clone());
    }

    /**
     * Removes the entry for {@code noteId}.  No-op if the id is not present.
     *
     * @param noteId note identifier to remove
     */
    public void delete(String noteId) {
        if (noteId == null) return;
        store.remove(noteId);
    }

    /**
     * Returns the stored vector for {@code noteId}, or {@code null} if absent.
     * The returned array is a defensive copy.
     */
    public double[] get(String noteId) {
        double[] v = store.get(noteId);
        return (v == null) ? null : v.clone();
    }

    /**
     * Returns an unmodifiable snapshot of all (noteId → vector) entries.
     * Each value array is a defensive copy.
     */
    public Map<String, double[]> getAll() {
        ConcurrentHashMap<String, double[]> snapshot = new ConcurrentHashMap<>(store.size());
        store.forEach((id, vec) -> snapshot.put(id, vec.clone()));
        return Collections.unmodifiableMap(snapshot);
    }

    /** Returns the number of stored vectors. */
    public int size() {
        return store.size();
    }

    /**
     * Brute-force cosine-similarity scan over all stored vectors.
     *
     * <p>Takes a snapshot of the current entries so concurrent mutations do not
     * affect this scan.  Vectors whose dimension differs from {@code query} are
     * silently skipped.
     *
     * @param query the query embedding
     * @param k     maximum number of results; clamped to {@code size()} if larger
     * @return up to {@code k} {@link Neighbor} objects sorted by similarity descending
     * @throws IllegalArgumentException if {@code query} is null or {@code k} &lt; 1
     */
    public List<Neighbor> searchTopK(double[] query, int k) {
        if (query == null) throw new IllegalArgumentException("query vector must not be null");
        if (k < 1)         throw new IllegalArgumentException("k must be >= 1");

        List<Neighbor> results = new ArrayList<>(store.size());
        // Snapshot: iterate over a copy of the entry set so concurrent adds/removes
        // don't affect this scan.
        for (Map.Entry<String, double[]> entry : store.entrySet()) {
            double[] vec = entry.getValue();
            if (vec.length != query.length) continue; // skip dimension mismatch
            double sim = VectorMath.cosineSimilarity(query, vec);
            results.add(new Neighbor(entry.getKey(), sim));
        }

        results.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        return Collections.unmodifiableList(results.subList(0, Math.min(k, results.size())));
    }

    /**
     * Clears all stored vectors.  Primarily useful for testing.
     */
    public void clear() {
        store.clear();
    }
}
