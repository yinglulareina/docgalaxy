package com.docgalaxy.layout;

import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.CelestialBody;
import com.docgalaxy.util.AppConstants;

import java.util.List;

/**
 * Post-layout overlap resolver that separates nodes (stars, nebulae, etc.)
 * whose visual circles intersect after the force-directed engine has converged.
 *
 * <h3>Algorithm</h3>
 * <p>The resolver runs a fixed number of passes (default {@value #DEFAULT_PASSES}).
 * In each pass every ordered pair {@code (i, j)} with {@code i < j} is examined:
 * <ol>
 *   <li>Compute the Euclidean distance between the two centres.</li>
 *   <li>If that distance is less than
 *       {@code radiusA + radiusB + }{@link AppConstants#OVERLAP_PADDING},
 *       an overlap exists.</li>
 *   <li>Each node is pushed half of the penetration depth along the line
 *       connecting their centres, away from the other node.  When centres
 *       coincide exactly the push is along the positive x-axis (a deterministic
 *       tiebreak).</li>
 * </ol>
 *
 * <p>Multiple passes are needed because resolving one pair may re-introduce a
 * slight overlap with a third node; 2–3 passes reduce visible overlap to an
 * imperceptible residual for typical galaxy sizes (≤ 1 000 nodes).
 *
 * <p>This class is <em>stateless</em> — all mutable state lives in the
 * {@link CelestialBody} objects passed by the caller.
 */
public final class OverlapResolver {

    /** Default number of resolution passes. */
    static final int DEFAULT_PASSES = 3;

    /** Guard against division by zero when two centres coincide. */
    private static final double MIN_DISTANCE = 1e-6;

    /** Unit vector used as a tiebreak when two centres are exactly coincident. */
    private static final Vector2D TIEBREAK_DIRECTION = new Vector2D(1.0, 0.0);

    private final int passes;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /** Production constructor — runs {@value #DEFAULT_PASSES} passes. */
    public OverlapResolver() {
        this(DEFAULT_PASSES);
    }

    /**
     * Package-private constructor for tests — allows a custom pass count.
     *
     * @param passes number of resolution passes (must be &ge; 1)
     */
    OverlapResolver(int passes) {
        if (passes < 1) throw new IllegalArgumentException("passes must be >= 1");
        this.passes = passes;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Resolves overlaps between all nodes in the list by mutating their
     * {@link CelestialBody#setPosition positions} in-place.
     *
     * <p>Runs {@link #DEFAULT_PASSES} (or the constructor-supplied value) passes
     * over all {@code O(n²)} pairs.  Nodes that do not overlap are left untouched.
     *
     * @param nodes the nodes to process; must not be {@code null};
     *              individual elements must not be {@code null}
     * @throws IllegalArgumentException if {@code nodes} is {@code null}
     */
    public void resolve(List<? extends CelestialBody> nodes) {
        if (nodes == null) throw new IllegalArgumentException("nodes must not be null");
        if (nodes.size() < 2) return; // nothing to resolve

        for (int pass = 0; pass < passes; pass++) {
            runPass(nodes);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static void runPass(List<? extends CelestialBody> nodes) {
        int n = nodes.size();
        for (int i = 0; i < n - 1; i++) {
            CelestialBody a = nodes.get(i);
            for (int j = i + 1; j < n; j++) {
                CelestialBody b = nodes.get(j);
                pushApart(a, b);
            }
        }
    }

    /**
     * If {@code a} and {@code b} overlap (accounting for their radii and
     * {@link AppConstants#OVERLAP_PADDING}), shifts each node half the
     * penetration depth away from the other.
     */
    private static void pushApart(CelestialBody a, CelestialBody b) {
        double minDist = a.getRadius() + b.getRadius() + AppConstants.OVERLAP_PADDING;

        Vector2D delta = a.getPosition().subtract(b.getPosition()); // A − B
        double dist = delta.magnitude();

        if (dist >= minDist) return; // no overlap

        double penetration = minDist - dist;
        double halfPush    = penetration / 2.0;

        Vector2D direction = (dist < MIN_DISTANCE)
                ? TIEBREAK_DIRECTION          // coincident centres — deterministic tiebreak
                : delta.scale(1.0 / dist);   // unit vector A → away from B

        a.setPosition(a.getPosition().add(direction.scale( halfPush)));
        b.setPosition(b.getPosition().add(direction.scale(-halfPush)));
    }
}
