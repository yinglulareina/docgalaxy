package com.docgalaxy.ui.canvas.layer;

import com.docgalaxy.model.celestial.Star;
import com.docgalaxy.ui.ThemeManager;
import com.docgalaxy.util.AppConstants;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Concrete implementation of {@link OverlayLayer} for the DocGalaxy canvas.
 *
 * <h3>Highlight mode</h3>
 * When a non-empty highlight set is active:
 * <ol>
 *   <li>A black translucent mask ({@link AppConstants#SEARCH_MASK_ALPHA}) is
 *       painted over the entire viewport to dim non-highlighted stars.</li>
 *   <li>For every highlighted star a blue {@link RadialGradientPaint} glow halo
 *       at {@link AppConstants#SEARCH_GLOW_RADIUS_MULTIPLIER} × screen-radius
 *       is drawn, then the star body is re-drawn on top to "pop" it out of the
 *       mask.</li>
 * </ol>
 *
 * <h3>Navigation route</h3>
 * When a non-empty route list is active, consecutive route stars are connected
 * by a two-pass glowing path: a wide semi-transparent blue outer stroke and a
 * narrow brighter white inner stroke.
 *
 * <p>Both modes can be active simultaneously; the mask is drawn once regardless
 * of whether a route is also showing.
 *
 * <p>{@link #needsRepaint()} returns {@code true}: state changes trigger a
 * canvas repaint via {@link com.docgalaxy.ui.canvas.CanvasController}.
 */
public final class GalaxyOverlayLayer implements OverlayLayer {

    /** Alpha fraction for the dim mask (matches {@link AppConstants#SEARCH_MASK_ALPHA}). */
    static final float MASK_ALPHA = AppConstants.SEARCH_MASK_ALPHA;   // 100/255

    /** Glow radius = star screen-radius × this factor. */
    static final int GLOW_RADIUS_MULTIPLIER = AppConstants.SEARCH_GLOW_RADIUS_MULTIPLIER; // 5

    /** Peak alpha (0-255) for the highlight glow halo. */
    static final int GLOW_PEAK_ALPHA = 120;

    /** Outer route stroke width (screen pixels). */
    static final float ROUTE_STROKE_OUTER = 3.5f;

    /** Inner (core) route stroke width (screen pixels). */
    static final float ROUTE_STROKE_INNER = 1.5f;

    private static final BasicStroke STROKE_OUTER = new BasicStroke(
            ROUTE_STROKE_OUTER, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke STROKE_INNER = new BasicStroke(
            ROUTE_STROKE_INNER, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    // ── State updated via OverlayLayer API ────────────────────────────────────
    private Set<String>  highlightedIds    = Collections.emptySet();
    private List<String> navigationRoute   = Collections.emptyList();

    // ── Star lookup ───────────────────────────────────────────────────────────
    private final List<Star> stars;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a {@code GalaxyOverlayLayer} backed by the given star list.
     * The list is referenced (not copied) so live position updates are
     * reflected on the next repaint.
     *
     * @param stars the full star list used for ID → position lookup;
     *              must not be {@code null}
     * @throws IllegalArgumentException if {@code stars} is {@code null}
     */
    public GalaxyOverlayLayer(List<Star> stars) {
        if (stars == null) throw new IllegalArgumentException("stars must not be null");
        this.stars = stars;
    }

    // -------------------------------------------------------------------------
    // OverlayLayer API
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * A {@code null} or empty set clears the highlight.
     */
    @Override
    public void setHighlightedNotes(Set<String> noteIds) {
        this.highlightedIds = (noteIds != null) ? noteIds : Collections.emptySet();
    }

    /**
     * {@inheritDoc}
     * A {@code null} or empty list clears the route.
     */
    @Override
    public void setNavigationRoute(List<String> noteIds) {
        this.navigationRoute = (noteIds != null) ? noteIds : Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // RenderLayer
    // -------------------------------------------------------------------------

    /**
     * Renders the highlight mask + glows and/or the navigation route path,
     * depending on which are currently active.
     */
    @Override
    public void render(Graphics2D g, AffineTransform cameraTransform, double zoom) {
        boolean hasHighlight = !highlightedIds.isEmpty();
        boolean hasRoute     = navigationRoute.size() >= 2;
        if (!hasHighlight && !hasRoute) return;

        double offsetX = cameraTransform.getTranslateX();
        double offsetY = cameraTransform.getTranslateY();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);

        Composite savedComposite = g.getComposite();
        Color     savedColor     = g.getColor();
        Stroke    savedStroke    = g.getStroke();

        try {
            if (hasHighlight) {
                renderHighlight(g, offsetX, offsetY, zoom);
            }
            if (hasRoute) {
                renderRoute(g, offsetX, offsetY, zoom);
            }
        } finally {
            g.setComposite(savedComposite);
            g.setColor(savedColor);
            g.setStroke(savedStroke);
        }
    }

    /**
     * Always returns {@code true}: overlay state changes require a repaint.
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

    Set<String>  getHighlightedIds()  { return Collections.unmodifiableSet(highlightedIds); }
    List<String> getNavigationRoute() { return Collections.unmodifiableList(navigationRoute); }
    List<Star>   getStars()           { return Collections.unmodifiableList(stars); }

    // -------------------------------------------------------------------------
    // Private — highlight rendering
    // -------------------------------------------------------------------------

    private void renderHighlight(Graphics2D g,
                                 double offsetX, double offsetY, double zoom) {
        // ── 1. Dim mask ──────────────────────────────────────────────────────
        Rectangle clip = g.getClipBounds();
        int mx = clip != null ? clip.x      : 0;
        int my = clip != null ? clip.y      : 0;
        int mw = clip != null ? clip.width  : AppConstants.DEFAULT_WINDOW_WIDTH;
        int mh = clip != null ? clip.height : AppConstants.DEFAULT_WINDOW_HEIGHT;

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, MASK_ALPHA));
        g.setColor(Color.BLACK);
        g.fillRect(mx, my, mw, mh);

        // ── 2. Glow + star re-draw for each highlighted star ─────────────────
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
        for (String id : highlightedIds) {
            Star star = findStar(id);
            if (star == null) continue;

            double sx = star.getPosition().getX() * zoom + offsetX;
            double sy = star.getPosition().getY() * zoom + offsetY;
            double sr = Math.max(1.0, star.getRadius() * zoom);

            drawHighlightGlow(g, sx, sy, sr);
            drawStarBody(g, sx, sy, sr, star.getColor());
        }
    }

    /** Blue radial glow halo at {@value #GLOW_RADIUS_MULTIPLIER}× screen radius. */
    private static void drawHighlightGlow(Graphics2D g,
                                          double sx, double sy, double sr) {
        float glowR = (float) (sr * GLOW_RADIUS_MULTIPLIER);
        Color base  = ThemeManager.EDGE_HIGHLIGHT;
        Color peak  = new Color(base.getRed(), base.getGreen(), base.getBlue(), GLOW_PEAK_ALPHA);
        Color edge  = new Color(base.getRed(), base.getGreen(), base.getBlue(), 0);

        RadialGradientPaint glow = new RadialGradientPaint(
                new Point2D.Double(sx, sy), glowR,
                new float[]{0f, 0.4f, 1f},
                new Color[]{peak, peak, edge},
                MultipleGradientPaint.CycleMethod.NO_CYCLE);
        g.setPaint(glow);
        int d = (int) Math.ceil(glowR * 2);
        g.fillOval((int) Math.round(sx - glowR), (int) Math.round(sy - glowR), d, d);
    }

    /** Re-draws the star body so it "pops" above the dim mask. */
    private static void drawStarBody(Graphics2D g,
                                     double sx, double sy, double sr, Color sectorColor) {
        if (sr < 2.0) {
            g.setColor(sectorColor);
            g.fillRect((int) Math.round(sx), (int) Math.round(sy), 1, 1);
        } else {
            // Body gradient: white → sectorColor → darker
            RadialGradientPaint body = new RadialGradientPaint(
                    new Point2D.Double(sx, sy), (float) sr,
                    new float[]{0f, 0.5f, 1f},
                    new Color[]{Color.WHITE, sectorColor, sectorColor.darker()},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE);
            g.setPaint(body);
            int bx = (int) Math.round(sx - sr);
            int by = (int) Math.round(sy - sr);
            int bd = (int) Math.round(sr * 2);
            g.fillOval(bx, by, bd, bd);
        }
    }

    // -------------------------------------------------------------------------
    // Private — route rendering
    // -------------------------------------------------------------------------

    private void renderRoute(Graphics2D g,
                             double offsetX, double offsetY, double zoom) {
        Color base = ThemeManager.EDGE_HIGHLIGHT;

        // Outer glow pass
        Color outerColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), 55);
        g.setStroke(STROKE_OUTER);
        g.setColor(outerColor);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
        drawRoutePath(g, offsetX, offsetY, zoom);

        // Inner bright pass
        g.setStroke(STROKE_INNER);
        g.setColor(new Color(220, 235, 255, 180));
        drawRoutePath(g, offsetX, offsetY, zoom);
    }

    private void drawRoutePath(Graphics2D g,
                               double offsetX, double offsetY, double zoom) {
        Star prev = null;
        for (String id : navigationRoute) {
            Star curr = findStar(id);
            if (curr == null) { prev = null; continue; }
            if (prev != null) {
                double x1 = prev.getPosition().getX() * zoom + offsetX;
                double y1 = prev.getPosition().getY() * zoom + offsetY;
                double x2 = curr.getPosition().getX() * zoom + offsetX;
                double y2 = curr.getPosition().getY() * zoom + offsetY;
                g.draw(new Line2D.Double(x1, y1, x2, y2));
            }
            prev = curr;
        }
    }

    // -------------------------------------------------------------------------
    // Private — utility
    // -------------------------------------------------------------------------

    /** Linear scan for a star by ID; returns {@code null} if not found. */
    private Star findStar(String id) {
        for (Star s : stars) {
            if (s.getId().equals(id)) return s;
        }
        return null;
    }
}
