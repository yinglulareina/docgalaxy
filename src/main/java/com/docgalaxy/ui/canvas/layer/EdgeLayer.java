package com.docgalaxy.ui.canvas.layer;

import com.docgalaxy.model.Edge;
import com.docgalaxy.model.Vector2D;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Renders the semantic-similarity edges between stars.
 *
 * <h3>Visibility</h3>
 * The layer is completely skipped when {@code zoom < }{@value #SHOW_THRESHOLD}:
 * edges clutter the view at galaxy scale and are only useful when the user has
 * zoomed in enough to distinguish individual stars.
 *
 * <h3>Per-edge rendering</h3>
 * Each {@link Edge} is drawn as a straight line between the two endpoint
 * positions (looked up by ID from the positions map) using:
 * <ul>
 *   <li>{@link BasicStroke}({@value #STROKE_WIDTH}) — thin hairline</li>
 *   <li>White with {@code alpha = clamp(similarity × 40, 0, 255)} — more
 *       similar notes produce a more visible connection</li>
 * </ul>
 * Edges whose endpoint IDs are absent from the positions map are silently
 * skipped so a stale edge list never crashes the renderer.
 *
 * <p>{@link #needsRepaint()} returns {@code true}: edge endpoints move with
 * the layout engine.
 */
public final class EdgeLayer implements RenderLayer {

    /** Zoom level below which this layer is completely skipped. */
    static final double SHOW_THRESHOLD = 0.4;

    /** Stroke width for all edges (screen pixels, independent of zoom). */
    static final float STROKE_WIDTH = 0.5f;

    /** Similarity multiplier → alpha channel (0–255). */
    static final double ALPHA_SCALE = 40.0;

    private static final BasicStroke EDGE_STROKE = new BasicStroke(
            STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private final List<Edge>             edges;
    private final Map<String, Vector2D>  positions;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates an {@code EdgeLayer} backed by the given live collections.
     * Both the edge list and the positions map are referenced, not copied,
     * so updates are reflected on the next repaint.
     *
     * @param edges     list of semantic edges to draw; must not be {@code null}
     * @param positions map from star/node ID → world-space position;
     *                  must not be {@code null}
     * @throws IllegalArgumentException if either argument is {@code null}
     */
    public EdgeLayer(List<Edge> edges, Map<String, Vector2D> positions) {
        if (edges     == null) throw new IllegalArgumentException("edges must not be null");
        if (positions == null) throw new IllegalArgumentException("positions must not be null");
        this.edges     = edges;
        this.positions = positions;
    }

    // -------------------------------------------------------------------------
    // RenderLayer
    // -------------------------------------------------------------------------

    /**
     * Draws all edges as semi-transparent hairlines.  The layer is a no-op
     * when {@code zoom < }{@value #SHOW_THRESHOLD} or the edge list is empty.
     *
     * @param g               graphics context (screen space)
     * @param cameraTransform world-to-screen affine transform
     * @param zoom            current zoom level
     */
    @Override
    public void render(Graphics2D g, AffineTransform cameraTransform, double zoom) {
        if (zoom < SHOW_THRESHOLD || edges.isEmpty()) return;

        double offsetX = cameraTransform.getTranslateX();
        double offsetY = cameraTransform.getTranslateY();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);

        Composite savedComposite = g.getComposite();
        Color     savedColor     = g.getColor();
        Stroke    savedStroke    = g.getStroke();

        try {
            g.setStroke(EDGE_STROKE);

            for (Edge edge : edges) {
                Vector2D from = positions.get(edge.getFromId());
                Vector2D to   = positions.get(edge.getToId());
                if (from == null || to == null) continue;

                int alpha = (int) Math.round(
                        Math.min(255.0, Math.max(0.0, edge.getSimilarity() * ALPHA_SCALE)));
                if (alpha == 0) continue;

                double x1 = from.getX() * zoom + offsetX;
                double y1 = from.getY() * zoom + offsetY;
                double x2 = to.getX()   * zoom + offsetX;
                double y2 = to.getY()   * zoom + offsetY;

                g.setColor(new Color(255, 255, 255, alpha));
                g.draw(new Line2D.Double(x1, y1, x2, y2));
            }
        } finally {
            g.setComposite(savedComposite);
            g.setColor(savedColor);
            g.setStroke(savedStroke);
        }
    }

    /**
     * Always returns {@code true}: edge endpoint positions change with layout.
     *
     * @return {@code true}
     */
    @Override
    public boolean needsRepaint() {
        return true;
    }

    // -------------------------------------------------------------------------
    // Package-private accessors (tests)
    // -------------------------------------------------------------------------

    /** Returns an unmodifiable view of the backing edge list. */
    List<Edge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    /** Returns an unmodifiable view of the backing positions map. */
    Map<String, Vector2D> getPositions() {
        return Collections.unmodifiableMap(positions);
    }
}
