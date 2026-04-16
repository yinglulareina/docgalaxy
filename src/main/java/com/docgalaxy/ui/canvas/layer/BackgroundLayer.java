package com.docgalaxy.ui.canvas.layer;

import com.docgalaxy.util.AppConstants;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * A static star-field background rendered onto a cached {@link BufferedImage}.
 *
 * <p>On construction, 300 dim dots are scattered pseudo-randomly across a
 * {@value AppConstants#DEFAULT_WINDOW_WIDTH} × {@value AppConstants#DEFAULT_WINDOW_HEIGHT}
 * image using a fixed seed (42) so the pattern is deterministic across runs.
 * The image is generated once and reused on every {@link #render} call, making
 * this layer essentially free at paint time.
 *
 * <p>{@link #needsRepaint()} always returns {@code false} — the background
 * never changes.
 */
public final class BackgroundLayer implements RenderLayer {

    /** Number of background dots drawn on the cached image. */
    static final int DOT_COUNT = 300;

    /** Fixed RNG seed for deterministic dot placement. */
    static final long SEED = 42L;

    /** Maximum alpha (0-255) for a dim background star. */
    private static final int MAX_ALPHA = 180;

    /** Minimum alpha so dots remain faintly visible. */
    private static final int MIN_ALPHA = 30;

    private final BufferedImage cache;
    private final int imageWidth;
    private final int imageHeight;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a {@code BackgroundLayer} sized to the default window dimensions
     * ({@value AppConstants#DEFAULT_WINDOW_WIDTH} ×
     * {@value AppConstants#DEFAULT_WINDOW_HEIGHT}).
     */
    public BackgroundLayer() {
        this(AppConstants.DEFAULT_WINDOW_WIDTH, AppConstants.DEFAULT_WINDOW_HEIGHT);
    }

    /**
     * Creates a {@code BackgroundLayer} with a custom image size.
     * Useful for tests and non-standard viewport configurations.
     *
     * @param width  image width in pixels (must be &gt; 0)
     * @param height image height in pixels (must be &gt; 0)
     * @throws IllegalArgumentException if either dimension is &le; 0
     */
    public BackgroundLayer(int width, int height) {
        if (width  <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        this.imageWidth  = width;
        this.imageHeight = height;
        this.cache = buildCache(width, height);
    }

    // -------------------------------------------------------------------------
    // RenderLayer
    // -------------------------------------------------------------------------

    /**
     * Draws the pre-generated star-field image at the origin (ignoring the
     * camera transform — the background is fixed in screen space).
     *
     * @param g               graphics context
     * @param cameraTransform current camera transform (unused — background is screen-fixed)
     * @param zoom            current zoom level (unused)
     */
    @Override
    public void render(Graphics2D g, AffineTransform cameraTransform, double zoom) {
        g.drawImage(cache, 0, 0, null);
    }

    /**
     * Returns {@code false}: the cached image never changes.
     *
     * @return {@code false} always
     */
    @Override
    public boolean needsRepaint() {
        return false;
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
     * Generates the star-field image: a solid dark background with
     * {@value #DOT_COUNT} 1-px dots at random positions.
     */
    private static BufferedImage buildCache(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_OFF);

            // Transparent background — GalaxyCanvas already fills with BG_PRIMARY
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
