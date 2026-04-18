package com.docgalaxy.layout;

import com.docgalaxy.ai.cluster.Cluster;
import com.docgalaxy.model.DendrogramNode;
import com.docgalaxy.model.Edge;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.util.AppConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hierarchical tree layout using the Reingold–Tilford simplified model.
 *
 * <h3>Primary use (multi-cluster)</h3>
 * <p>Construct with {@link #TreeLayout(List, double, double)} passing the
 * {@link Cluster} list produced by {@code HybridClusterStrategy}.  Each
 * cluster carries a {@link DendrogramNode} dendrogram root.  A virtual root
 * is built by left-folding all cluster roots into a binary merge chain:</p>
 * <pre>  merge(merge(r0, r1), r2) …</pre>
 * <p>This places each cluster's notes in a contiguous horizontal band during
 * in-order traversal, while the merge chain expresses the inter-cluster
 * hierarchy at the top of the tree.
 *
 * <h3>Algorithm (Reingold–Tilford simplified)</h3>
 * <ol>
 *   <li><b>Phase 1 — x-slots</b>: in-order (left→right) traversal assigns
 *       each leaf a sequential integer slot (0, 1, 2 …), so semantically
 *       close notes in the same cluster appear adjacent.</li>
 *   <li><b>Phase 2 — positions</b>: post-order traversal assigns positions
 *       to every node (leaves <em>and</em> internal merge nodes):
 *       <ul>
 *         <li>Leaf x = {@code margin + slot / (leafCount-1) × usableWidth}</li>
 *         <li>Internal x = average of children x</li>
 *         <li>y = {@code margin + depth / maxDepth × usableHeight}</li>
 *       </ul></li>
 * </ol>
 *
 * <h3>Tree edges</h3>
 * <p>After {@link #calculate} returns, {@link #getTreeEdges()} provides
 * one {@link Edge} per parent→child relationship in the tree.  Internal
 * nodes carry synthetic IDs prefixed {@code "tree-internal-"}.  These IDs
 * are also present as keys in the returned positions map so that
 * {@code EdgeLayer} can draw the connecting lines.
 *
 * <h3>Legacy single-root path</h3>
 * <p>The constructors {@link #TreeLayout(DendrogramNode)} and
 * {@link #TreeLayout(DendrogramNode, double, double)} are preserved for
 * backward compatibility.  They do not produce tree edges.
 */
public final class TreeLayout implements LayoutStrategy {

    /** Fraction of canvas width/height reserved as margin on each side. */
    private static final double MARGIN = 0.10;

    /**
     * Similarity value used for all synthetic tree edges.
     * With EdgeLayer's {@code ALPHA_SCALE = 70}, this gives alpha ≈ 50.
     */
    private static final double TREE_EDGE_SIMILARITY = 50.0 / 70.0;

    /** Minimum horizontal pixel distance between adjacent leaf nodes. */
    private static final double MIN_LEAF_SPACING = 80.0;

    // ── state ──────────────────────────────────────────────────────────────

    /** Non-null when using the primary multi-cluster path. */
    private final List<Cluster> clusters;

    /** Non-null when using the legacy single-root path. */
    private final DendrogramNode legacyRoot;

    private final double canvasWidth;
    private final double canvasHeight;

    /** Populated by {@link #calculate}; empty until then. */
    private List<Edge> treeEdges = Collections.emptyList();

    // ── Constructors ───────────────────────────────────────────────────────

    /**
     * Primary constructor: drives layout from cluster dendrograms.
     *
     * @param clusters     list of {@link Cluster} objects from HybridClusterStrategy;
     *                     each must carry a non-null {@link DendrogramNode} root
     * @param canvasWidth  world-space canvas width  (must be &gt; 0)
     * @param canvasHeight world-space canvas height (must be &gt; 0)
     */
    public TreeLayout(List<Cluster> clusters, double canvasWidth, double canvasHeight) {
        if (canvasWidth  <= 0) throw new IllegalArgumentException("canvasWidth must be > 0");
        if (canvasHeight <= 0) throw new IllegalArgumentException("canvasHeight must be > 0");
        this.clusters    = clusters != null ? new ArrayList<>(clusters) : Collections.emptyList();
        this.legacyRoot  = null;
        this.canvasWidth  = canvasWidth;
        this.canvasHeight = canvasHeight;
    }

    /**
     * Legacy constructor using default window dimensions.
     *
     * @param root dendrogram root; may be {@code null}
     */
    public TreeLayout(DendrogramNode root) {
        this(root, AppConstants.DEFAULT_WINDOW_WIDTH, AppConstants.DEFAULT_WINDOW_HEIGHT);
    }

    /**
     * Legacy full constructor.
     *
     * @param root         dendrogram root; may be {@code null}
     * @param canvasWidth  world-space canvas width  (must be &gt; 0)
     * @param canvasHeight world-space canvas height (must be &gt; 0)
     */
    public TreeLayout(DendrogramNode root, double canvasWidth, double canvasHeight) {
        if (canvasWidth  <= 0) throw new IllegalArgumentException("canvasWidth must be > 0");
        if (canvasHeight <= 0) throw new IllegalArgumentException("canvasHeight must be > 0");
        this.legacyRoot  = root;
        this.clusters    = null;
        this.canvasWidth  = canvasWidth;
        this.canvasHeight = canvasHeight;
    }

    // ── LayoutStrategy ─────────────────────────────────────────────────────

    /**
     * Computes node positions.  When constructed with a cluster list the
     * returned map also contains positions for synthetic internal-node IDs
     * (prefixed {@code "tree-internal-"}) so that {@code EdgeLayer} can draw
     * tree edges.  Leaf (note) positions are always present.
     *
     * @param nodes note nodes to position; must not be {@code null}
     * @return unmodifiable map from node ID → world-space position
     */
    @Override
    public Map<String, Vector2D> calculate(List<NodeData> nodes) {
        if (nodes == null) throw new IllegalArgumentException("nodes must not be null");
        if (nodes.isEmpty()) {
            treeEdges = Collections.emptyList();
            return Collections.emptyMap();
        }
        if (clusters != null && !clusters.isEmpty()) {
            return calculateFromClusters(nodes);
        }
        return calculateFromSingleRoot(legacyRoot, nodes);
    }

    /**
     * Returns the tree parent→child edges built during the most recent
     * {@link #calculate} call.  Each edge uses a fixed
     * {@link #TREE_EDGE_SIMILARITY} so that {@code EdgeLayer} renders them
     * at a consistent, clearly-visible opacity.
     *
     * <p>Edges may reference synthetic internal-node IDs that do not
     * correspond to any {@link com.docgalaxy.model.celestial.Star}; the
     * {@code EdgeLayer} looks up positions by ID from the positions map
     * returned by {@code calculate()}.
     *
     * @return unmodifiable list; empty if the layout has not run yet or
     *         the legacy single-root constructor was used
     */
    public List<Edge> getTreeEdges() {
        return treeEdges;
    }

    @Override public boolean isIterative() { return false; }
    @Override public String  getName()     { return "Tree"; }

    // ── Multi-cluster RT algorithm ─────────────────────────────────────────

    private Map<String, Vector2D> calculateFromClusters(List<NodeData> nodes) {
        Vector2D centre = new Vector2D(canvasWidth / 2.0, canvasHeight / 2.0);

        // Build set of currently-valid note IDs to prune stale dendrogram leaves
        Set<String> validIds = new HashSet<>(nodes.size() * 2);
        for (NodeData nd : nodes) validIds.add(nd.getNoteId());

        // Collect non-null cluster dendrogram roots, sanitized against validIds
        List<DendrogramNode> roots = new ArrayList<>();
        for (Cluster c : clusters) {
            if (c.getDendrogram() != null) {
                DendrogramNode sanitized = sanitize(c.getDendrogram(), validIds);
                if (sanitized != null) roots.add(sanitized);
            }
        }
        if (roots.isEmpty()) {
            treeEdges = Collections.emptyList();
            Map<String, Vector2D> fb = new HashMap<>(nodes.size() * 2);
            nodes.forEach(n -> fb.put(n.getNoteId(), centre));
            return Collections.unmodifiableMap(fb);
        }

        // Build virtual root: left-fold cluster roots into a binary merge chain.
        //   merge(merge(merge(r0, r1), r2), r3) …
        // In-order traversal then yields r0-leaves, r1-leaves, … in cluster order.
        DendrogramNode virtualRoot = roots.get(0);
        for (int i = 1; i < roots.size(); i++) {
            virtualRoot = DendrogramNode.merge(virtualRoot, roots.get(i), 0.0);
        }

        // Phase 1 — in-order traversal: assign sequential x-slots to leaves
        int[] leafCounter = {0};
        Map<String, double[]> leafSlots = new LinkedHashMap<>();  // noteId → [xSlot, depth]
        inOrderLeaves(virtualRoot, 0, leafCounter, leafSlots);

        int leafCount = leafCounter[0];
        int maxDepth  = leafSlots.values().stream()
                                 .mapToInt(v -> (int) v[1]).max().orElse(1);

        // Phase 2 — post-order traversal: compute positions for ALL nodes
        //   (leaves and internal merge nodes) and collect parent→child edges
        double mX      = canvasWidth  * MARGIN;
        double mY      = canvasHeight * MARGIN;
        // Ensure leaves are at least MIN_LEAF_SPACING px apart; expand beyond
        // canvas bounds if needed (user can pan to see all nodes).
        double usableW = Math.max(canvasWidth - 2 * mX,
                                  (leafCount > 1) ? (leafCount - 1) * MIN_LEAF_SPACING : 0);
        double usableH = canvasHeight - 2 * mY;
        int[]             idGen   = {0};
        Map<String, Vector2D> allPos   = new HashMap<>();
        List<Edge>        edgeList = new ArrayList<>();

        computeNodePositions(virtualRoot, 0,
                             leafSlots, leafCount, maxDepth,
                             mX, mY, usableW, usableH,
                             allPos, edgeList, idGen);

        // Merge allPos (leaves + internal nodes) into result.
        // allPos already contains note positions for all leaves; add fallback
        // centre for any note the dendrogram does not mention.
        Map<String, Vector2D> result = new HashMap<>(allPos);
        for (NodeData nd : nodes) {
            result.putIfAbsent(nd.getNoteId(), centre);
        }

        treeEdges = Collections.unmodifiableList(edgeList);
        return Collections.unmodifiableMap(result);
    }

    /**
     * Recursive post-order traversal.
     *
     * <p>Leaf nodes: position is read from {@code leafSlots}.<br>
     * Internal nodes: x = average of children's x; y = depth × scale.
     *
     * @return the node ID assigned to {@code node}
     *         (noteId for leaves, {@code "tree-internal-N"} for internal nodes)
     */
    private static String computeNodePositions(
            DendrogramNode node, int depth,
            Map<String, double[]> leafSlots, int leafCount, int maxDepth,
            double mX, double mY, double usableW, double usableH,
            Map<String, Vector2D> allPos, List<Edge> edgeList, int[] idGen) {

        double y = (maxDepth > 0)
                ? mY + (double) depth / maxDepth * usableH
                : mY + usableH / 2.0;

        if (node.isLeaf()) {
            String   id   = node.getNoteId();
            double[] slot = leafSlots.get(id);
            double x = (slot != null && leafCount > 1)
                    ? mX + slot[0] / (leafCount - 1.0) * usableW
                    : mX + usableW / 2.0;
            allPos.put(id, new Vector2D(x, y));
            return id;
        }

        // Internal node — generate ID before recursing so we can attach child edges
        String myId = "tree-internal-" + (idGen[0]++);

        double sumX      = 0.0;
        int    childCount = 0;

        if (node.getLeft() != null) {
            String cid = computeNodePositions(node.getLeft(), depth + 1,
                                              leafSlots, leafCount, maxDepth,
                                              mX, mY, usableW, usableH,
                                              allPos, edgeList, idGen);
            edgeList.add(new Edge(myId, cid, TREE_EDGE_SIMILARITY));
            sumX += allPos.get(cid).getX();
            childCount++;
        }
        if (node.getRight() != null) {
            String cid = computeNodePositions(node.getRight(), depth + 1,
                                              leafSlots, leafCount, maxDepth,
                                              mX, mY, usableW, usableH,
                                              allPos, edgeList, idGen);
            edgeList.add(new Edge(myId, cid, TREE_EDGE_SIMILARITY));
            sumX += allPos.get(cid).getX();
            childCount++;
        }

        double x = childCount > 0 ? sumX / childCount : mX + usableW / 2.0;
        allPos.put(myId, new Vector2D(x, y));
        return myId;
    }

    /**
     * Prunes a dendrogram so that:
     * <ul>
     *   <li>Leaves whose {@code noteId} is absent from {@code validIds} are removed.</li>
     *   <li>Internal nodes with only one surviving child are replaced by that child
     *       (single-child nodes carry no branching information).</li>
     * </ul>
     *
     * @param node     dendrogram node to prune; may be {@code null}
     * @param validIds set of note IDs that still exist in the KnowledgeBase
     * @return pruned subtree, or {@code null} if the entire subtree was removed
     */
    private static DendrogramNode sanitize(DendrogramNode node, Set<String> validIds) {
        if (node == null) return null;
        if (node.isLeaf()) {
            return validIds.contains(node.getNoteId()) ? node : null;
        }
        DendrogramNode left  = sanitize(node.getLeft(),  validIds);
        DendrogramNode right = sanitize(node.getRight(), validIds);
        if (left == null && right == null) return null;
        if (left  == null) return right;   // single-child collapse
        if (right == null) return left;    // single-child collapse
        return DendrogramNode.merge(left, right, node.getMergeDistance());
    }

    /**
     * In-order (left→right) traversal: records each leaf's x-slot (sequential
     * integer) and tree depth in {@code leafSlots}.
     */
    private static void inOrderLeaves(DendrogramNode node, int depth,
                                       int[] counter,
                                       Map<String, double[]> leafSlots) {
        if (node.isLeaf()) {
            leafSlots.put(node.getNoteId(), new double[]{counter[0]++, depth});
            return;
        }
        if (node.getLeft()  != null) inOrderLeaves(node.getLeft(),  depth + 1, counter, leafSlots);
        if (node.getRight() != null) inOrderLeaves(node.getRight(), depth + 1, counter, leafSlots);
    }

    // ── Legacy single-root path ────────────────────────────────────────────

    private Map<String, Vector2D> calculateFromSingleRoot(DendrogramNode root,
                                                           List<NodeData> nodes) {
        treeEdges = Collections.emptyList();
        Vector2D centre = new Vector2D(canvasWidth / 2.0, canvasHeight / 2.0);

        if (nodes.size() == 1) return Map.of(nodes.get(0).getNoteId(), centre);

        if (root == null) {
            Map<String, Vector2D> fb = new HashMap<>(nodes.size() * 2);
            nodes.forEach(n -> fb.put(n.getNoteId(), centre));
            return Collections.unmodifiableMap(fb);
        }

        int[] leafCounter = {0};
        Map<String, double[]> leafSlots = new HashMap<>();
        inOrderLeaves(root, 0, leafCounter, leafSlots);

        int leafCount = leafCounter[0];
        int maxDepth  = leafSlots.values().stream()
                                 .mapToInt(v -> (int) v[1]).max().orElse(0);

        double mX      = canvasWidth  * MARGIN;
        double mY      = canvasHeight * MARGIN;
        double usableW = canvasWidth  - 2 * mX;
        double usableH = canvasHeight - 2 * mY;

        Map<String, Vector2D> result = new HashMap<>(nodes.size() * 2);
        for (NodeData nd : nodes) {
            double[] lp = leafSlots.get(nd.getNoteId());
            if (lp == null) { result.put(nd.getNoteId(), centre); continue; }
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
}
