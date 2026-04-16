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
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    /** Padding (px) added around each label bounding box for collision tests. */
    private static final int LABEL_PADDING = 10;

    /** Candidate Y offset (px) for down/up alternatives. */
    private static final int LABEL_OFFSET_Y = 30;

    /** Candidate X offset (px) for left/right alternatives. */
    private static final int LABEL_OFFSET_X = 50;

    /** Font size used when zoom ≥ 0.3 (zoom-in range). */
    private static final int FONT_SIZE_CLOSE = 13;

    /** Font size used when zoom < 0.3 (zoom-out / galaxy view). */
    private static final int FONT_SIZE_FAR = 16;

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

        // Choose label font size based on zoom level
        int fontSize = (zoom < FULL_ALPHA_ZOOM) ? FONT_SIZE_FAR : FONT_SIZE_CLOSE;
        Font labelFont = ThemeManager.FONT_SECTOR_LABEL.deriveFont(Font.BOLD, fontSize);

        Composite savedComposite = g.getComposite();
        Font      savedFont      = g.getFont();
        Color     savedColor     = g.getColor();
        AffineTransform savedTx  = g.getTransform();

        try {
            // ── Pass 1: collect visible nebulae, sorted by noteCount descending ──
            g.setFont(labelFont);
            FontMetrics fm = g.getFontMetrics();

            // Pair: [nebula, sx, sy, sr, alpha]
            record NebRecord(Nebula nebula, double sx, double sy, double sr, float alpha) {}
            List<NebRecord> visible = new ArrayList<>();
            for (Nebula nebula : nebulae) {
                double sx = nebula.getPosition().getX() * zoom + offsetX;
                double sy = nebula.getPosition().getY() * zoom + offsetY;
                double sr = nebula.getRadius() * zoom;
                if (sr < 1.0) continue;
                float alpha = layerAlpha * nebula.getAlpha();
                if (alpha <= 0f) continue;
                visible.add(new NebRecord(nebula, sx, sy, sr, alpha));
            }
            // Larger clusters render labels first (higher priority)
            visible.sort(Comparator.comparingInt(
                    (NebRecord r) -> r.nebula().getSector().getNoteCount()).reversed());

            // ── Pass 2: draw blobs for all (order doesn't matter for blobs) ────
            for (NebRecord rec : visible) {
                renderBlobs(g, savedTx, rec.sx(), rec.sy(), rec.sr(),
                            rec.alpha(), rec.nebula().getColor());
            }

            // ── Pass 3: place labels with collision detection ─────────────────
            List<Rectangle> placed = new ArrayList<>();
            for (NebRecord rec : visible) {
                String label = rec.nebula().getSector().getLabel();
                if (label == null || label.isBlank()) continue;

                int textW = fm.stringWidth(label);
                int textH = fm.getHeight();

                // Five candidate anchor positions (top-left of text bbox)
                int[][] candidates = {
                    { (int) Math.round(rec.sx() - textW / 2.0),
                      (int) Math.round(rec.sy() - textH / 2.0) },                    // centre
                    { (int) Math.round(rec.sx() - textW / 2.0),
                      (int) Math.round(rec.sy() - textH / 2.0) + LABEL_OFFSET_Y },   // below
                    { (int) Math.round(rec.sx() - textW / 2.0),
                      (int) Math.round(rec.sy() - textH / 2.0) - LABEL_OFFSET_Y },   // above
                    { (int) Math.round(rec.sx() - textW / 2.0) - LABEL_OFFSET_X,
                      (int) Math.round(rec.sy() - textH / 2.0) },                    // left
                    { (int) Math.round(rec.sx() - textW / 2.0) + LABEL_OFFSET_X,
                      (int) Math.round(rec.sy() - textH / 2.0) },                    // right
                };

                int bestX = candidates[0][0];
                int bestY = candidates[0][1];
                int bestOverlap = Integer.MAX_VALUE;

                for (int[] cand : candidates) {
                    Rectangle bbox = paddedRect(cand[0], cand[1], textW, textH);
                    int overlap = totalOverlap(bbox, placed);
                    if (overlap < bestOverlap) {
                        bestOverlap = overlap;
                        bestX = cand[0];
                        bestY = cand[1];
                        if (overlap == 0) break;   // perfect placement found
                    }
                }

                Rectangle chosen = paddedRect(bestX, bestY, textW, textH);
                placed.add(chosen);

                // Draw the label
                g.setFont(labelFont);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, rec.alpha()));
                g.setColor(ThemeManager.TEXT_SECONDARY);
                g.drawString(label, bestX, bestY + fm.getAscent());
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
    // Private — label collision helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a {@link Rectangle} expanded by {@value #LABEL_PADDING} px on
     * each side, used for overlap testing.
     */
    private static Rectangle paddedRect(int x, int y, int w, int h) {
        return new Rectangle(x - LABEL_PADDING, y - LABEL_PADDING,
                             w + LABEL_PADDING * 2, h + LABEL_PADDING * 2);
    }

    /**
     * Returns the total intersection area between {@code bbox} and every
     * rectangle in {@code placed}.  Zero means no overlap.
     */
    private static int totalOverlap(Rectangle bbox, List<Rectangle> placed) {
        int total = 0;
        for (Rectangle r : placed) {
            Rectangle inter = bbox.intersection(r);
            if (!inter.isEmpty()) {
                total += inter.width * inter.height;
            }
        }
        return total;
    }
}
