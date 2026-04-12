package com.docgalaxy.ui.canvas;

import com.docgalaxy.model.Vector2D;
import com.docgalaxy.util.AppConstants;

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.util.List;

/**
 * Manages the 2-D viewport transform between world space and screen space.
 *
 * <h3>Coordinate model</h3>
 * <pre>
 *   screenX = worldX × zoom + offsetX
 *   screenY = worldY × zoom + offsetY
 * </pre>
 *
 * <p>Zoom is always clamped to [{@value #MIN_ZOOM}, {@value #MAX_ZOOM}].
 * All pan/zoom mutations are synchronous and assume calls originate on the EDT.
 */
public final class Camera {

    /** Minimum allowed zoom level. */
    public static final double MIN_ZOOM = 0.05;

    /** Maximum allowed zoom level. */
    public static final double MAX_ZOOM = 5.0;

    /** Padding fraction applied on each side when fitting all nodes (10 %). */
    private static final double FIT_PADDING = 0.10;

    private double offsetX;
    private double offsetY;
    private double zoom   = 1.0;

    private int viewportWidth;
    private int viewportHeight;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Default constructor — uses {@link AppConstants#DEFAULT_WINDOW_WIDTH} ×
     * {@link AppConstants#DEFAULT_WINDOW_HEIGHT} as the initial viewport size.
     */
    public Camera() {
        this(AppConstants.DEFAULT_WINDOW_WIDTH, AppConstants.DEFAULT_WINDOW_HEIGHT);
    }

    /**
     * Full constructor.
     *
     * @param viewportWidth  viewport width in pixels (must be &gt; 0)
     * @param viewportHeight viewport height in pixels (must be &gt; 0)
     * @throws IllegalArgumentException if either dimension is &le; 0
     */
    public Camera(int viewportWidth, int viewportHeight) {
        if (viewportWidth  <= 0) throw new IllegalArgumentException("viewportWidth must be > 0");
        if (viewportHeight <= 0) throw new IllegalArgumentException("viewportHeight must be > 0");
        this.viewportWidth  = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    // -------------------------------------------------------------------------
    // Viewport
    // -------------------------------------------------------------------------

    /**
     * Updates the viewport dimensions (e.g. on window resize).
     *
     * @param width  new viewport width in pixels (must be &gt; 0)
     * @param height new viewport height in pixels (must be &gt; 0)
     * @throws IllegalArgumentException if either dimension is &le; 0
     */
    public void setViewportSize(int width, int height) {
        if (width  <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        this.viewportWidth  = width;
        this.viewportHeight = height;
    }

    // -------------------------------------------------------------------------
    // Pan / zoom
    // -------------------------------------------------------------------------

    /**
     * Translates the viewport by {@code (dx, dy)} screen pixels.
     *
     * @param dx horizontal offset in pixels
     * @param dy vertical offset in pixels
     */
    public void pan(double dx, double dy) {
        offsetX += dx;
        offsetY += dy;
    }

    /**
     * Zooms the viewport by {@code factor}, keeping the world point currently
     * under {@code (screenX, screenY)} fixed on screen.
     *
     * <p>The resulting zoom is clamped to [{@value #MIN_ZOOM}, {@value #MAX_ZOOM}].
     *
     * @param screenX screen x of the zoom pivot (e.g. mouse cursor x)
     * @param screenY screen y of the zoom pivot (e.g. mouse cursor y)
     * @param factor  zoom multiplier (&gt; 1 zooms in, &lt; 1 zooms out)
     */
    public void zoomAt(double screenX, double screenY, double factor) {
        // World coordinates of the pivot point before zoom change
        double worldX = (screenX - offsetX) / zoom;
        double worldY = (screenY - offsetY) / zoom;

        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));

        // Recompute offsets so the same world point stays under the cursor
        offsetX = screenX - worldX * zoom;
        offsetY = screenY - worldY * zoom;
    }

    // -------------------------------------------------------------------------
    // Coordinate conversion
    // -------------------------------------------------------------------------

    /**
     * Converts a world-space position to an integer screen pixel coordinate.
     *
     * @param world world-space position; must not be {@code null}
     * @return screen-space {@link Point}
     * @throws IllegalArgumentException if {@code world} is {@code null}
     */
    public Point worldToScreen(Vector2D world) {
        if (world == null) throw new IllegalArgumentException("world must not be null");
        int sx = (int) Math.round(world.getX() * zoom + offsetX);
        int sy = (int) Math.round(world.getY() * zoom + offsetY);
        return new Point(sx, sy);
    }

    /**
     * Converts a screen pixel coordinate to a world-space position.
     *
     * @param screen screen-space pixel point; must not be {@code null}
     * @return world-space {@link Vector2D}
     * @throws IllegalArgumentException if {@code screen} is {@code null}
     */
    public Vector2D screenToWorld(Point screen) {
        if (screen == null) throw new IllegalArgumentException("screen must not be null");
        double wx = (screen.x - offsetX) / zoom;
        double wy = (screen.y - offsetY) / zoom;
        return new Vector2D(wx, wy);
    }

    /**
     * Returns an {@link AffineTransform} that maps world coordinates to screen
     * coordinates: {@code translate(offsetX, offsetY)} followed by
     * {@code scale(zoom, zoom)}.
     *
     * <p>The returned object is a fresh copy; mutating it does not affect the
     * camera state.
     *
     * @return current world-to-screen affine transform
     */
    public AffineTransform getTransform() {
        AffineTransform at = new AffineTransform();
        at.translate(offsetX, offsetY);
        at.scale(zoom, zoom);
        return at;
    }

    // -------------------------------------------------------------------------
    // Fit-all
    // -------------------------------------------------------------------------

    /**
     * Adjusts the camera so that all given world-space points are visible,
     * with {@value #FIT_PADDING} × 100 % padding added on each side.
     *
     * <p>If {@code points} is {@code null} or empty this method is a no-op.
     *
     * @param points world-space positions to fit; {@code null} is treated as empty
     */
    public void fitAll(List<Vector2D> points) {
        if (points == null || points.isEmpty()) return;

        // Compute world-space bounding box
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Vector2D p : points) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getY() > maxY) maxY = p.getY();
        }

        double bboxW = maxX - minX;
        double bboxH = maxY - minY;

        // Expand by 10 % on each side (20 % total)
        double paddedW = bboxW == 0 ? viewportWidth  : bboxW * (1.0 + 2.0 * FIT_PADDING);
        double paddedH = bboxH == 0 ? viewportHeight : bboxH * (1.0 + 2.0 * FIT_PADDING);

        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM,
                Math.min((double) viewportWidth  / paddedW,
                         (double) viewportHeight / paddedH)));

        // Centre the bounding box in the viewport
        double worldCX = (minX + maxX) / 2.0;
        double worldCY = (minY + maxY) / 2.0;
        offsetX = viewportWidth  / 2.0 - worldCX * zoom;
        offsetY = viewportHeight / 2.0 - worldCY * zoom;
    }

    // -------------------------------------------------------------------------
    // Accessors (for tests and rendering)
    // -------------------------------------------------------------------------

    /** Returns the current horizontal offset in screen pixels. */
    public double getOffsetX() { return offsetX; }

    /** Returns the current vertical offset in screen pixels. */
    public double getOffsetY() { return offsetY; }

    /** Returns the current zoom level (always within [{@value #MIN_ZOOM}, {@value #MAX_ZOOM}]). */
    public double getZoom()    { return zoom; }

    /** Returns the current viewport width in pixels. */
    public int getViewportWidth()  { return viewportWidth; }

    /** Returns the current viewport height in pixels. */
    public int getViewportHeight() { return viewportHeight; }
}
