package com.docgalaxy.layout;

import com.docgalaxy.model.DendrogramNode;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.util.AppConstants;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hierarchical tree layout based on the Reingold–Tilford (RT) simplified model.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>An in-order (left-then-right) traversal of the {@link DendrogramNode}
 *       tree assigns each <em>leaf</em> a sequential integer x-slot
 *       (0, 1, 2, …).  Left-side subtree leaves always precede right-side
 *       leaves, so related notes are placed adjacent to each other.</li>
 *   <li>Each node's y-slot equals its depth in the tree (root = 0, children
 *       = 1, …).  The root is drawn at the top; leaves hang below.</li>
 *   <li>Both axes are scaled linearly so that the leftmost leaf maps to
 *       {@code canvasWidth × MARGIN} and the rightmost to
 *       {@code canvasWidth × (1 − MARGIN)}, and the deepest leaf maps to
 *       {@code canvasHeight × (1 − MARGIN)}.</li>
 * </ol>
 *
 * <p>Only <em>leaf</em> nodes represent actual notes and appear in the result
 * map.  Internal (merge) nodes are used solely for x-slot computation and are
 * not returned.  Any note ID present in the {@code NodeData} list but absent
 * from the dendrogram is placed at the canvas centre as a fallback.
 *
 * <p>If no dendrogram root was provided at construction time, all nodes are
 * placed at the canvas centre.
 */
public final class TreeLayout implements LayoutStrategy {

    /** Fraction of canvas width / height reserved as margin on each side. */
    private static final double MARGIN = 0.10;

    private final DendrogramNode root;
    private final double canvasWidth;
    private final double canvasHeight;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Constructor using default window dimensions from {@link AppConstants}.
     *
     * @param root root of the HAC dendrogram produced by clustering;
     *             may be {@code null} (all nodes fall back to canvas centre)
     */
    public TreeLayout(DendrogramNode root) {
        this(root, AppConstants.DEFAULT_WINDOW_WIDTH, AppConstants.DEFAULT_WINDOW_HEIGHT);
    }

    /**
     * Full constructor.
     *
     * @param root         root of the HAC dendrogram; may be {@code null}
     * @param canvasWidth  layout canvas width in world-space units (must be &gt; 0)
     * @param canvasHeight layout canvas height in world-space units (must be &gt; 0)
     * @throws IllegalArgumentException if either canvas dimension is &le; 0
     */
    public TreeLayout(DendrogramNode root, double canvasWidth, double canvasHeight) {
        if (canvasWidth  <= 0) throw new IllegalArgumentException("canvasWidth must be > 0");
        if (canvasHeight <= 0) throw new IllegalArgumentException("canvasHeight must be > 0");
        this.root         = root;
        this.canvasWidth  = canvasWidth;
        this.canvasHeight = canvasHeight;
    }

    // -------------------------------------------------------------------------
    // LayoutStrategy
    // -------------------------------------------------------------------------

    /**
     * Positions each note according to its place in the dendrogram.
     *
     * @param nodes note nodes to position; must not be {@code null}
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

        // No dendrogram — place everything at canvas centre
        if (root == null) {
            Map<String, Vector2D> fallback = new HashMap<>(nodes.size() * 2);
            for (NodeData nd : nodes) fallback.put(nd.getNoteId(), centre);
            return Collections.unmodifiableMap(fallback);
        }

        // --- Phase 1: assign (xSlot, depth) to every leaf via in-order traversal ---
        Map<String, double[]> leafLayout = new HashMap<>();  // noteId → [xSlot, depth]
        int[] leafCounter = {0};
        traverseAndAssign(root, 0, leafCounter, leafLayout);

        int leafCount = leafCounter[0];  // total leaves visited
        int maxDepth  = leafLayout.values().stream()
                                  .mapToInt(v -> (int) v[1])
                                  .max()
                                  .orElse(0);

        // --- Phase 2: scale to canvas ---
        double mX      = canvasWidth  * MARGIN;
        double mY      = canvasHeight * MARGIN;
        double usableW = canvasWidth  - 2 * mX;
        double usableH = canvasHeight - 2 * mY;

        Map<String, Vector2D> result = new HashMap<>(nodes.size() * 2);
        for (NodeData nd : nodes) {
            double[] lp = leafLayout.get(nd.getNoteId());
            if (lp == null) {
                // Note absent from dendrogram — graceful fallback
                result.put(nd.getNoteId(), centre);
                continue;
            }
            double x = (leafCount > 1)
                    ? mX + lp[0] / (leafCount - 1.0) * usableW
                    : canvasWidth  / 2.0;
            double y = (maxDepth > 0)
                    ? mY + lp[1] / (double) maxDepth * usableH
                    : canvasHeight / 2.0;
            result.put(nd.getNoteId(), new Vector2D(x, y));
        }
        return Collections.unmodifiableMap(result);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isIterative() { return false; }

    /** {@inheritDoc} */
    @Override
    public String getName() { return "Tree"; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Recursive in-order traversal.  Each leaf is assigned the next available
     * x-slot (increments {@code leafCounter[0]}) and its tree depth.
     * Internal (merge) nodes are traversed but not stored.
     */
    private static void traverseAndAssign(DendrogramNode node,
                                           int depth,
                                           int[] leafCounter,
                                           Map<String, double[]> leafLayout) {
        if (node.isLeaf()) {
            leafLayout.put(node.getNoteId(), new double[]{leafCounter[0], depth});
            leafCounter[0]++;
            return;
        }
        if (node.getLeft()  != null) {
            traverseAndAssign(node.getLeft(),  depth + 1, leafCounter, leafLayout);
        }
        if (node.getRight() != null) {
            traverseAndAssign(node.getRight(), depth + 1, leafCounter, leafLayout);
        }
    }
}
