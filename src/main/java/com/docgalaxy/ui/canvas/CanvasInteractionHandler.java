package com.docgalaxy.ui.canvas;

import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.CelestialBody;
import com.docgalaxy.model.celestial.Star;

import java.awt.Desktop;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Translates raw AWT mouse/wheel events into {@link Camera} mutations,
 * hit-test results, and file-open requests on the owning {@link GalaxyCanvas}.
 *
 * <h3>Interactions</h3>
 * <ul>
 *   <li><b>Click-drag</b> — pans the viewport by the pixel delta since the
 *       last drag event.</li>
 *   <li><b>Scroll wheel / trackpad</b> — zooms centred on the cursor using
 *       {@link MouseWheelEvent#getPreciseWheelRotation()} for smooth trackpad
 *       support (factor = {@value #ZOOM_STEP} ^ −rotation).</li>
 *   <li><b>Single click</b> — hit-tests the body list via
 *       {@link CelestialBody#contains(Vector2D)}; shows the {@link PreviewCard}
 *       for the topmost hit, or hides it when the click lands on empty space.</li>
 *   <li><b>Double click</b> — opens the underlying note file in the OS default
 *       application via {@link Desktop#open(File)} (only for {@link Star} bodies;
 *       failures are silently swallowed).</li>
 * </ul>
 *
 * <p>The constructor self-registers as {@code mouseListener},
 * {@code mouseMotionListener}, and {@code mouseWheelListener} on the canvas,
 * and installs the {@link PreviewCard} as a null-layout overlay child.
 */
public final class CanvasInteractionHandler extends MouseAdapter
        implements MouseWheelListener {

    /**
     * Base zoom step per scroll unit (1.05 = 5 % per notch for smooth
     * trackpad feel).
     */
    static final double ZOOM_STEP = 1.05;

    private final GalaxyCanvas canvas;
    private final PreviewCard  previewCard;

    /** Screen coordinates of the most recent mouse-press/drag event. */
    private int lastX;
    private int lastY;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates the handler, attaches the {@link PreviewCard} overlay to the
     * canvas, and registers itself as all three mouse listener types.
     *
     * @param canvas the canvas to control; must not be {@code null}
     * @throws IllegalArgumentException if {@code canvas} is {@code null}
     */
    public CanvasInteractionHandler(GalaxyCanvas canvas) {
        if (canvas == null) throw new IllegalArgumentException("canvas must not be null");
        this.canvas = canvas;

        // Install PreviewCard as a null-layout overlay child
        previewCard = new PreviewCard();
        canvas.setLayout(null);
        canvas.add(previewCard);

        // Self-register
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
        canvas.addMouseWheelListener(this);
    }

    // -------------------------------------------------------------------------
    // MouseAdapter — pan
    // -------------------------------------------------------------------------

    /** Records the press location for subsequent drag deltas. */
    @Override
    public void mousePressed(MouseEvent e) {
        lastX = e.getX();
        lastY = e.getY();
    }

    /** Pans the camera by the pixel delta and repaints. */
    @Override
    public void mouseDragged(MouseEvent e) {
        canvas.getCamera().pan(e.getX() - lastX, e.getY() - lastY);
        lastX = e.getX();
        lastY = e.getY();
        canvas.repaint();
    }

    // -------------------------------------------------------------------------
    // MouseAdapter — click (single / double)
    // -------------------------------------------------------------------------

    /**
     * Handles single and double clicks.
     *
     * <ul>
     *   <li>Single click on a body → show {@link PreviewCard}.</li>
     *   <li>Single click on empty space → hide {@link PreviewCard}.</li>
     *   <li>Double click on a {@link Star} → hide card and open the note file.</li>
     * </ul>
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        CelestialBody hit = hitTest(e.getPoint());

        if (e.getClickCount() == 1) {
            if (hit != null) {
                previewCard.showFor(hit, e.getPoint(),
                                    canvas.getWidth(), canvas.getHeight());
            } else {
                previewCard.dismiss();
            }
            canvas.repaint();

        } else if (e.getClickCount() == 2) {
            previewCard.dismiss();
            canvas.repaint();
            if (hit != null) openFile(hit);
        }
    }

    // -------------------------------------------------------------------------
    // MouseWheelListener — zoom
    // -------------------------------------------------------------------------

    /**
     * Zooms the camera centred on the cursor position.
     * Uses {@link MouseWheelEvent#getPreciseWheelRotation()} so trackpad
     * two-finger scrolls are as smooth as physical wheel clicks.
     */
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        double factor = Math.pow(ZOOM_STEP, -e.getPreciseWheelRotation());
        canvas.getCamera().zoomAt(e.getX(), e.getY(), factor);
        canvas.repaint();
    }

    // -------------------------------------------------------------------------
    // Package-private accessors (for tests)
    // -------------------------------------------------------------------------

    /** Returns the {@link PreviewCard} managed by this handler. */
    PreviewCard getPreviewCard() {
        return previewCard;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the topmost body whose hit circle contains {@code screenPoint},
     * or {@code null} if the point is over empty space.
     * "Topmost" means the last matching element in the bodies list (highest
     * z-order, consistent with rendering).
     */
    private CelestialBody hitTest(Point screenPoint) {
        Vector2D worldPoint = canvas.getCamera().screenToWorld(screenPoint);
        List<CelestialBody> bodies = canvas.getBodies();
        CelestialBody result = null;
        for (CelestialBody body : bodies) {
            if (body.contains(worldPoint)) result = body;
        }
        return result;
    }

    /**
     * Opens the note file associated with {@code body} in the OS default
     * application.  Only {@link Star} bodies carry a file path; all errors
     * are silently swallowed so a missing or unreadable file never crashes
     * the UI.
     */
    private void openFile(CelestialBody body) {
        if (!(body instanceof Star star)) return;
        File file = new File(star.getNote().getFilePath());
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            }
        } catch (IOException | UnsupportedOperationException
                 | IllegalArgumentException ignored) {
            // Missing / unreadable file — silently ignore
        }
    }
}
