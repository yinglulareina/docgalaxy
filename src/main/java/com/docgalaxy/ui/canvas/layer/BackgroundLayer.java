package com.docgalaxy.ui.canvas.layer;

import com.docgalaxy.model.Vector2D;
import com.docgalaxy.ui.ThemeManager;
import com.docgalaxy.util.AppConstants;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

/**
 * Background layer rendering two sub-passes:
 *
 * <ol>
 *   <li><b>Dynamic radial gradient</b> — centre follows the screen-space
 *       centroid of all star positions so the "glow region" tracks whichever
 *       part of the galaxy the user is looking at.  Gradient:
 *       {@link ThemeManager#BG_PRIMARY} at centre → {@code #0A0A14} at the
 *       edge.  Radius = 0.6 × canvas diagonal.</li>
 *   <li><b>Static star-field cache</b> — 300 dim dots pre-rendered into a
 *       {@link BufferedImage} once at construction time (fixed seed 42), then
 *       blitted every frame at near-zero cost.</li>
 * </ol>
 *
 * <p>When no star positions are supplied (zero-arg / size-only constructors)
 * the gradient falls back to the canvas centre, matching the old look.
 *
 * <p>{@link #needsRepaint()} returns {@code true} because the gradient centre
 * changes whenever the user pans the canvas.
 */
public final class BackgroundLayer implements RenderLayer {

    /** Number of background dots drawn on the cached image. */
    static final int DOT_COUNT = 300;

    /** Fixed RNG seed for deterministic dot placement. */
    static final long SEED = 42L;

    /** Fraction of the canvas diagonal used as the gradient radius. */
    static final double GRADIENT_RADIUS_FACTOR = 0.6;

    /** Outer edge colour of the radial gradient. */
    private static final Color BG_OUTER = new Color(0x0A, 0x0A, 0x14);

    /** Maximum alpha (0-255) for a dim background star. */
    private static final int MAX_ALPHA = 180;

    /** Minimum alpha so dots remain faintly visible. */
    private static final int MIN_ALPHA = 30;

    private final BufferedImage  cache;
    private final int            imageWidth;
    private final int            imageHeight;

    /**
     * Live reference to world-space star positions used to compute the
     * gradient centroid.  May be {@code null} when none are provided.
     */
    private final List<Vector2D> starPositions;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a {@code BackgroundLayer} sized to the default window dimensions
     * with no dynamic gradient centroid (falls back to canvas centre).
     */
    public BackgroundLayer() {
        this(AppConstants.DEFAULT_WINDOW_WIDTH, AppConstants.DEFAULT_WINDOW_HEIGHT, null);
    }

    /**
     * Creates a {@code BackgroundLayer} with a custom image size and no
     * dynamic centroid.  Useful for tests.
     *
     * @param width  image width in pixels (must be &gt; 0)
     * @param height image height in pixels (must be &gt; 0)
     * @throws IllegalArgumentException if either dimension is &le; 0
     */
    public BackgroundLayer(int width, int height) {
        this(width, height, null);
    }

    /**
     * Creates a {@code BackgroundLayer} sized to the default window dimensions
     * whose radial gradient tracks the centroid of the supplied star positions.
     *
     * @param starPositions live list of world-space star positions; referenced,
     *                      not copied — updates are reflected on the next repaint.
     *                      {@code null} is accepted (falls back to canvas centre).
     */
    public BackgroundLayer(List<Vector2D> starPositions) {
        this(AppConstants.DEFAULT_WINDOW_WIDTH, AppConstants.DEFAULT_WINDOW_HEIGHT, starPositions);
    }

    /**
     * Full constructor.
     *
     * @param width          image width in pixels (must be &gt; 0)
     * @param height         image height in pixels (must be &gt; 0)
     * @param starPositions  live list of world-space positions, or {@code null}
     * @throws IllegalArgumentException if either dimension is &le; 0
     */
    public BackgroundLayer(int width, int height, List<Vector2D> starPositions) {
        if (width  <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        this.imageWidth     = width;
        this.imageHeight    = height;
        this.starPositions  = starPositions;
        this.cache          = buildCache(width, height);
    }

    // -------------------------------------------------------------------------
    // RenderLayer
    // -------------------------------------------------------------------------

    /**
     * Renders the background in two sub-passes:
     * <ol>
     *   <li>Dynamic radial gradient centred on the screen-projected star centroid.</li>
     *   <li>Cached static star-field image blitted over it.</li>
     * </ol>
     *
     * @param g               graphics context
     * @param cameraTransform world-to-screen affine transform
     * @param zoom            current zoom level
     */
    @Override
    public void render(Graphics2D g, AffineTransform cameraTransform, double zoom) {
        int w = g.getClipBounds() != null ? g.getClipBounds().width  : imageWidth;
        int h = g.getClipBounds() != null ? g.getClipBounds().height : imageHeight;

        // ── 1. Dynamic radial gradient ────────────────────────────────────────
        Point2D centre = computeScreenCentroid(cameraTransform, zoom, w, h);
        float gradRadius = (float) (Math.sqrt((double) w * w + (double) h * h) * GRADIENT_RADIUS_FACTOR);
        if (gradRadius > 0) {
            RadialGradientPaint gradient = new RadialGradientPaint(
                    centre, gradRadius,
                    new float[]{0f, 1f},
                    new Color[]{ThemeManager.BG_PRIMARY, BG_OUTER},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE);
            g.setPaint(gradient);
            g.fillRect(0, 0, w, h);
        } else {
            g.setColor(ThemeManager.BG_PRIMARY);
            g.fillRect(0, 0, w, h);
        }

        // ── 2. Static star-field cache ─────────────────────────────────────────
        g.drawImage(cache, 0, 0, null);
    }

    /**
     * Returns {@code true}: the gradient centre changes as the user pans.
     *
     * @return {@code true} always
     */
    @Override
    public boolean needsRepaint() {
        return true;
    }

    // -------------------------------------------------------------------------
    // Package-private accessors (tests)
    // -------------------------------------------------------------------------

    /** Returns the cached background image (for inspection in tests). */
    BufferedImage getCache() {
        return cache;
    }

    /** Returns the image width passed at construction. */
    int getImageWidth()  { return imageWidth; }

    /** Returns the image height passed at construction. */
    int getImageHeight() { return imageHeight; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Computes the screen-space centroid of all star positions by averaging
     * their world coordinates and projecting through the camera transform.
     * Falls back to the canvas centre when the position list is absent or empty.
     */
    private Point2D computeScreenCentroid(AffineTransform cameraTransform,
                                          double zoom, int w, int h) {
        if (starPositions == null || starPositions.isEmpty()) {
            return new Point2D.Double(w / 2.0, h / 2.0);
        }

        double sumX = 0, sumY = 0;
        for (Vector2D p : starPositions) {
            sumX += p.getX();
            sumY += p.getY();
        }
        double worldCx = sumX / starPositions.size();
        double worldCy = sumY / starPositions.size();

        double offsetX = cameraTransform.getTranslateX();
        double offsetY = cameraTransform.getTranslateY();
        return new Point2D.Double(worldCx * zoom + offsetX,
                                  worldCy * zoom + offsetY);
    }

    /**
     * Generates the star-field image: transparent base with {@value #DOT_COUNT}
     * 1-px dots at random positions.  The canvas fills the solid BG colour
     * before this layer, so transparency here is correct.
     */
    private static BufferedImage buildCache(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_OFF);

            // Transparent base — radial gradient pass fills the solid colour
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, width, height);

            Random rng = new Random(SEED);
            for (int i = 0; i < DOT_COUNT; i++) {
                int x     = rng.nextInt(width);
                int y     = rng.nextInt(height);
                int alpha = MIN_ALPHA + rng.nextInt(MAX_ALPHA - MIN_ALPHA + 1);
                g.setColor(new Color(255, 255, 255, alpha));
                g.fillRect(x, y, 1, 1);
            }
        } finally {
            g.dispose();
        }
        return img;
    }
}
