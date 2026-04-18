package com.docgalaxy.layout;

import com.docgalaxy.ai.Neighbor;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.util.AppConstants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Concentric-ring ("radial") layout driven by the KNN graph embedded in each
 * {@link NodeData}.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li><b>Centre selection</b> — the node with the highest KNN degree (most
 *       outgoing neighbours) is chosen as the layout hub.  Ties are broken by
 *       total (outgoing + incoming) degree, then by position in the input list.</li>
 *   <li><b>BFS ring assignment</b> — a standard breadth-first traversal
 *       starting at the centre assigns each reachable note a ring number equal
 *       to its shortest hop distance from the centre.  Ring 0 = centre,
 *       ring 1 = direct KNN neighbours, ring 2 = neighbours-of-neighbours, etc.</li>
 *   <li><b>Disconnected nodes</b> — notes not reachable from the centre via KNN
 *       edges are collected into one additional "overflow" ring just outside the
 *       last BFS ring.</li>
 *   <li><b>Radial placement</b> — rings are spaced {@value #RING_SPACING} px
 *       apart from the centre.  Within each ring the notes are distributed at
 *       equal angular intervals, starting from the positive x-axis.</li>
 * </ol>
 *
 * <p>{@link #isIterative()} returns {@code false} — the layout is computed in
 * a single deterministic pass.
 *
 * <p>After {@link #calculate} the caller may read {@link #getCenterPosition()}
 * and {@link #getRingCount()} to construct a {@code RadialRingLayer} that draws
 * the concentric reference circles.
 */
public final class RadialLayout implements LayoutStrategy {

    /** Fixed pixel distance between consecutive rings. */
    public static final double RING_SPACING = 150.0;

    private static final double TWO_PI = 2.0 * Math.PI;

    private final double canvasWidth;
    private final double canvasHeight;

    /** Set by {@link #calculate}; {@code null} until then. */
    private Vector2D centerPosition;

    /** Number of BFS rings (excluding ring 0 = centre); set by {@link #calculate}. */
    private int ringCount;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Production constructor — uses default window dimensions from
     * {@link AppConstants}.
     */
    public RadialLayout() {
        this(AppConstants.DEFAULT_WINDOW_WIDTH, AppConstants.DEFAULT_WINDOW_HEIGHT);
    }

    /**
     * Full constructor.
     *
     * @param canvasWidth  layout canvas width in world-space units (must be &gt; 0)
     * @param canvasHeight layout canvas height in world-space units (must be &gt; 0)
     * @throws IllegalArgumentException if either canvas dimension is &le; 0
     */
    public RadialLayout(double canvasWidth, double canvasHeight) {
        if (canvasWidth  <= 0) throw new IllegalArgumentException("canvasWidth must be > 0");
        if (canvasHeight <= 0) throw new IllegalArgumentException("canvasHeight must be > 0");
        this.canvasWidth  = canvasWidth;
        this.canvasHeight = canvasHeight;
    }

    // -------------------------------------------------------------------------
    // LayoutStrategy
    // -------------------------------------------------------------------------

    /**
     * Computes radial positions for all nodes using parent-guided angle assignment.
     *
     * <ul>
     *   <li>Ring 1 nodes are sorted by similarity to the centre (descending) and
     *       distributed evenly around the full 360°.</li>
     *   <li>Ring 2+ nodes inherit the angle of their BFS parent.  All children of
     *       a given parent are spread within a sector of width
     *       {@code min(30°, 360° / M)} centred on the parent's angle, where
     *       {@code M} is the number of parents that have children in that ring.
     *       This causes nodes in the same "lineage" to form a radial line.</li>
     *   <li>Disconnected (overflow) nodes are distributed evenly on an extra ring
     *       beyond the last BFS ring.</li>
     * </ul>
     *
     * @param nodes nodes to lay out; must not be {@code null}
     * @return unmodifiable map from note-id to canvas position
     * @throws IllegalArgumentException if {@code nodes} is {@code null}
     */
    @Override
    public Map<String, Vector2D> calculate(List<NodeData> nodes) {
        if (nodes == null) throw new IllegalArgumentException("nodes must not be null");
        if (nodes.isEmpty()) {
            centerPosition = null;
            ringCount = 0;
            return Collections.emptyMap();
        }

        Vector2D centre = new Vector2D(canvasWidth / 2.0, canvasHeight / 2.0);
        centerPosition = centre;

        if (nodes.size() == 1) {
            ringCount = 0;
            return Map.of(nodes.get(0).getNoteId(), centre);
        }

        // Build id → node lookup
        Map<String, NodeData> nodeById = new HashMap<>(nodes.size() * 2);
        for (NodeData nd : nodes) nodeById.put(nd.getNoteId(), nd);

        // --- Step 1: centre selection ---
        String centreId = pickCentre(nodes);

        // --- Step 2: BFS ring assignment + parent tracking ---
        Map<String, Integer> ringOf   = new LinkedHashMap<>(nodes.size() * 2);
        Map<String, String>  parentOf = new HashMap<>(nodes.size() * 2);
        ringOf.put(centreId, 0);

        Queue<String> queue = new ArrayDeque<>();
        queue.add(centreId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            NodeData nd    = nodeById.get(current);
            if (nd == null) continue;
            int nextRing = ringOf.get(current) + 1;
            for (Neighbor nb : nd.getNeighbors()) {
                String nbId = nb.getNoteId();
                if (!ringOf.containsKey(nbId) && nodeById.containsKey(nbId)) {
                    ringOf.put(nbId, nextRing);
                    parentOf.put(nbId, current);
                    queue.add(nbId);
                }
            }
        }

        // --- Step 3: overflow ring for disconnected nodes ---
        int maxBfsRing  = ringOf.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int overflowRing = maxBfsRing + 1;
        for (NodeData nd : nodes) {
            if (!ringOf.containsKey(nd.getNoteId())) {
                ringOf.put(nd.getNoteId(), overflowRing);
            }
        }

        // Group by ring (TreeMap sorts rings 0, 1, 2, …)
        NavigableMap<Integer, List<String>> ringMembers = new TreeMap<>();
        for (Map.Entry<String, Integer> e : ringOf.entrySet()) {
            ringMembers.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        }

        int maxRing = ringMembers.lastKey();
        ringCount = maxRing;



        // Build sector and similarity-to-centre lookups used for sorting
        Map<String, String> sectorOf = new HashMap<>(nodes.size() * 2);
        for (NodeData nd : nodes) sectorOf.put(nd.getNoteId(), nd.getSectorId());

        Map<String, Double> simFromCentre = new HashMap<>();
        NodeData centreNode = nodeById.get(centreId);
        if (centreNode != null) {
            for (Neighbor nb : centreNode.getNeighbors()) {
                simFromCentre.put(nb.getNoteId(), nb.getSimilarity());
            }
        }

        // --- Step 4: parent-guided angle assignment ---
        Map<String, Vector2D> result  = new HashMap<>(nodes.size() * 2);
        Map<String, Double>   angleOf = new HashMap<>(nodes.size() * 2);

        // Ring 0: centre node
        result.put(centreId, centre);

        // Ring 1: sector-grouped, within each sector sorted by sim-to-centre desc,
        //         then distributed evenly around the full 360°.
        List<String> ring1 = ringMembers.getOrDefault(1, Collections.emptyList());
        if (!ring1.isEmpty()) {
            List<String> sorted1 = sortBySectorThenSim(ring1, sectorOf, simFromCentre);

            int    N       = sorted1.size();
            double radius1 = RING_SPACING;
            for (int i = 0; i < N; i++) {
                double angle = TWO_PI * i / N;
                String id    = sorted1.get(i);
                angleOf.put(id, angle);
                result.put(id, new Vector2D(
                        centre.getX() + radius1 * Math.cos(angle),
                        centre.getY() + radius1 * Math.sin(angle)));

            }
        }

        // Ring 2+: children inherit parent angle, spread within a sector.
        //          Within each parent's children, apply sector-then-sim ordering.
        for (int r = 2; r <= maxBfsRing; r++) {
            List<String> members = ringMembers.getOrDefault(r, Collections.emptyList());
            if (members.isEmpty()) continue;

            double radius = r * RING_SPACING;

            // Group children by their BFS parent (LinkedHashMap preserves BFS order)
            Map<String, List<String>> byParent = new LinkedHashMap<>();
            for (String id : members) {
                String p = parentOf.get(id);
                if (p != null) byParent.computeIfAbsent(p, k -> new ArrayList<>()).add(id);
            }

            // Sector width: narrow enough that children of different parents don't overlap
            int    M           = byParent.size();  // number of active parents in this ring
            double sectorWidth = Math.min(Math.PI / 6.0, TWO_PI / Math.max(1, M));

            for (Map.Entry<String, List<String>> entry : byParent.entrySet()) {
                String       parent   = entry.getKey();
                List<String> children = sortBySectorThenSim(entry.getValue(), sectorOf, simFromCentre);
                double parentAngle    = angleOf.getOrDefault(parent, 0.0);
                int    k              = children.size();

                for (int i = 0; i < k; i++) {
                    double childAngle = (k == 1)
                            ? parentAngle
                            : parentAngle - sectorWidth / 2.0 + i * sectorWidth / (k - 1.0);
                    // Normalize to [0, 2π)
                    childAngle = ((childAngle % TWO_PI) + TWO_PI) % TWO_PI;

                    String id = children.get(i);
                    angleOf.put(id, childAngle);
                    result.put(id, new Vector2D(
                            centre.getX() + radius * Math.cos(childAngle),
                            centre.getY() + radius * Math.sin(childAngle)));

                }
            }
        }

        // Overflow ring: sector-grouped, then sim desc, distributed evenly
        List<String> overflow = ringMembers.getOrDefault(overflowRing, Collections.emptyList());
        if (!overflow.isEmpty()) {
            List<String> sortedOF = sortBySectorThenSim(overflow, sectorOf, simFromCentre);
            double radius = overflowRing * RING_SPACING;
            int    count  = sortedOF.size();
            for (int i = 0; i < count; i++) {
                double angle = TWO_PI * i / count;
                String id    = sortedOF.get(i);
                angleOf.put(id, angle);
                result.put(id, new Vector2D(
                        centre.getX() + radius * Math.cos(angle),
                        centre.getY() + radius * Math.sin(angle)));
            }
        }

        return Collections.unmodifiableMap(result);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isIterative() { return false; }

    /** {@inheritDoc} */
    @Override
    public String getName() { return "Radial"; }

    // -------------------------------------------------------------------------
    // Post-calculate accessors (consumed by RadialRingLayer)
    // -------------------------------------------------------------------------

    /**
     * World-space position of the centre node, set after the most recent
     * {@link #calculate} call.  Returns {@code null} if {@code calculate}
     * has not been called yet.
     */
    public Vector2D getCenterPosition() { return centerPosition; }

    /**
     * Number of rings (excluding ring 0 = centre node), set after the most
     * recent {@link #calculate} call.  Used to determine how many concentric
     * circles the {@code RadialRingLayer} should draw.
     */
    public int getRingCount() { return ringCount; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a new list containing the same IDs sorted by:
     * <ol>
     *   <li>Sector ID (lexicographic) — nodes in the same sector are adjacent.</li>
     *   <li>Similarity to the centre node (descending) — most similar first within
     *       each sector.</li>
     * </ol>
     */
    private static List<String> sortBySectorThenSim(List<String> ids,
                                                     Map<String, String> sectorOf,
                                                     Map<String, Double> simFromCentre) {
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort((a, b) -> {
            String sA = sectorOf.getOrDefault(a, "");
            String sB = sectorOf.getOrDefault(b, "");
            int cmp = sA.compareTo(sB);
            if (cmp != 0) return cmp;
            // Within same sector: higher similarity first
            return Double.compare(
                    simFromCentre.getOrDefault(b, 0.0),
                    simFromCentre.getOrDefault(a, 0.0));
        });
        return sorted;
    }

    /**
     * Selects the hub node using a two-level priority:
     * <ol>
     *   <li>Most outgoing KNN edges (highest out-degree).</li>
     *   <li>Highest total degree (out + in) as tie-breaker.</li>
     *   <li>First occurrence in the input list as final tie-breaker
     *       (ensures deterministic output).</li>
     * </ol>
     */
    private static String pickCentre(List<NodeData> nodes) {
        // Compute in-degree for tie-breaking
        Map<String, Integer> inDegree = new HashMap<>();
        for (NodeData nd : nodes) {
            for (Neighbor nb : nd.getNeighbors()) {
                inDegree.merge(nb.getNoteId(), 1, Integer::sum);
            }
        }

        String bestId    = null;
        int    bestOut   = -1;
        int    bestTotal = -1;

        for (NodeData nd : nodes) {
            int out   = nd.getNeighbors().size();
            int total = out + inDegree.getOrDefault(nd.getNoteId(), 0);
            if (out > bestOut || (out == bestOut && total > bestTotal)) {
                bestOut   = out;
                bestTotal = total;
                bestId    = nd.getNoteId();
            }
        }
        return bestId;
    }
}
