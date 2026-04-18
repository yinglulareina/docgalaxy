package com.docgalaxy.ui.canvas;

import com.docgalaxy.model.Edge;
import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.CelestialBody;
import com.docgalaxy.model.celestial.Nebula;
import com.docgalaxy.model.celestial.Star;
import com.docgalaxy.ui.ThemeManager;
import com.docgalaxy.ui.canvas.layer.OverlayLayer;
import com.docgalaxy.ui.canvas.layer.RenderLayer;
import com.docgalaxy.util.AppConstants;

import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The main rendering surface for the DocGalaxy visualisation.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Owns the {@link Camera} and wires up {@link CanvasInteractionHandler}
 *       for mouse-driven pan and zoom.</li>
 *   <li>Maintains an ordered list of {@link RenderLayer}s; each layer is
 *       asked to render itself inside {@link #paintComponent}.</li>
 *   <li>Keeps a flat list of {@link CelestialBody} objects purely for hit
 *       testing ({@link #hitTest}).</li>
 *   <li>Implements every {@link CanvasController} method so the sidebar and
 *       other UI components can drive navigation and highlighting without a
 *       direct reference to this class.</li>
 * </ul>
 *
 * <p>All public methods must be called on the Event Dispatch Thread.
 */
public final class GalaxyCanvas extends JPanel implements CanvasController {

    // -------------------------------------------------------------------------
    // Core state
    // -------------------------------------------------------------------------

    private final Camera camera;
    private final CanvasInteractionHandler interactionHandler;

    /** Ordered rendering layers (back-to-front). */
    private final List<RenderLayer> layers = new ArrayList<>();

    /** Flat body list for hit testing. Not rendered directly — layers own rendering. */
    private final List<CelestialBody> bodies = new ArrayList<>();

    /** Live edge list — shared with EdgeLayer, used for hit testing by the interaction handler. */
    private List<Edge> edges = Collections.emptyList();

    /** Optional overlay layer for highlight / route rendering. */
    private OverlayLayer overlayLayer;

    // -------------------------------------------------------------------------
    // Controller state
    // -------------------------------------------------------------------------

    private final Set<String> highlightedNotes = new HashSet<>();
    private final List<String> navigationRoute  = new ArrayList<>();

    /** Callback fired whenever the zoom level changes (via mouse wheel). */
    private Runnable onZoomChange;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates the canvas with a default-sized camera and wires up mouse
     * interaction.
     */
    public GalaxyCanvas() {
        setDoubleBuffered(true);
        setBackground(ThemeManager.BG_PRIMARY);
        setLayout(null);   // null layout — overlay children positioned via setBounds

        camera = new Camera(AppConstants.DEFAULT_WINDOW_WIDTH,
                            AppConstants.DEFAULT_WINDOW_HEIGHT);

        // Handler self-registers as mouseListener/mouseMotionListener/mouseWheelListener
        interactionHandler = new CanvasInteractionHandler(this);
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /**
     * Paints the canvas: sets up rendering hints, retrieves the camera
     * transform, and delegates to each registered {@link RenderLayer} in order.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        AffineTransform cameraTransform = camera.getTransform();
        double zoom = camera.getZoom();

        for (RenderLayer layer : layers) {
            try {
                layer.render(g2, cameraTransform, zoom);
            } catch (Exception ex) {
                // Defensive: a broken layer must not crash the whole paint cycle
            }
        }
    }

    // -------------------------------------------------------------------------
    // Layer management
    // -------------------------------------------------------------------------

    /**
     * Appends a render layer to the back-to-front draw order.
     *
     * @param layer the layer to add; must not be {@code null}
     */
    public void addLayer(RenderLayer layer) {
        if (layer == null) throw new IllegalArgumentException("layer must not be null");
        layers.add(layer);
        if (layer instanceof OverlayLayer ol) {
            overlayLayer = ol;
        }
    }

    /**
     * Removes a previously registered render layer.
     *
     * @param layer the layer to remove
     */
    public void removeLayer(RenderLayer layer) {
        layers.remove(layer);
        if (layer != null && layer == overlayLayer) {
            overlayLayer = null;
        }
    }

    /**
     * Returns an unmodifiable view of the current layer list (back-to-front).
     *
     * @return unmodifiable layer list
     */
    public List<RenderLayer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    // -------------------------------------------------------------------------
    // Body management (hit testing)
    // -------------------------------------------------------------------------

    /**
     * Replaces the complete set of celestial bodies used for hit testing.
     *
     * @param bodies new body list; {@code null} is treated as empty
     */
    public void setBodies(List<CelestialBody> bodies) {
        this.bodies.clear();
        if (bodies != null) this.bodies.addAll(bodies);
    }

    /**
     * Returns an unmodifiable view of the current body list.
     *
     * @return unmodifiable body list
     */
    public List<CelestialBody> getBodies() {
        return Collections.unmodifiableList(bodies);
    }

    /**
     * Sets the live edge list used for click hit-testing.
     * The same list should be passed to {@code EdgeLayer} so they stay in sync.
     *
     * @param edges list of edges; {@code null} is treated as empty
     */
    public void setEdges(List<Edge> edges) {
        this.edges = (edges != null) ? edges : Collections.emptyList();
    }

    /**
     * Returns the current edge list.
     *
     * @return live edge list; never {@code null}
     */
    public List<Edge> getEdges() {
        return edges;
    }

    /**
     * Returns the first body whose screen-space bounding circle contains
     * {@code screenPoint}, or {@code null} if no body is hit.
     *
     * @param screenPoint screen-space pixel coordinate
     * @return hit body, or {@code null}
     */
    public CelestialBody hitTest(Point screenPoint) {
        if (screenPoint == null) return null;
        Vector2D worldPoint = camera.screenToWorld(screenPoint);
        for (int i = bodies.size() - 1; i >= 0; i--) {
            if (bodies.get(i).contains(worldPoint)) return bodies.get(i);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Camera access
    // -------------------------------------------------------------------------

    /**
     * Returns the camera managed by this canvas.
     * Exposed so {@link CanvasInteractionHandler} can mutate it.
     *
     * @return the camera; never {@code null}
     */
    public Camera getCamera() {
        return camera;
    }

    /**
     * Returns the interaction handler wired to this canvas.
     *
     * @return the interaction handler; never {@code null}
     */
    public CanvasInteractionHandler getInteractionHandler() {
        return interactionHandler;
    }

    /** Registers a callback fired on every zoom gesture. */
    public void setOnZoomChange(Runnable callback) { this.onZoomChange = callback; }

    /** Called by {@link CanvasInteractionHandler} after every zoom gesture. */
    void notifyZoomChange() { if (onZoomChange != null) onZoomChange.run(); }

    // -------------------------------------------------------------------------
    // CanvasController — highlighting
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * Stores the set and forwards it to the registered {@link OverlayLayer}
     * if present, then repaints.
     */
    @Override
    public void highlightNotes(Set<String> noteIds) {
        highlightedNotes.clear();
        if (noteIds != null) highlightedNotes.addAll(noteIds);
        if (overlayLayer != null) overlayLayer.setHighlightedNotes(
                Collections.unmodifiableSet(new HashSet<>(highlightedNotes)));
        repaint();
    }

    /** {@inheritDoc} */
    @Override
    public void clearHighlight() {
        highlightedNotes.clear();
        if (overlayLayer != null) overlayLayer.setHighlightedNotes(Collections.emptySet());
        navigationRoute.clear();
        if (overlayLayer != null) overlayLayer.setNavigationRoute(Collections.emptyList());
        repaint();
    }

    // -------------------------------------------------------------------------
    // CanvasController — navigation route
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public void showNavigationRoute(List<String> noteIds) {
        navigationRoute.clear();
        if (noteIds != null) navigationRoute.addAll(noteIds);
        if (overlayLayer != null) overlayLayer.setNavigationRoute(
                Collections.unmodifiableList(new ArrayList<>(navigationRoute)));
        repaint();
    }

    // -------------------------------------------------------------------------
    // CanvasController — camera navigation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * Finds the {@link CelestialBody} with {@code noteId} and centres the
     * camera on its world position.  If the note is not in the body list the
     * call is silently ignored.
     */
    @Override
    public void navigateToNote(String noteId) {
        if (noteId == null) return;
        bodies.stream()
              .filter(b -> noteId.equals(b.getId()))
              .findFirst()
              .ifPresent(b -> {
                  centerCameraOn(b.getPosition());
                  repaint();
              });
    }

    /**
     * {@inheritDoc}
     * Computes the centroid of all {@link Star} and {@link Nebula} bodies that
     * belong to the given sector and centres the camera there.  If no matching
     * bodies exist the call is silently ignored.
     */
    @Override
    public void navigateToSector(String sectorId) {
        if (sectorId == null) return;

        List<Vector2D> positions = new ArrayList<>();
        for (CelestialBody b : bodies) {
            if (b instanceof Star  s && sectorId.equals(s.getSector().getId()))
                positions.add(b.getPosition());
            else if (b instanceof Nebula n && sectorId.equals(n.getSector().getId()))
                positions.add(b.getPosition());
        }
        if (positions.isEmpty()) return;

        double cx = positions.stream().mapToDouble(Vector2D::getX).average().orElse(0);
        double cy = positions.stream().mapToDouble(Vector2D::getY).average().orElse(0);
        centerCameraOn(new Vector2D(cx, cy));
        repaint();
    }

    // -------------------------------------------------------------------------
    // CanvasController — zoom / fit-all
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public double getZoomLevel() {
        return camera.getZoom();
    }

    /**
     * Convenience alias used by MainFrame status bar.
     *
     * @return current zoom level
     */
    public double getZoom() {
        return camera.getZoom();
    }

    /**
     * {@inheritDoc}
     * Collects all body positions, delegates to
     * {@link Camera#fitAll(List)}, then repaints.
     */
    @Override
    public void fitAll() {
        List<Vector2D> positions = bodies.stream()
                .map(CelestialBody::getPosition)
                .collect(Collectors.toList());
        camera.fitAll(positions);
        repaint();
    }

    // -------------------------------------------------------------------------
    // KnowledgeBase integration (used by MainFrame/Sidebar refresh)
    // -------------------------------------------------------------------------

    /**
     * Called by MainFrame after the pipeline populates the KnowledgeBase.
     * The canvas itself does not render from KB directly — bodies/edges are
     * set explicitly via {@link #setBodies} and {@link #setEdges} — but
     * accepting this call allows MainFrame to follow a uniform refresh pattern.
     *
     * @param kb the loaded knowledge base; may be {@code null}
     */
    public void setKnowledgeBase(KnowledgeBase kb) {
        // The canvas renders from bodies/layers set directly by the pipeline,
        // not from KnowledgeBase. Accept the call but do nothing extra.
        repaint();
    }

    // -------------------------------------------------------------------------
    // State accessors (for tests)
    // -------------------------------------------------------------------------

    /**
     * Returns a snapshot of the currently highlighted note ids.
     *
     * @return unmodifiable set; never {@code null}
     */
    public Set<String> getHighlightedNotes() {
        return Collections.unmodifiableSet(new HashSet<>(highlightedNotes));
    }

    /**
     * Returns the current navigation route.
     *
     * @return unmodifiable list; never {@code null}
     */
    public List<String> getNavigationRoute() {
        return Collections.unmodifiableList(new ArrayList<>(navigationRoute));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Centres the camera viewport on the given world-space position using
     * {@link Camera#pan} to compute the required offset delta.
     * Falls back to the camera's stored viewport dimensions when the panel
     * has not yet been laid out (width/height == 0).
     */
    private void centerCameraOn(Vector2D worldPos) {
        int vpW = getWidth()  > 0 ? getWidth()  : camera.getViewportWidth();
        int vpH = getHeight() > 0 ? getHeight() : camera.getViewportHeight();
        Point currentScreen = camera.worldToScreen(worldPos);
        camera.pan(vpW / 2.0 - currentScreen.x,
                   vpH / 2.0 - currentScreen.y);
    }
}
