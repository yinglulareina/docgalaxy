package com.docgalaxy.ui.canvas.layer;

import com.docgalaxy.model.celestial.Star;
import com.docgalaxy.ui.ThemeManager;
import com.docgalaxy.util.AppConstants;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.util.Collections;
import java.util.List;

/**
 * Dedicated label layer that renders the note filename below each star.
 *
 * <h3>Visibility</h3>
 * <ul>
 *   <li>Completely skipped when {@code zoom < }{@link AppConstants#LABEL_SHOW_THRESHOLD} (0.5).</li>
 *   <li>Alpha fades in linearly from 0 at zoom = 0.5 to 1 at zoom = 0.8:
 *       {@code alpha = min(1, (zoom − 0.5) / 0.3)}.</li>
 * </ul>
 *
 * <h3>Per-star rendering</h3>
 * The note's filename ({@link com.docgalaxy.model.Note#getFileName()}) is drawn
 * horizontally centred below the star body in {@link ThemeManager#FONT_SMALL} /
 * {@link ThemeManager#TEXT_PRIMARY}.
 *
 * <p>{@link #needsRepaint()} returns {@code true}: star positions change with
 * layout updates.
 */
public final class LabelLayer implements RenderLayer {

    /** Zoom level below which no labels are drawn. */
    static final double SHOW_THRESHOLD = AppConstants.LABEL_SHOW_THRESHOLD; // 0.5

    /** Zoom range over which labels fade from invisible to fully opaque. */
    static final double FADE_RANGE = 0.2;

    /** Pixel gap between the bottom of the star circle and the top of the label. */
    private static final int LABEL_GAP = 2;

    private final List<Star> stars;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a {@code LabelLayer} backed by the given list.
     * The list is referenced (not copied) so position changes are reflected
     * on the next repaint without rebuilding the layer.
     *
     * @param stars list of stars whose labels to render; must not be {@code null}
     * @throws IllegalArgumentException if {@code stars} is {@code null}
     */
    public LabelLayer(List<Star> stars) {
        if (stars == null) throw new IllegalArgumentException("stars must not be null");
        this.stars = stars;
    }

    // -------------------------------------------------------------------------
    // RenderLayer
    // -------------------------------------------------------------------------

    /**
     * Draws filenames for all stars at an alpha level determined by zoom.
     * The layer is a no-op when {@code zoom < }{@value #SHOW_THRESHOLD} or
     * the star list is empty.
     *
     * @param g               graphics context (screen space)
     * @param cameraTransform world-to-screen affine transform
     * @param zoom            current zoom level
     */
    @Override
    public void render(Graphics2D g, AffineTransform cameraTransform, double zoom) {
        if (zoom < SHOW_THRESHOLD || stars.isEmpty()) return;

        float alpha = (float) Math.min(1.0, (zoom - SHOW_THRESHOLD) / FADE_RANGE);
        if (alpha <= 0f) return;

        double offsetX = cameraTransform.getTranslateX();
        double offsetY = cameraTransform.getTranslateY();

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Composite savedComposite = g.getComposite();
        Color     savedColor     = g.getColor();
        Font      savedFont      = g.getFont();

        try {
            g.setFont(ThemeManager.FONT_SMALL);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setColor(ThemeManager.TEXT_PRIMARY);

            FontMetrics fm = g.getFontMetrics();

            for (Star star : stars) {
                String label = star.getNote().getFileName();
                if (label == null || label.isEmpty()) continue;

                double sx = star.getPosition().getX() * zoom + offsetX;
                double sy = star.getPosition().getY() * zoom + offsetY;
                double sr = star.getRadius() * zoom;

                int textW  = fm.stringWidth(label);
                int labelX = (int) Math.round(sx - textW / 2.0);
                int labelY = (int) Math.round(sy + sr + fm.getAscent() + LABEL_GAP);

                g.drawString(label, labelX, labelY);
            }
        } finally {
            g.setComposite(savedComposite);
            g.setColor(savedColor);
            g.setFont(savedFont);
        }
    }

    /**
     * Always returns {@code true}: star positions change with layout updates.
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

    /** Returns an unmodifiable view of the backing star list. */
    List<Star> getStars() {
        return Collections.unmodifiableList(stars);
    }
}
