package com.docgalaxy.ai.cluster;

import com.docgalaxy.model.DendrogramNode;
import com.docgalaxy.model.Vector2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Two-phase clustering strategy that combines K-Means (coarse) with agglomerative
 * hierarchical clustering (fine, within each coarse group).
 *
 * <h3>Phase 1 — K-Means coarse grouping</h3>
 * <p>Computes {@code K = max(3, ⌈√n⌉)} and delegates to {@link KMeansClusterStrategy}.
 * K is automatically clamped by K-Means when fewer than K points are present.
 *
 * <h3>Phase 2 — Agglomerative HAC within each coarse group</h3>
 * <p>Within each K-Means bucket, runs bottom-up agglomerative clustering using
 * centroid linkage: at every step the two micro-clusters whose centroids are
 * closest are merged into a {@link DendrogramNode}.  The final root node is
 * attached to the corresponding {@link Cluster}.
 *
 * <p>The returned {@link Cluster} objects carry the K-Means centroid, the full
 * member list, and the HAC {@link DendrogramNode} tree.
 */
public class HybridClusterStrategy implements ClusterStrategy {

    private final long seed;

    /** Production constructor — uses a time-based seed. */
    public HybridClusterStrategy() {
        this(System.nanoTime());
    }

    /** Package-private deterministic constructor for tests. */
    HybridClusterStrategy(long seed) {
        this.seed = seed;
    }

    /**
     * Clusters the given 2-D points via K-Means followed by per-group HAC.
     *
     * @param points  2-D positions parallel to {@code noteIds}; must not be null
     * @param noteIds note identifiers; must not be null and same length as {@code points}
     * @return unmodifiable list of {@link Cluster} objects, each with a non-null
     *         {@link DendrogramNode} root
     * @throws IllegalArgumentException if either list is null or the sizes differ
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

        // --- Phase 1: K-Means coarse grouping ---
        int k = Math.max(3, (int) Math.ceil(Math.sqrt(n)));
        List<Cluster> coarse = new KMeansClusterStrategy(k, seed).cluster(points, noteIds);

        // Build a lookup so we can retrieve the Vector2D for a note id in O(1)
        Map<String, Vector2D> pointById = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) pointById.put(noteIds.get(i), points.get(i));

        // --- Phase 2: HAC within each coarse cluster ---
        List<Cluster> result = new ArrayList<>(coarse.size());
        for (Cluster coarseCluster : coarse) {
            List<String>   memberIds  = coarseCluster.getMemberNoteIds();
            List<Vector2D> memberPts  = new ArrayList<>(memberIds.size());
            for (String id : memberIds) memberPts.add(pointById.get(id));

            DendrogramNode tree = buildDendrogram(memberIds, memberPts);
            result.add(new Cluster(coarseCluster.getCentroid(),
                                   Collections.unmodifiableList(new ArrayList<>(memberIds)),
                                   tree));
        }
        return Collections.unmodifiableList(result);
    }

    // -------------------------------------------------------------------------
    // Agglomerative HAC with centroid linkage
    // -------------------------------------------------------------------------

    /**
     * Builds a full binary dendrogram over the given points via bottom-up
     * agglomerative clustering with centroid linkage.
     *
     * <p>Complexity: O(m² · log m) — acceptable for the small per-K-Means-bucket
     * sizes that arise in practice (typically √n points per bucket).
     *
     * @param ids leaf identifiers, parallel to {@code pts}
     * @param pts 2-D positions of the leaves
     * @return root {@link DendrogramNode} of the complete dendrogram
     */
    static DendrogramNode buildDendrogram(List<String> ids, List<Vector2D> pts) {
        int m = ids.size();
        if (m == 0) throw new IllegalArgumentException("ids must not be empty");
        if (m == 1) return DendrogramNode.leaf(ids.get(0));

        // Mutable working lists: nodes, their current centroids, and member counts
        // (counts are used for weighted centroid re-computation after merges)
        List<DendrogramNode> nodes     = new ArrayList<>(m);
        List<Vector2D>       centroids = new ArrayList<>(m);
        List<Integer>        counts    = new ArrayList<>(m);

        for (int i = 0; i < m; i++) {
            nodes.add(DendrogramNode.leaf(ids.get(i)));
            centroids.add(pts.get(i));
            counts.add(1);
        }

        while (nodes.size() > 1) {
            // Find the pair of clusters with the smallest centroid distance
            int    bestI = 0, bestJ = 1;
            double bestD = centroids.get(0).distanceTo(centroids.get(1));

            int sz = centroids.size();
            for (int i = 0; i < sz; i++) {
                for (int j = i + 1; j < sz; j++) {
                    double d = centroids.get(i).distanceTo(centroids.get(j));
                    if (d < bestD) { bestD = d; bestI = i; bestJ = j; }
                }
            }

            // Merge bestI and bestJ
            DendrogramNode merged = DendrogramNode.merge(
                    nodes.get(bestI), nodes.get(bestJ), bestD);

            int si = counts.get(bestI), sj = counts.get(bestJ);
            Vector2D ci = centroids.get(bestI), cj = centroids.get(bestJ);
            double newX = (ci.getX() * si + cj.getX() * sj) / (si + sj);
            double newY = (ci.getY() * si + cj.getY() * sj) / (si + sj);

            // Remove higher index first to keep lower index valid
            nodes    .remove(bestJ); centroids.remove(bestJ); counts.remove(bestJ);
            nodes    .remove(bestI); centroids.remove(bestI); counts.remove(bestI);

            nodes    .add(merged);
            centroids.add(new Vector2D(newX, newY));
            counts   .add(si + sj);
        }

        return nodes.get(0);
    }
}
