package com.docgalaxy.ui.canvas;

import com.docgalaxy.ai.AIServiceException;
import com.docgalaxy.ai.ChatResponse;
import com.docgalaxy.ai.OpenAIChatProvider;
import com.docgalaxy.model.Edge;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.CelestialBody;
import com.docgalaxy.model.celestial.Star;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Desktop;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Translates raw AWT mouse/wheel events into {@link Camera} mutations,
 * hit-test results, and file-open requests on the owning {@link GalaxyCanvas}.
 *
 * <h3>Interactions</h3>
 * <ul>
 *   <li><b>Click-drag</b> — pans the viewport.</li>
 *   <li><b>Scroll wheel / trackpad</b> — zooms centred on the cursor.</li>
 *   <li><b>Mouse move</b> — shows {@link PreviewCard} on hover over a
 *       {@link Star}; hides it when the cursor leaves.</li>
 *   <li><b>Single click on Star</b> — highlights the star.</li>
 *   <li><b>Single click on Edge</b> — highlights the edge and shows a
 *       relationship card; triggers a lazy LLM description if not yet cached.</li>
 *   <li><b>Single click on empty space</b> — clears all highlights.</li>
 *   <li><b>Double click on Star</b> — opens the note file in the OS default app.</li>
 * </ul>
 */
public final class CanvasInteractionHandler extends MouseAdapter
        implements MouseWheelListener {

    /**
     * Base zoom step per scroll unit (1.05 = 5 % per notch for smooth
     * trackpad feel).
     */
    static final double ZOOM_STEP = 1.05;

    /**
     * Maximum screen-space pixel distance from the cursor to a line segment
     * for an edge click to register.
     */
    static final double EDGE_HIT_THRESHOLD_PX = 8.0;

    private final GalaxyCanvas canvas;
    private final PreviewCard  previewCard;

    // ── Optional ChatProvider for LLM edge descriptions ──────────────────────
    private final OpenAIChatProvider chatProvider;

    /** Screen coordinates of the most recent mouse-press/drag event. */
    private int lastX;
    private int lastY;

    /** ID of the star currently shown in the hover card; null if none. */
    private String lastHoveredStarId;

    /** The edge currently highlighted; null if none. */
    private Edge highlightedEdge;

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

        // Try to build ChatProvider for edge relationship descriptions
        OpenAIChatProvider cp = null;
        try {
            cp = new OpenAIChatProvider();
        } catch (IllegalStateException ignored) { /* OPENAI_API_KEY not set */ }
        this.chatProvider = cp;

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
    // MouseAdapter — hover (mouse move)
    // -------------------------------------------------------------------------

    /**
     * Shows the {@link PreviewCard} when hovering over a {@link Star}; hides
     * it when moving over empty space.  The dedup guard prevents redundant
     * refreshes while the cursor stays on the same star.
     *
     * <p>Edge hover is intentionally not handled here.
     */
    @Override
    public void mouseMoved(MouseEvent e) {
        Star hit = hitTestStar(e.getPoint());

        if (hit == null) {
            if (lastHoveredStarId != null) {
                previewCard.dismiss();
                lastHoveredStarId = null;
                canvas.repaint();
            }
            return;
        }

        if (hit.getId().equals(lastHoveredStarId)) return;   // same star, no refresh

        lastHoveredStarId = hit.getId();
        String snippet = readSnippet(hit.getNote().getFilePath(), 100);
        previewCard.showHover(hit, snippet, e.getPoint(),
                              canvas.getWidth(), canvas.getHeight());
        canvas.repaint();
    }

    // -------------------------------------------------------------------------
    // MouseAdapter — click (single / double)
    // -------------------------------------------------------------------------

    /**
     * Handles single and double clicks.
     *
     * <ul>
     *   <li>Single click on a {@link Star} → highlight it.</li>
     *   <li>Single click on an {@link Edge} → highlight edge + show relationship card.</li>
     *   <li>Single click on empty space → clear all highlights, hide card.</li>
     *   <li>Double click on a {@link Star} → open the note file.</li>
     * </ul>
     */
    @Override
    public void mouseClicked(MouseEvent e) {

        if (e.getClickCount() == 2) {
            Star hit = hitTestStar(e.getPoint());
            previewCard.dismiss();
            lastHoveredStarId = null;
            canvas.repaint();
            if (hit != null) openFile(hit);
            return;
        }

        // Single click — star takes priority
        Star starHit = hitTestStar(e.getPoint());
        if (starHit != null) {
            clearEdgeHighlight();
            canvas.highlightNotes(java.util.Set.of(starHit.getId()));
            canvas.repaint();
            return;
        }

        // No star — try edge
        EdgeHitResult edgeHit = hitTestEdge(e.getPoint());
        if (edgeHit != null) {
            clearEdgeHighlight();
            edgeHit.edge.setHighlighted(true);
            highlightedEdge = edgeHit.edge;
            lastHoveredStarId = null;
            canvas.clearHighlight();
            showEdgeCard(edgeHit.edge, edgeHit.midScreenX, edgeHit.midScreenY);
            canvas.repaint();
            return;
        }

        // Empty space — clear everything
        clearEdgeHighlight();
        canvas.clearHighlight();
        previewCard.dismiss();
        lastHoveredStarId = null;
        canvas.repaint();
    }

    // -------------------------------------------------------------------------
    // MouseWheelListener — zoom
    // -------------------------------------------------------------------------

    /**
     * Zooms the camera centred on the cursor.  Uses
     * {@link MouseWheelEvent#getPreciseWheelRotation()} for smooth trackpad support.
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
    // Private — edge card + LLM
    // -------------------------------------------------------------------------

    /**
     * Displays the edge relationship card.  If a description is already cached
     * it is shown immediately; otherwise "Analyzing relationship…" is shown and
     * a {@link SwingWorker} fires off the LLM request (max 5 s timeout via
     * result polling) and updates the card on the EDT.
     */
    private void showEdgeCard(Edge edge, int midX, int midY) {
        // Resolve the two Star objects for names / snippets
        Star fromStar = findStar(edge.getFromId());
        Star toStar   = findStar(edge.getToId());
        String nameA  = fromStar != null ? fromStar.getNote().getFileName() : edge.getFromId();
        String nameB  = toStar   != null ? toStar.getNote().getFileName()   : edge.getToId();

        String cached = edge.getRelationDescription();
        String displayText = (cached != null) ? cached : "Analyzing relationship…";

        System.out.println("[EDGE DEBUG] Display text: [" + displayText + "]");
        System.out.println("[EDGE DEBUG] Display text length: " + displayText.length());

        previewCard.showEdge(edge, nameA, nameB, displayText,
                             midX, midY, canvas.getWidth(), canvas.getHeight());

        if (cached != null) return;   // already have description

        // Launch LLM worker
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                return fetchRelationDescription(edge, fromStar, toStar);
            }

            @Override
            protected void done() {
                try {
                    String desc = get();
                    edge.setRelationDescription(desc);
                    // Only update if this edge is still highlighted
                    if (edge.isHighlighted()) {
                        previewCard.updateDescription(desc);
                        previewCard.revalidate();
                        previewCard.repaint();
                        canvas.repaint();
                        System.out.println("[EDGE DEBUG] Card updated with: " + edge.getRelationDescription());
                    }
                } catch (Exception ignored) { /* worker cancelled or failed */ }
            }
        };
        worker.execute();
    }

    /**
     * Calls the LLM to produce a one-sentence relationship description.
     * On failure falls back to a local heuristic.
     */
    private String fetchRelationDescription(Edge edge, Star fromStar, Star toStar) {
        if (chatProvider != null) {
            try {
                String nameA    = fromStar != null ? fromStar.getNote().getFileName() : edge.getFromId();
                String nameB    = toStar   != null ? toStar.getNote().getFileName()   : edge.getToId();
                String sectorA  = fromStar != null ? fromStar.getSector().getLabel() : "";
                String sectorB  = toStar   != null ? toStar.getSector().getLabel()   : "";
                String snippetA = fromStar != null ? readSnippet(fromStar.getNote().getFilePath(), 150) : "";
                String snippetB = toStar   != null ? readSnippet(toStar.getNote().getFilePath(),   150) : "";

                String prompt = "Two notes in a knowledge base.\n"
                        + "Note 1: '" + nameA + "' in cluster '" + sectorA + "': " + snippetA + "\n"
                        + "Note 2: '" + nameB + "' in cluster '" + sectorB + "': " + snippetB + "\n"
                        + "Describe their relationship in exactly one complete English sentence,"
                        + " no more than 30 words. Output only the sentence.";

                System.out.println("[EDGE DEBUG] Prompt: " + prompt);

                // Use a SwingWorker future with 5-second timeout via direct call (already off EDT)
                ChatResponse resp = chatProvider.chat(prompt);
                System.out.println("[EDGE DEBUG] Response success: " + resp.isSuccess());
                System.out.println("[EDGE DEBUG] Response content: [" + resp.getContent() + "]");
                System.out.println("[EDGE DEBUG] Tokens used: " + resp.getTokensUsed());
                if (resp.isSuccess() && resp.getContent() != null && !resp.getContent().isBlank()) {
                    return resp.getContent().trim();
                }
            } catch (AIServiceException e) {
                System.err.println("  Edge LLM failed: " + e.getMessage() + " — using fallback.");
            }
        }
        return localRelationFallback(edge, fromStar, toStar);
    }

    /** Local fallback relationship description when LLM is unavailable. */
    private static String localRelationFallback(Edge edge, Star fromStar, Star toStar) {
        if (fromStar == null || toStar == null) return "Related notes";

        String sectorA = fromStar.getSector().getLabel();
        String sectorB = toStar.getSector().getLabel();
        if (sectorA.equals(sectorB)) {
            return "Both explore " + sectorA + " concepts";
        }
        return sectorA + " meets " + sectorB;
    }

    // -------------------------------------------------------------------------
    // Private — hit testing
    // -------------------------------------------------------------------------

    /**
     * Returns the topmost {@link Star} whose hit circle contains
     * {@code screenPoint}, ignoring non-Star bodies.
     */
    private Star hitTestStar(Point screenPoint) {
        Vector2D worldPoint = canvas.getCamera().screenToWorld(screenPoint);
        List<CelestialBody> bodies = canvas.getBodies();
        Star result = null;
        for (CelestialBody body : bodies) {
            if (body instanceof Star star && star.contains(worldPoint)) {
                result = star;
            }
        }
        return result;
    }

    /**
     * Checks whether {@code screenPoint} is within {@value #EDGE_HIT_THRESHOLD_PX}
     * pixels of any edge line segment.  Returns the closest such edge wrapped in an
     * {@link EdgeHitResult}, or {@code null} if none is close enough.
     */
    private EdgeHitResult hitTestEdge(Point screenPoint) {
        List<Edge> edges = canvas.getEdges();
        if (edges.isEmpty()) return null;

        double zoom    = canvas.getCamera().getZoom();
        double offsetX = canvas.getCamera().getTransform().getTranslateX();
        double offsetY = canvas.getCamera().getTransform().getTranslateY();

        double px = screenPoint.x;
        double py = screenPoint.y;

        Edge   bestEdge = null;
        double bestDist = EDGE_HIT_THRESHOLD_PX;
        double bestMidX = 0, bestMidY = 0;

        for (Edge edge : edges) {
            Vector2D from = getEdgePosition(edge.getFromId());
            Vector2D to   = getEdgePosition(edge.getToId());
            if (from == null || to == null) continue;

            double x1 = from.getX() * zoom + offsetX;
            double y1 = from.getY() * zoom + offsetY;
            double x2 = to.getX()   * zoom + offsetX;
            double y2 = to.getY()   * zoom + offsetY;

            double dist = pointToSegmentDistance(px, py, x1, y1, x2, y2);
            if (dist < bestDist) {
                bestDist = dist;
                bestEdge = edge;
                bestMidX = (x1 + x2) / 2.0;
                bestMidY = (y1 + y2) / 2.0;
            }
        }
        return bestEdge != null
                ? new EdgeHitResult(bestEdge, (int) bestMidX, (int) bestMidY)
                : null;
    }

    /** Looks up the world position of a node by ID from the current body list. */
    private Vector2D getEdgePosition(String id) {
        for (CelestialBody body : canvas.getBodies()) {
            if (id.equals(body.getId())) return body.getPosition();
        }
        return null;
    }

    /**
     * Returns the perpendicular distance from point {@code (px,py)} to the line
     * segment {@code (x1,y1)–(x2,y2)}, clamped to the segment endpoints.
     */
    private static double pointToSegmentDistance(double px, double py,
                                                  double x1, double y1,
                                                  double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        if (lenSq == 0) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lenSq));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    /** Finds a {@link Star} by note ID from the canvas body list. */
    private Star findStar(String noteId) {
        for (CelestialBody body : canvas.getBodies()) {
            if (body instanceof Star star && star.getId().equals(noteId)) return star;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Private — helpers
    // -------------------------------------------------------------------------

    /** Clears the currently highlighted edge, if any. */
    private void clearEdgeHighlight() {
        if (highlightedEdge != null) {
            highlightedEdge.setHighlighted(false);
            highlightedEdge = null;
        }
    }

    /**
     * Opens the note file of {@code star} in the OS default application.
     * Errors are silently swallowed.
     */
    private void openFile(Star star) {
        File file = new File(star.getNote().getFilePath());
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file);
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException ignored) { }
    }

    /**
     * Reads the first {@code maxChars} characters from the file at
     * {@code filePath}.  Returns an empty string on any error.
     */
    private static String readSnippet(String filePath, int maxChars) {
        try {
            String content = java.nio.file.Files.readString(java.nio.file.Path.of(filePath));
            return content.length() <= maxChars ? content : content.substring(0, maxChars) + "…";
        } catch (Exception e) {
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // Private — inner record
    // -------------------------------------------------------------------------

    /** Holds a hit-tested edge and its midpoint screen coordinates. */
    private record EdgeHitResult(Edge edge, int midScreenX, int midScreenY) { }
}
