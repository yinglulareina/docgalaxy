package com.docgalaxy.ui.canvas.layer;

import com.docgalaxy.model.celestial.Star;
import com.docgalaxy.ui.ThemeManager;
import com.docgalaxy.util.AppConstants;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
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
import java.util.Collections;
import java.util.List;

/**
 * Renders the star layer of the DocGalaxy canvas.
 *
 * <h3>Pipeline per star</h3>
 * <ol>
 *   <li><b>Viewport culling</b> — stars whose screen bounding circle (including
 *       glow radius) lies entirely outside the clip bounds are skipped.</li>
 *   <li><b>LOD selection</b>
 *     <ul>
 *       <li>Screen radius {@literal <} 2 px → single pixel dot.</li>
 *       <li>Screen radius 2 – 5 px → plain filled circle in sector colour.</li>
 *       <li>Screen radius {@literal >} 5 px → full 3-layer:
 *         <ul>
 *           <li>Glow — {@link RadialGradientPaint} at radius × 3, peak alpha 40.</li>
 *           <li>Body — radial gradient white → sectorColor → sectorColor.darker().</li>
 *           <li>Label — note filename below the star, visible when
 *               zoom {@literal >} {@link AppConstants#LABEL_SHOW_THRESHOLD} with a
 *               linear alpha fade-in over the next 0.2 zoom units.</li>
 *         </ul>
 *       </li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>{@link #needsRepaint()} always returns {@code true} so the canvas is
 * re-evaluated every repaint cycle (positions change with layout updates).
 */
public final class StarLayer implements RenderLayer {

    /** Zoom multiplier for the outer edge of the glow ring. */
    static final double GLOW_RADIUS_FACTOR = 4.0;

    /** Peak alpha value for the glow (0-255). */
    static final int GLOW_ALPHA = 80;

    /** Width of the label fade-in window above {@link AppConstants#LABEL_SHOW_THRESHOLD}. */
    static final double LABEL_FADE_RANGE = 0.2;

    /** Screen-radius boundary below which only a single pixel is drawn. */
    static final double LOD_PIXEL_MAX    = 2.0;

    /** Screen-radius boundary below which only a simple circle is drawn. */
    static final double LOD_SIMPLE_MAX   = 3.0;

    private final List<Star> stars;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a {@code StarLayer} backed by the given list.
     * The list is referenced (not copied) so updates to it are reflected on
     * the next repaint without rebuilding the layer.
     *
     * @param stars mutable list of stars to render; must not be {@code null}
     * @throws IllegalArgumentException if {@code stars} is {@code null}
     */
    public StarLayer(List<Star> stars) {
        if (stars == null) throw new IllegalArgumentException("stars must not be null");
        this.stars = stars;
    }

    // -------------------------------------------------------------------------
    // RenderLayer
    // -------------------------------------------------------------------------

    /**
     * Renders all visible stars with LOD-based detail, applying viewport
     * culling to skip stars outside the current clip region.
     *
     * @param g               graphics context (screen space)
     * @param cameraTransform world-to-screen affine transform
     * @param zoom            current zoom level (used for LOD and label fade)
     */
    @Override
    public void render(Graphics2D g, AffineTransform cameraTransform, double zoom) {
        if (stars.isEmpty()) return;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);

        double offsetX = cameraTransform.getTranslateX();
        double offsetY = cameraTransform.getTranslateY();
        Rectangle clip = g.getClipBounds();  // may be null (off-screen render)

        // Save graphics state once, restore once after all stars
        Composite savedComposite = g.getComposite();
        Color     savedColor     = g.getColor();
        Font      savedFont      = g.getFont();

        try {
            for (Star star : stars) {
                double wx = star.getPosition().getX();
                double wy = star.getPosition().getY();
                double sx = wx * zoom + offsetX;
                double sy = wy * zoom + offsetY;
                double sr = star.getRadius() * zoom;   // screen radius

                // Viewport culling — include glow margin
                if (clip != null && isCulled(sx, sy, sr * GLOW_RADIUS_FACTOR, clip)) {
                    continue;
                }

                if (sr < LOD_PIXEL_MAX) {
                    renderPixel(g, sx, sy, star.getColor());
                } else if (sr <= LOD_SIMPLE_MAX) {
                    renderSimple(g, sx, sy, sr, star.getColor());
                } else {
                    renderFull(g, sx, sy, sr, zoom, star);
                }
            }
        } finally {
            g.setComposite(savedComposite);
            g.setColor(savedColor);
            g.setFont(savedFont);
        }
    }

    /**
     * Always returns {@code true}: star positions change as the layout evolves.
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

    /** Returns the backing star list. */
    List<Star> getStars() {
        return Collections.unmodifiableList(stars);
    }

    // -------------------------------------------------------------------------
    // Private — LOD renderers
    // -------------------------------------------------------------------------

    /** Single 1-px dot in the star's sector colour. */
    private static void renderPixel(Graphics2D g, double sx, double sy, Color color) {
        g.setColor(color);
        g.fillRect((int) Math.round(sx), (int) Math.round(sy), 1, 1);
    }

    /** Filled circle in the star's sector colour — no glow, no label. */
    private static void renderSimple(Graphics2D g,
                                     double sx, double sy, double sr, Color color) {
        int ix = (int) Math.round(sx - sr);
        int iy = (int) Math.round(sy - sr);
        int id = (int) Math.round(sr * 2);
        g.setColor(color);
        g.fillOval(ix, iy, id, id);
    }

    /**
     * Full 3-layer rendering: glow halo → body gradient → label.
     */
    private static void renderFull(Graphics2D g,
                                   double sx, double sy, double sr,
                                   double zoom, Star star) {
        Color sectorColor = star.getColor();
        Point2D centre    = new Point2D.Double(sx, sy);

        // ── 1. Glow ─────────────────────────────────────────────────────────
        float glowR = (float) (sr * GLOW_RADIUS_FACTOR);
        if (glowR > 0) {
            Color peakGlow = new Color(sectorColor.getRed(),
                                       sectorColor.getGreen(),
                                       sectorColor.getBlue(),
                                       GLOW_ALPHA);
            Color edgeGlow = new Color(sectorColor.getRed(),
                                       sectorColor.getGreen(),
                                       sectorColor.getBlue(), 0);
            RadialGradientPaint glowPaint = new RadialGradientPaint(
                    centre, glowR,
                    new float[]{0f, 1f},
                    new Color[]{peakGlow, edgeGlow},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE);
            g.setPaint(glowPaint);
            int gx = (int) Math.round(sx - glowR);
            int gy = (int) Math.round(sy - glowR);
            int gd = (int) Math.round(glowR * 2);
            g.fillOval(gx, gy, gd, gd);
        }

        // ── 2. Body gradient ────────────────────────────────────────────────
        float bodyR = Math.max(1f, (float) sr);
        Color bodyEdge = sectorColor.darker();
        RadialGradientPaint bodyPaint = new RadialGradientPaint(
                centre, bodyR,
                new float[]{0f, 0.3f, 1f},
                new Color[]{Color.WHITE, sectorColor, bodyEdge},
                MultipleGradientPaint.CycleMethod.NO_CYCLE);
        g.setPaint(bodyPaint);
        int bx = (int) Math.round(sx - sr);
        int by = (int) Math.round(sy - sr);
        int bd = (int) Math.round(sr * 2);
        g.fillOval(bx, by, bd, bd);

        // ── 3. Label ─────────────────────────────────────────────────────────
        if (zoom > AppConstants.LABEL_SHOW_THRESHOLD) {
            float alpha = (float) Math.min(1.0,
                    (zoom - AppConstants.LABEL_SHOW_THRESHOLD) / LABEL_FADE_RANGE);
            renderLabel(g, sx, sy, sr, alpha, star.getNote().getFileName());
        }
    }

    /** Draws the note filename below the star body with given alpha. */
    private static void renderLabel(Graphics2D g,
                                    double sx, double sy, double sr,
                                    float alpha, String text) {
        g.setFont(ThemeManager.FONT_SMALL);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setColor(ThemeManager.TEXT_PRIMARY);

        FontMetrics fm  = g.getFontMetrics();
        int textW       = fm.stringWidth(text);
        int labelX      = (int) Math.round(sx - textW / 2.0);
        int labelY      = (int) Math.round(sy + sr + fm.getAscent() + 2);
        g.drawString(text, labelX, labelY);
    }

    // -------------------------------------------------------------------------
    // Private — culling helper
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if a circle at {@code (sx, sy)} with the given
     * {@code margin} radius is entirely outside {@code clip}.
     */
    private static boolean isCulled(double sx, double sy, double margin, Rectangle clip) {
        return sx + margin < clip.x
            || sx - margin > clip.x + clip.width
            || sy + margin < clip.y
            || sy - margin > clip.y + clip.height;
    }
}
