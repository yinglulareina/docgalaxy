package com.docgalaxy.ui.canvas.layer;

import com.docgalaxy.model.celestial.Nebula;
import com.docgalaxy.ui.ThemeManager;
import com.docgalaxy.util.AppConstants;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.List;

/**
 * Renders nebula cloud overlays — the translucent sector-region blobs that
 * are visible only at low zoom levels to give a sense of spatial grouping.
 *
 * <h3>Visibility</h3>
 * <ul>
 *   <li>When {@code zoom > }{@link AppConstants#NEBULA_SHOW_THRESHOLD} (0.6)
 *       the entire layer is skipped — nebulae are replaced by individual stars
 *       at closer zoom.</li>
 *   <li>Layer alpha fades linearly from fully opaque at zoom ≤ 0.3 to fully
 *       transparent at zoom = 0.6: {@code alpha = clamp((0.6 − zoom) / 0.3, 0, 1)}.</li>
 * </ul>
 *
 * <h3>Per-nebula rendering</h3>
 * Three overlapping elliptical blobs are drawn using {@link RadialGradientPaint}
 * (sector colour at centre → transparent at edge), each at a slightly different
 * offset and aspect ratio to produce an organic cloud shape.  A sector label is
 * drawn at the world-space centre of the nebula.
 *
 * <p>{@link #needsRepaint()} returns {@code true}: nebulae move with the layout.
 */
public final class NebulaLayer implements RenderLayer {

    /** Zoom level above which this layer is completely invisible. */
    static final double SHOW_THRESHOLD = AppConstants.NEBULA_SHOW_THRESHOLD; // 0.6

    /** Zoom level at or below which the layer is fully opaque. */
    static final double FULL_ALPHA_ZOOM = 0.3;

    /** Peak alpha (0–255) for the sector colour at the blob centre. */
    static final int BLOB_PEAK_ALPHA = 55;

    /**
     * Three blob descriptors: {offsetXFraction, offsetYFraction,
     * radiusFraction, xScale, yScale} — all relative to nebula screen radius.
     */
    private static final double[][] BLOBS = {
        //  dxF   dyF   rF    xS    yS
        {  0.00,  0.00, 1.00, 1.00, 0.60 },   // main cloud
        {  0.28,  0.12, 0.72, 0.82, 0.52 },   // right lobe
        { -0.22,  0.18, 0.66, 0.78, 0.56 },   // left lobe
    };

    private final List<Nebula> nebulae;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a {@code NebulaLayer} backed by the given list.
     * The list is referenced (not copied) so mutations to it are reflected on
     * the next repaint without rebuilding the layer.
     *
     * @param nebulae mutable list of nebulae to render; must not be {@code null}
     * @throws IllegalArgumentException if {@code nebulae} is {@code null}
     */
    public NebulaLayer(List<Nebula> nebulae) {
        if (nebulae == null) throw new IllegalArgumentException("nebulae must not be null");
        this.nebulae = nebulae;
    }

    // -------------------------------------------------------------------------
    // RenderLayer
    // -------------------------------------------------------------------------

    /**
     * Renders all nebulae at an alpha determined by the current zoom level.
     * The entire layer is skipped when {@code zoom > }{@value #SHOW_THRESHOLD}.
     *
     * @param g               graphics context (screen space)
     * @param cameraTransform world-to-screen affine transform
     * @param zoom            current zoom level
     */
    @Override
    public void render(Graphics2D g, AffineTransform cameraTransform, double zoom) {
        if (zoom > SHOW_THRESHOLD || nebulae.isEmpty()) return;

        // Layer-wide alpha: 1.0 at zoom≤0.3, fades to 0.0 at zoom=0.6
        float layerAlpha = (float) Math.min(1.0,
                Math.max(0.0, (SHOW_THRESHOLD - zoom) / (SHOW_THRESHOLD - FULL_ALPHA_ZOOM)));

        double offsetX = cameraTransform.getTranslateX();
        double offsetY = cameraTransform.getTranslateY();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                           RenderingHints.VALUE_RENDER_QUALITY);

        Composite savedComposite = g.getComposite();
        Font      savedFont      = g.getFont();
        Color     savedColor     = g.getColor();
        AffineTransform savedTx  = g.getTransform();

        try {
            for (Nebula nebula : nebulae) {
                double sx = nebula.getPosition().getX() * zoom + offsetX;
                double sy = nebula.getPosition().getY() * zoom + offsetY;
                double sr = nebula.getRadius() * zoom;
                if (sr < 1.0) continue;  // too small to see

                // Combine layer alpha with the nebula's own alpha field
                float alpha = layerAlpha * nebula.getAlpha();
                if (alpha <= 0f) continue;

                renderBlobs(g, savedTx, sx, sy, sr, alpha, nebula.getColor());
                renderLabel(g, sx, sy, alpha, nebula.getSector().getLabel());
            }
        } finally {
            g.setComposite(savedComposite);
            g.setFont(savedFont);
            g.setColor(savedColor);
            g.setTransform(savedTx);
        }
    }

    /**
     * Always returns {@code true}: nebulae reposition with layout updates.
     *
     * @return {@code true}
     */
    @Override
    public boolean needsRepaint() {
        return true;
    }

    // -------------------------------------------------------------------------
    // Package-private accessor (tests)
    // -------------------------------------------------------------------------

    /** Returns an unmodifiable view of the backing nebula list. */
    List<Nebula> getNebulae() {
        return Collections.unmodifiableList(nebulae);
    }

    // -------------------------------------------------------------------------
    // Private — blob rendering
    // -------------------------------------------------------------------------

    /**
     * Draws three overlapping elliptical blobs centred near {@code (sx, sy)}
     * using a sector-colour {@link RadialGradientPaint} per blob.
     */
    private static void renderBlobs(Graphics2D g, AffineTransform baseTx,
                                    double sx, double sy, double sr,
                                    float alpha, Color sectorColor) {
        int peakAlpha = Math.round(BLOB_PEAK_ALPHA * alpha);
        if (peakAlpha <= 0) return;

        Color peak = new Color(sectorColor.getRed(),
                               sectorColor.getGreen(),
                               sectorColor.getBlue(),
                               peakAlpha);
        Color edge = new Color(sectorColor.getRed(),
                               sectorColor.getGreen(),
                               sectorColor.getBlue(), 0);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));

        for (double[] blob : BLOBS) {
            double bx = sx + blob[0] * sr;
            double by = sy + blob[1] * sr;
            double br = blob[2] * sr;
            double xScale = blob[3];
            double yScale = blob[4];

            // Build an ellipse by translating to blob centre, applying a
            // non-uniform scale, then drawing a circular RadialGradientPaint.
            AffineTransform blobTx = new AffineTransform(baseTx);
            blobTx.translate(bx, by);
            blobTx.scale(xScale, yScale);
            g.setTransform(blobTx);

            float r = (float) br;
            if (r <= 0) continue;

            RadialGradientPaint paint = new RadialGradientPaint(
                    new Point2D.Double(0, 0), r,
                    new float[]{0f, 0.55f, 1f},
                    new Color[]{peak, peak, edge},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE);
            g.setPaint(paint);

            int d = (int) Math.ceil(r * 2);
            g.fillOval(-(int) Math.ceil(r), -(int) Math.ceil(r), d, d);
        }

        // Restore to base (pre-blob) transform so the caller's save/restore works
        g.setTransform(baseTx);
    }

    // -------------------------------------------------------------------------
    // Private — label rendering
    // -------------------------------------------------------------------------

    /** Draws the sector label centred at the nebula's screen position. */
    private static void renderLabel(Graphics2D g,
                                    double sx, double sy,
                                    float alpha, String label) {
        if (label == null || label.isBlank()) return;

        g.setFont(ThemeManager.FONT_SECTOR_LABEL);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setColor(ThemeManager.TEXT_SECONDARY);

        FontMetrics fm = g.getFontMetrics();
        int textW = fm.stringWidth(label);
        int labelX = (int) Math.round(sx - textW / 2.0);
        int labelY = (int) Math.round(sy + fm.getAscent() / 2.0);
        g.drawString(label, labelX, labelY);
    }
}
