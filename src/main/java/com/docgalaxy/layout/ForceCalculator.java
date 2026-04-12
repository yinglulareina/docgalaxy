package com.docgalaxy.layout;

import com.docgalaxy.model.Vector2D;

/**
 * Pure-function force calculator for force-directed graph layouts.
 *
 * <p>All methods are stateless and operate only on {@link Vector2D} positions.
 * The caller is responsible for holding mutable node positions and accumulating
 * forces from multiple interactions before applying them each iteration.
 *
 * <p>Formulas follow the Fruchterman–Reingold (FR) conventions:
 * <ul>
 *   <li><b>K</b> — the "optimal distance" parameter, typically proportional to
 *       {@code sqrt(area / n)}.</li>
 *   <li>Repulsion decays as {@code K²/d}: nodes far apart feel almost nothing;
 *       nodes that overlap get a large separating force.</li>
 *   <li>Semantic attraction is a spring (Hooke's law) whose rest length and
 *       stiffness are driven by normalised cosine similarity.</li>
 *   <li>Gravity is a uniform centripetal pull that prevents the layout from
 *       drifting off-screen.</li>
 * </ul>
 */
public final class ForceCalculator {

    /** Minimum separation used as a guard against division by zero. */
    private static final double MIN_DISTANCE = 1e-6;

    private ForceCalculator() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Computes the FR repulsion force that node at {@code posA} exerts on
     * itself due to node at {@code posB}.
     *
     * <p>Formula: {@code f = K² / d}, directed <em>away</em> from {@code posB}.
     * As a vector: {@code (posA − posB) * K² / d²}.
     *
     * @param posA position of the node receiving the force
     * @param posB position of the node exerting the repulsion
     * @param K    optimal-distance parameter (must be &gt; 0)
     * @return repulsion force vector applied to {@code posA};
     *         {@link Vector2D#ZERO} if the positions coincide
     * @throws IllegalArgumentException if K &le; 0 or either position is null
     */
    public static Vector2D repulsion(Vector2D posA, Vector2D posB, double K) {
        if (posA == null) throw new IllegalArgumentException("posA must not be null");
        if (posB == null) throw new IllegalArgumentException("posB must not be null");
        if (K <= 0)       throw new IllegalArgumentException("K must be > 0");

        Vector2D delta = posA.subtract(posB);
        double dist = delta.magnitude();
        if (dist < MIN_DISTANCE) return Vector2D.ZERO;

        // (delta / dist) * K²/dist  =  delta * K² / dist²
        double scale = (K * K) / (dist * dist);
        return delta.scale(scale);
    }

    /**
     * Computes the semantic spring force between two nodes whose cosine
     * similarity is known.
     *
     * <p>The similarity is first normalised to {@code [0, 1]}:
     * <pre>
     *   sNorm = clamp((similarity − minSim) / (maxSim − minSim), 0, 1)
     * </pre>
     * Then a Hooke-style spring is applied:
     * <ul>
     *   <li>Rest length: {@code idealLength = K * (1 − sNorm)}
     *       — perfectly similar nodes want zero separation; least similar
     *       nodes want to be {@code K} apart.</li>
     *   <li>Stiffness:   {@code stiffness = sNorm}
     *       — similar nodes pull harder; dissimilar nodes barely pull.</li>
     *   <li>Force:       {@code stiffness * (dist − idealLength)} in the
     *       direction from {@code posA} toward {@code posB}.</li>
     * </ul>
     *
     * @param posA       position of node A
     * @param posB       position of node B
     * @param similarity raw cosine similarity between A and B
     * @param minSim     minimum similarity in the dataset (used for normalisation)
     * @param maxSim     maximum similarity in the dataset (used for normalisation)
     * @param K          optimal-distance parameter (must be &gt; 0)
     * @return attraction force vector applied to {@code posA};
     *         {@link Vector2D#ZERO} if the nodes coincide or stiffness is zero
     * @throws IllegalArgumentException if K &le; 0 or either position is null
     */
    public static Vector2D semanticAttraction(Vector2D posA, Vector2D posB,
                                               double similarity,
                                               double minSim, double maxSim,
                                               double K) {
        if (posA == null) throw new IllegalArgumentException("posA must not be null");
        if (posB == null) throw new IllegalArgumentException("posB must not be null");
        if (K <= 0)       throw new IllegalArgumentException("K must be > 0");

        // Normalise similarity to [0, 1]; guard against degenerate range
        double range = maxSim - minSim;
        double sNorm = (range < MIN_DISTANCE) ? 0.5
                : clamp((similarity - minSim) / range, 0.0, 1.0);

        double stiffness = sNorm;
        if (stiffness < MIN_DISTANCE) return Vector2D.ZERO; // no attraction

        Vector2D delta = posB.subtract(posA); // direction A → B
        double dist = delta.magnitude();
        if (dist < MIN_DISTANCE) return Vector2D.ZERO;

        double idealLength = K * (1.0 - sNorm);
        double displacement = dist - idealLength;

        // Spring force: stiffness * displacement * unit_direction
        // = stiffness * displacement / dist * delta
        return delta.scale(stiffness * displacement / dist);
    }

    /**
     * Computes the gravity force pulling a node toward the layout centroid.
     *
     * <p>Formula: {@code f = g * (centroid − pos)} — a linear (Hookean) pull
     * proportional to distance.  A small {@code g} (e.g.
     * {@link com.docgalaxy.util.AppConstants#GRAVITY_CONSTANT}) keeps the
     * layout centred without distorting local structure.
     *
     * @param pos      current position of the node
     * @param centroid target centroid (typically the canvas centre)
     * @param g        gravity constant (must be &ge; 0)
     * @return force vector directed from {@code pos} toward {@code centroid};
     *         {@link Vector2D#ZERO} if g is zero or the node is at the centroid
     * @throws IllegalArgumentException if g &lt; 0 or either argument is null
     */
    public static Vector2D gravity(Vector2D pos, Vector2D centroid, double g) {
        if (pos      == null) throw new IllegalArgumentException("pos must not be null");
        if (centroid == null) throw new IllegalArgumentException("centroid must not be null");
        if (g < 0)            throw new IllegalArgumentException("g must be >= 0");

        if (g == 0.0) return Vector2D.ZERO;

        // (centroid − pos) * g
        return centroid.subtract(pos).scale(g);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
