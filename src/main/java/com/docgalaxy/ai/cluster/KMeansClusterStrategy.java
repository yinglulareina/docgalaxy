package com.docgalaxy.ai.cluster;

import com.docgalaxy.model.Vector2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Classic K-Means clustering over 2-D positions.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Select {@code k} initial centroids by random sampling (without replacement).</li>
 *   <li>Assign each point to its nearest centroid using {@link Vector2D#distanceTo}.</li>
 *   <li>Recompute each centroid as the mean of its assigned points.</li>
 *   <li>Repeat until assignments stop changing or {@code maxIterations} is reached.</li>
 * </ol>
 *
 * <p>If {@code k} exceeds the number of points each point becomes its own cluster.
 * Empty clusters (no points assigned) are dropped from the result.
 *
 * <p>This class is stateless between calls; {@link #cluster} is thread-safe as long
 * as the caller does not share the input lists across concurrent invocations.
 */
public class KMeansClusterStrategy implements ClusterStrategy {

    private static final int MAX_ITERATIONS = 100;

    private final int k;
    private final long seed;

    /**
     * @param k number of clusters; must be &ge; 1
     */
    public KMeansClusterStrategy(int k) {
        this(k, System.nanoTime());
    }

    /**
     * Package-private constructor for deterministic tests.
     *
     * @param k    number of clusters
     * @param seed RNG seed used for centroid initialisation
     */
    KMeansClusterStrategy(int k, long seed) {
        if (k < 1) throw new IllegalArgumentException("k must be >= 1");
        this.k    = k;
        this.seed = seed;
    }

    /**
     * Clusters the supplied 2-D points.
     *
     * @param points  2-D positions (one per note); must not be null
     * @param noteIds note identifiers parallel to {@code points}; must not be null
     *                and must have the same size
     * @return list of {@link Cluster} objects (no dendrogram); may contain fewer
     *         than {@code k} entries if empty clusters are dropped
     * @throws IllegalArgumentException if the lists are null or have different sizes
     */
    @Override
    public List<Cluster> cluster(List<Vector2D> points, List<String> noteIds) {
        if (points == null)  throw new IllegalArgumentException("points must not be null");
        if (noteIds == null) throw new IllegalArgumentException("noteIds must not be null");
        if (points.size() != noteIds.size()) {
            throw new IllegalArgumentException(
                    "points and noteIds must have the same size");
        }

        int n = points.size();
        if (n == 0) return Collections.emptyList();

        // Clamp k to the actual number of points
        int actualK = Math.min(k, n);

        // --- 1. Initialise centroids by random sampling ---
        List<Vector2D> centroids = initCentroids(points, actualK);

        int[] assignments = new int[n];

        // --- 2 & 3. Assign → recompute loop ---
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            boolean changed = assign(points, centroids, assignments);
            recompute(points, assignments, centroids, actualK);
            if (!changed) break;
        }

        // --- 4. Build result ---
        return buildClusters(points, noteIds, assignments, centroids, actualK);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Samples {@code count} distinct points without replacement as initial centroids. */
    private List<Vector2D> initCentroids(List<Vector2D> points, int count) {
        List<Integer> indices = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) indices.add(i);
        Collections.shuffle(indices, new Random(seed));

        List<Vector2D> centroids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            centroids.add(points.get(indices.get(i)));
        }
        return centroids;
    }

    /**
     * Assigns each point to the nearest centroid.
     *
     * @return {@code true} if any assignment changed
     */
    private static boolean assign(List<Vector2D> points, List<Vector2D> centroids,
                                   int[] assignments) {
        boolean changed = false;
        for (int i = 0; i < points.size(); i++) {
            int best     = 0;
            double bestD = points.get(i).distanceTo(centroids.get(0));
            for (int c = 1; c < centroids.size(); c++) {
                double d = points.get(i).distanceTo(centroids.get(c));
                if (d < bestD) { bestD = d; best = c; }
            }
            if (assignments[i] != best) { assignments[i] = best; changed = true; }
        }
        return changed;
    }

    /**
     * Recomputes each centroid as the mean of its assigned points.
     * Clusters with no assigned points retain their previous centroid.
     */
    private static void recompute(List<Vector2D> points, int[] assignments,
                                   List<Vector2D> centroids, int k) {
        double[] sumX   = new double[k];
        double[] sumY   = new double[k];
        int[]    counts = new int[k];

        for (int i = 0; i < points.size(); i++) {
            int c = assignments[i];
            sumX[c]   += points.get(i).getX();
            sumY[c]   += points.get(i).getY();
            counts[c] += 1;
        }
        for (int c = 0; c < k; c++) {
            if (counts[c] > 0) {
                centroids.set(c, new Vector2D(sumX[c] / counts[c], sumY[c] / counts[c]));
            }
            // else: keep previous centroid (empty cluster stays put)
        }
    }

    /**
     * Packages the final assignments into {@link Cluster} objects, dropping any
     * clusters that have no members.
     */
    private static List<Cluster> buildClusters(List<Vector2D> points, List<String> noteIds,
                                                int[] assignments, List<Vector2D> centroids,
                                                int k) {
        @SuppressWarnings("unchecked")
        List<String>[] members = new List[k];
        for (int c = 0; c < k; c++) members[c] = new ArrayList<>();
        for (int i = 0; i < noteIds.size(); i++) members[assignments[i]].add(noteIds.get(i));

        List<Cluster> result = new ArrayList<>();
        for (int c = 0; c < k; c++) {
            if (!members[c].isEmpty()) {
                result.add(new Cluster(centroids.get(c),
                                       Collections.unmodifiableList(members[c]),
                                       null));
            }
        }
        return Collections.unmodifiableList(result);
    }
}
