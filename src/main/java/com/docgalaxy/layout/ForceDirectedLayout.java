package com.docgalaxy.layout;

import com.docgalaxy.ai.Neighbor;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.CelestialBody;
import com.docgalaxy.util.AppConstants;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Force-directed galaxy layout implementing the Fruchterman–Reingold (FR)
 * algorithm augmented with semantic spring attraction and centripetal gravity.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li><b>Initialise</b> — map PCA positions to canvas space (10 % margin).</li>
 *   <li><b>Iterate</b> — up to {@value AppConstants#MAX_ITERATIONS} steps or
 *       until temperature drops below {@value AppConstants#MIN_TEMPERATURE}:
 *       <ul>
 *         <li>Repulsion between every node and its {@link SpatialGrid} neighbours
 *             (FR formula K²/d).</li>
 *         <li>Semantic spring attraction along each KNN edge ({@link NodeData#getNeighbors()},
 *             K = {@value AppConstants#KNN_K}), using cosine-similarity as stiffness.</li>
 *         <li>Gravity toward the canvas centre (constant
 *             {@value AppConstants#GRAVITY_CONSTANT}).</li>
 *         <li>Displacement clamped to current temperature; temperature cooled by
 *             {@value AppConstants#COOLING_FACTOR} each step.</li>
 *       </ul>
 *   </li>
 *   <li><b>Post-process</b> — {@link OverlapResolver} makes a final pass to
 *       eliminate any residual visual overlap.</li>
 * </ol>
 *
 * <p>{@link #isIterative()} returns {@code true}; the caller may invoke
 * {@link #calculate} repeatedly as new notes are added.
 */
public final class ForceDirectedLayout implements LayoutStrategy {

    /** Visual radius assumed for each node when running {@link OverlapResolver}. */
    static final double DEFAULT_NODE_RADIUS = 8.0;

    /** Guard against division by zero in force calculations. */
    private static final double EPSILON = 1e-9;

    /** Fraction of canvas reserved as margin on each side during initialisation. */
    private static final double MARGIN = 0.10;

    private final double canvasWidth;
    private final double canvasHeight;
    private final OverlapResolver overlapResolver;

    /** Number of iterations used in the most recent {@link #calculate} call. */
    private int lastIterationCount;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Production constructor — uses the default window dimensions from
     * {@link AppConstants}.
     */
    public ForceDirectedLayout() {
        this(AppConstants.DEFAULT_WINDOW_WIDTH, AppConstants.DEFAULT_WINDOW_HEIGHT);
    }

    /**
     * Constructor with explicit canvas dimensions.
     *
     * @param canvasWidth  layout canvas width in world-space units (must be &gt; 0)
     * @param canvasHeight layout canvas height in world-space units (must be &gt; 0)
     */
    public ForceDirectedLayout(double canvasWidth, double canvasHeight) {
        this(canvasWidth, canvasHeight, new OverlapResolver());
    }

    /**
     * Package-private constructor for tests — allows injecting a custom
     * {@link OverlapResolver} (e.g. one with fewer passes).
     */
    ForceDirectedLayout(double canvasWidth, double canvasHeight, OverlapResolver overlapResolver) {
        if (canvasWidth  <= 0) throw new IllegalArgumentException("canvasWidth must be > 0");
        if (canvasHeight <= 0) throw new IllegalArgumentException("canvasHeight must be > 0");
        this.canvasWidth    = canvasWidth;
        this.canvasHeight   = canvasHeight;
        this.overlapResolver = overlapResolver;
    }

    // -------------------------------------------------------------------------
    // LayoutStrategy
    // -------------------------------------------------------------------------

    /**
     * Computes a stable force-directed 2-D layout for the given nodes.
     *
     * @param nodes nodes to lay out; must not be {@code null};
     *              each node's {@link NodeData#getNeighbors()} list provides
     *              the pre-computed KNN semantic edges
     * @return unmodifiable map from note-id to final canvas position
     * @throws IllegalArgumentException if {@code nodes} is {@code null}
     */
    @Override
    public Map<String, Vector2D> calculate(List<NodeData> nodes) {
        if (nodes == null) throw new IllegalArgumentException("nodes must not be null");

        int n = nodes.size();
        if (n == 0) return Collections.emptyMap();

        Vector2D centre = new Vector2D(canvasWidth / 2.0, canvasHeight / 2.0);
        if (n == 1) return Map.of(nodes.get(0).getNoteId(), centre);

        // Step 1 — initialise positions from PCA → canvas space
        Map<String, Vector2D> pos = initPositions(nodes);

        // Step 2 — global similarity range for semantic attraction normalisation
        double[] simRange = computeSimRange(nodes);
        double minSim = simRange[0], maxSim = simRange[1];

        // Step 3 — FR optimal distance K = sqrt(area / n)
        double K = Math.sqrt(canvasWidth * canvasHeight / n);

        // Step 4 — iteration loop
        SpatialGrid grid = new SpatialGrid();
        double temp = AppConstants.INITIAL_TEMPERATURE;
        int iter = 0;

        for (;
             iter < AppConstants.MAX_ITERATIONS && temp > AppConstants.MIN_TEMPERATURE;
             iter++) {

            // Rebuild grid with current positions
            List<NodeData> snapshot  = buildSnapshot(nodes, pos);
            Map<String, NodeData> snapshotById = indexById(snapshot);
            grid.rebuild(snapshot, canvasWidth, canvasHeight);

            // Initialise force accumulators
            Map<String, Vector2D> forces = new HashMap<>(n * 2);
            for (NodeData nd : nodes) forces.put(nd.getNoteId(), Vector2D.ZERO);

            // Repulsion — query SpatialGrid for each node's neighbourhood
            for (NodeData nd : nodes) {
                String    id   = nd.getNoteId();
                Vector2D  posA = pos.get(id);
                for (NodeData other : grid.getNearbyNodes(snapshotById.get(id))) {
                    String otherId = other.getNoteId();
                    if (otherId.equals(id)) continue;
                    Vector2D rep = ForceCalculator.repulsion(posA, pos.get(otherId), K);
                    forces.put(id, forces.get(id).add(rep));
                }
            }

            // Semantic attraction — along each KNN edge
            for (NodeData nd : nodes) {
                String   id   = nd.getNoteId();
                Vector2D posA = pos.get(id);
                for (Neighbor nb : nd.getNeighbors()) {
                    Vector2D posB = pos.get(nb.getNoteId());
                    if (posB == null) continue; // neighbour not in this layout
                    Vector2D attr = ForceCalculator.semanticAttraction(
                            posA, posB, nb.getSimilarity(), minSim, maxSim, K);
                    forces.put(id, forces.get(id).add(attr));
                }
            }

            // Gravity — centripetal pull toward canvas centre
            for (NodeData nd : nodes) {
                String   id   = nd.getNoteId();
                Vector2D grav = ForceCalculator.gravity(pos.get(id), centre,
                                                        AppConstants.GRAVITY_CONSTANT);
                forces.put(id, forces.get(id).add(grav));
            }

            // Apply forces, clamping displacement to current temperature
            for (NodeData nd : nodes) {
                String   id    = nd.getNoteId();
                Vector2D force = forces.get(id);
                double   mag   = force.magnitude();
                if (mag > EPSILON) {
                    double   clipped = Math.min(mag, temp);
                    pos.put(id, pos.get(id).add(force.scale(clipped / mag)));
                }
            }

            // Cool temperature
            temp *= AppConstants.COOLING_FACTOR;
        }
        lastIterationCount = iter;

        // Step 5 — resolve residual overlaps
        List<CelestialBody> bodies = buildBodies(nodes, pos);
        overlapResolver.resolve(bodies);
        for (CelestialBody body : bodies) {
            pos.put(body.getId(), body.getPosition());
        }

        return Collections.unmodifiableMap(pos);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isIterative() { return true; }

    /** {@inheritDoc} */
    @Override
    public String getName() { return "Galaxy"; }

    /**
     * Returns the number of iterations used in the most recent {@link #calculate}
     * call.  Returns 0 if {@code calculate} has not been called yet.
     */
    public int getLastIterationCount() { return lastIterationCount; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Scales PCA positions (arbitrary range) into canvas space with a
     * {@value #MARGIN} margin on each side.
     */
    private Map<String, Vector2D> initPositions(List<NodeData> nodes) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (NodeData nd : nodes) {
            Vector2D p = nd.getInitialPosition();
            if (p.getX() < minX) minX = p.getX();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getY() > maxY) maxY = p.getY();
        }

        double rangeX  = maxX - minX;
        double rangeY  = maxY - minY;
        double usableW = canvasWidth  * (1.0 - 2 * MARGIN);
        double usableH = canvasHeight * (1.0 - 2 * MARGIN);
        double offX    = canvasWidth  * MARGIN;
        double offY    = canvasHeight * MARGIN;

        Map<String, Vector2D> positions = new HashMap<>(nodes.size() * 2);
        for (NodeData nd : nodes) {
            Vector2D p  = nd.getInitialPosition();
            double   nx = (rangeX < EPSILON) ? canvasWidth  / 2.0
                        : offX + (p.getX() - minX) / rangeX * usableW;
            double   ny = (rangeY < EPSILON) ? canvasHeight / 2.0
                        : offY + (p.getY() - minY) / rangeY * usableH;
            positions.put(nd.getNoteId(), new Vector2D(nx, ny));
        }
        return positions;
    }

    /**
     * Creates a snapshot of the node list with current positions as
     * {@code initialPosition} so the {@link SpatialGrid} can use them.
     */
    private static List<NodeData> buildSnapshot(List<NodeData> nodes,
                                                 Map<String, Vector2D> pos) {
        List<NodeData> snapshot = new ArrayList<>(nodes.size());
        for (NodeData nd : nodes) {
            snapshot.add(new NodeData(nd.getNoteId(), pos.get(nd.getNoteId()),
                                      nd.getSectorId(), nd.getNeighbors()));
        }
        return snapshot;
    }

    /** Builds a by-id map from a snapshot for O(1) lookup during grid queries. */
    private static Map<String, NodeData> indexById(List<NodeData> snapshot) {
        Map<String, NodeData> map = new HashMap<>(snapshot.size() * 2);
        for (NodeData nd : snapshot) map.put(nd.getNoteId(), nd);
        return map;
    }

    /**
     * Scans all KNN edges to find the global [minSim, maxSim] range used to
     * normalise cosine similarities for semantic attraction.
     */
    private static double[] computeSimRange(List<NodeData> nodes) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (NodeData nd : nodes) {
            for (Neighbor nb : nd.getNeighbors()) {
                double s = nb.getSimilarity();
                if (s < min) min = s;
                if (s > max) max = s;
            }
        }
        if (min == Double.MAX_VALUE) { return new double[]{0.0, 1.0}; } // no edges
        return new double[]{min, max};
    }

    /**
     * Wraps current positions into minimal {@link CelestialBody} stubs so that
     * {@link OverlapResolver} can mutate their positions.
     */
    private static List<CelestialBody> buildBodies(List<NodeData> nodes,
                                                    Map<String, Vector2D> pos) {
        List<CelestialBody> bodies = new ArrayList<>(nodes.size());
        for (NodeData nd : nodes) {
            bodies.add(new PositionBody(nd.getNoteId(),
                                        pos.get(nd.getNoteId()),
                                        DEFAULT_NODE_RADIUS));
        }
        return bodies;
    }

    // -------------------------------------------------------------------------
    // Inner class — minimal CelestialBody for OverlapResolver
    // -------------------------------------------------------------------------

    /**
     * Lightweight {@link CelestialBody} stub that carries only an id, a mutable
     * position and a radius.  Used exclusively to bridge {@link OverlapResolver}.
     */
    private static final class PositionBody extends CelestialBody {
        PositionBody(String id, Vector2D pos, double radius) {
            super(id, pos, radius, Color.WHITE);
        }

        @Override public void draw(Graphics2D g, double zoom) { /* no-op */ }
        @Override public String getTooltipText() { return id; }
    }
}
