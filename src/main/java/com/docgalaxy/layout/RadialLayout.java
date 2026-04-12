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
 *   <li><b>Centre selection</b> — the node with the highest <em>outgoing</em>
 *       KNN degree is chosen as the layout hub.  Ties are broken by total
 *       (outgoing + incoming) degree, then by position in the input list.</li>
 *   <li><b>BFS ring assignment</b> — a standard breadth-first traversal
 *       starting at the centre assigns each reachable note a ring number equal
 *       to its shortest hop distance from the centre.  Ring 0 = centre,
 *       ring 1 = direct KNN neighbours, ring 2 = neighbours-of-neighbours, etc.</li>
 *   <li><b>Disconnected nodes</b> — notes not reachable from the centre via KNN
 *       edges are collected into one additional "overflow" ring just outside the
 *       last BFS ring.</li>
 *   <li><b>Radial placement</b> — ring radii are evenly spaced across the
 *       usable canvas radius ({@code min(W,H)/2 × (1 − 2×MARGIN)}).  Within
 *       each ring the notes are distributed at equal angular intervals,
 *       starting from the positive x-axis.</li>
 * </ol>
 *
 * <p>{@link #isIterative()} returns {@code false} — the layout is computed in
 * a single deterministic pass.
 */
public final class RadialLayout implements LayoutStrategy {

    /** Fraction of each canvas half-extent reserved as margin. */
    private static final double MARGIN = 0.10;

    private static final double TWO_PI = 2.0 * Math.PI;

    private final double canvasWidth;
    private final double canvasHeight;

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
     * Computes radial positions for all nodes.
     *
     * @param nodes nodes to lay out; must not be {@code null}
     * @return unmodifiable map from note-id to canvas position
     * @throws IllegalArgumentException if {@code nodes} is {@code null}
     */
    @Override
    public Map<String, Vector2D> calculate(List<NodeData> nodes) {
        if (nodes == null) throw new IllegalArgumentException("nodes must not be null");
        if (nodes.isEmpty()) return Collections.emptyMap();

        Vector2D centre = new Vector2D(canvasWidth / 2.0, canvasHeight / 2.0);

        if (nodes.size() == 1) {
            return Map.of(nodes.get(0).getNoteId(), centre);
        }

        // Build id → node lookup
        Map<String, NodeData> nodeById = new HashMap<>(nodes.size() * 2);
        for (NodeData nd : nodes) nodeById.put(nd.getNoteId(), nd);

        // --- Step 1: centre selection ---
        String centreId = pickCentre(nodes);

        // --- Step 2: BFS ring assignment ---
        // LinkedHashMap preserves insertion order (BFS order) for deterministic tests
        Map<String, Integer> ringOf = new LinkedHashMap<>(nodes.size() * 2);
        ringOf.put(centreId, 0);

        Queue<String> queue = new ArrayDeque<>();
        queue.add(centreId);

        while (!queue.isEmpty()) {
            String current  = queue.poll();
            NodeData nd     = nodeById.get(current);
            if (nd == null) continue;
            int nextRing    = ringOf.get(current) + 1;
            for (Neighbor nb : nd.getNeighbors()) {
                String nbId = nb.getNoteId();
                if (!ringOf.containsKey(nbId) && nodeById.containsKey(nbId)) {
                    ringOf.put(nbId, nextRing);
                    queue.add(nbId);
                }
            }
        }

        // --- Step 3: overflow ring for disconnected nodes ---
        int maxBfsRing = ringOf.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int overflowRing = maxBfsRing + 1;
        for (NodeData nd : nodes) {
            if (!ringOf.containsKey(nd.getNoteId())) {
                ringOf.put(nd.getNoteId(), overflowRing);
            }
        }

        // Group by ring (TreeMap sorts rings 0,1,2,…)
        NavigableMap<Integer, List<String>> ringMembers = new TreeMap<>();
        for (Map.Entry<String, Integer> e : ringOf.entrySet()) {
            ringMembers.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        }

        int maxRing = ringMembers.lastKey();

        // Usable radius: from canvas centre to the outermost ring
        double usableRadius = Math.min(canvasWidth, canvasHeight) / 2.0 * (1.0 - 2.0 * MARGIN);

        // --- Step 4: assign canvas positions ---
        Map<String, Vector2D> result = new HashMap<>(nodes.size() * 2);

        for (Map.Entry<Integer, List<String>> e : ringMembers.entrySet()) {
            int    r          = e.getKey();
            List<String> ids  = e.getValue();

            if (r == 0) {
                // Centre node sits exactly at the canvas centre
                result.put(ids.get(0), centre);
                continue;
            }

            double radius = (maxRing > 0) ? (double) r / maxRing * usableRadius
                                          : usableRadius;
            int    count  = ids.size();

            for (int i = 0; i < count; i++) {
                double angle = TWO_PI * i / count;
                double x = centre.getX() + radius * Math.cos(angle);
                double y = centre.getY() + radius * Math.sin(angle);
                result.put(ids.get(i), new Vector2D(x, y));
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
    // Private helpers
    // -------------------------------------------------------------------------

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
