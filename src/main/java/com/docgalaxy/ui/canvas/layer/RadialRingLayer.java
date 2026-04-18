package com.docgalaxy.ui.canvas.layer;

import com.docgalaxy.model.Vector2D;
import com.docgalaxy.ui.ThemeManager;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;

/**
 * Draws concentric reference circles for the Radial layout.
 *
 * <p>One circle is drawn per BFS ring at radius {@code ring × ringSpacing}
 * from the centre node's world-space position.  The circles use
 * {@link ThemeManager#EDGE_DEFAULT} (rgba 255,255,255,0.08) and a 0.5 px
 * hairline stroke — identical to the KNN edge style — so they read as subtle
 * scaffolding rather than data.
 *
 * <p>This layer is added only when the Radial layout is active; it is removed
 * automatically when the user switches to another layout (layers are rebuilt on
 * every layout switch).
 */
public final class RadialRingLayer implements RenderLayer {

    private static final BasicStroke RING_STROKE = new BasicStroke(
            0.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private final Vector2D center;
    private final int      ringCount;
    private final double   ringSpacing;

    /**
     * Creates a ring layer for the given radial layout state.
     *
     * @param center      world-space position of the centre node; must not be {@code null}
     * @param ringCount   number of concentric rings to draw (must be &ge; 0)
     * @param ringSpacing world-space distance between consecutive rings (must be &gt; 0)
     * @throws IllegalArgumentException if {@code center} is {@code null} or
     *                                  {@code ringCount} / {@code ringSpacing} are invalid
     */
    public RadialRingLayer(Vector2D center, int ringCount, double ringSpacing) {
        if (center      == null) throw new IllegalArgumentException("center must not be null");
        if (ringCount   <  0)    throw new IllegalArgumentException("ringCount must be >= 0");
        if (ringSpacing <= 0)    throw new IllegalArgumentException("ringSpacing must be > 0");
        this.center      = center;
        this.ringCount   = ringCount;
        this.ringSpacing = ringSpacing;
    }

    /**
     * Draws the concentric reference circles.  No-op when {@code ringCount == 0}.
     *
     * @param g               graphics context (screen space)
     * @param cameraTransform world-to-screen affine transform
     * @param zoom            current zoom level
     */
    @Override
    public void render(Graphics2D g, AffineTransform cameraTransform, double zoom) {
        if (ringCount == 0) return;

        double offsetX = cameraTransform.getTranslateX();
        double offsetY = cameraTransform.getTranslateY();

        double cx = center.getX() * zoom + offsetX;
        double cy = center.getY() * zoom + offsetY;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);

        Color  savedColor  = g.getColor();
        Stroke savedStroke = g.getStroke();

        try {
            g.setColor(ThemeManager.EDGE_DEFAULT);
            g.setStroke(RING_STROKE);

            for (int ring = 1; ring <= ringCount; ring++) {
                double r = ring * ringSpacing * zoom;
                g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            }
        } finally {
            g.setColor(savedColor);
            g.setStroke(savedStroke);
        }
    }

    /**
     * Returns {@code false}: ring geometry is fixed once the layout is computed.
     *
     * @return {@code false}
     */
    @Override
    public boolean needsRepaint() {
        return false;
    }
}
