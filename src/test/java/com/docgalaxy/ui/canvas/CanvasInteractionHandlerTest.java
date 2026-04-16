package com.docgalaxy.ui.canvas;

import com.docgalaxy.model.Note;
import com.docgalaxy.model.Sector;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.CelestialBody;
import com.docgalaxy.model.celestial.Nebula;
import com.docgalaxy.model.celestial.Star;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;
import java.util.EventListener;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CanvasInteractionHandler}.
 *
 * <p>All tests run in headless mode.  Mouse/wheel events are dispatched by
 * calling the handler methods directly (no real AWT event queue required).
 */
class CanvasInteractionHandlerTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private GalaxyCanvas              canvas;
    private CanvasInteractionHandler  handler;

    @BeforeEach
    void setUp() {
        canvas  = new GalaxyCanvas();           // constructor creates the handler internally
        handler = canvas.getInteractionHandler();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Creates a mouse-clicked event for the given canvas point and click count. */
    private MouseEvent click(int x, int y, int clickCount) {
        return new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, x, y, clickCount, false);
    }

    /** Creates a mouse-pressed event. */
    private MouseEvent press(int x, int y) {
        return new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, x, y, 1, false);
    }

    /** Creates a mouse-dragged event. */
    private MouseEvent drag(int x, int y) {
        return new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED,
                System.currentTimeMillis(), 0, x, y, 0, false);
    }

    /**
     * Creates a MouseWheelEvent with the given precise rotation.
     * Negative = scroll toward user (zoom in).
     */
    private MouseWheelEvent wheel(int x, int y, double rotation) {
        return new MouseWheelEvent(canvas, MouseWheelEvent.MOUSE_WHEEL,
                System.currentTimeMillis(), 0, x, y, x, y, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, (int) Math.signum(rotation),
                rotation);
    }

    private static Star starAt(String noteId, double wx, double wy, String sectorId) {
        Note   note   = new Note(noteId, "/notes/" + noteId + ".md", noteId + ".md");
        Sector sector = new Sector(sectorId, "S " + sectorId, Color.CYAN);
        return new Star(note, new Vector2D(wx, wy), 8.0, sector);
    }

    private static Nebula nebulaAt(String sectorId, double wx, double wy) {
        Sector sector = new Sector(sectorId, "S " + sectorId, Color.BLUE);
        return new Nebula(sector, new Vector2D(wx, wy), 30.0);
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Test
    void constructor_null_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new CanvasInteractionHandler(null));
    }

    @Test
    void constructor_previewCardIsNotNull() {
        assertNotNull(handler.getPreviewCard());
    }

    @Test
    void constructor_previewCardInitiallyHidden() {
        assertFalse(handler.getPreviewCard().isVisible());
    }

    @Test
    void constructor_previewCardAddedToCanvas() {
        boolean found = Arrays.asList(canvas.getComponents())
                              .contains(handler.getPreviewCard());
        assertTrue(found, "PreviewCard must be a child component of GalaxyCanvas");
    }

    @Test
    void constructor_selfRegisteredAsMouseListener() {
        EventListener[] ls = canvas.getListeners(java.awt.event.MouseListener.class);
        boolean found = Arrays.asList(ls).contains(handler);
        assertTrue(found, "handler must be registered as MouseListener on canvas");
    }

    @Test
    void constructor_selfRegisteredAsMouseMotionListener() {
        EventListener[] ls = canvas.getListeners(java.awt.event.MouseMotionListener.class);
        assertTrue(Arrays.asList(ls).contains(handler),
                "handler must be registered as MouseMotionListener on canvas");
    }

    @Test
    void constructor_selfRegisteredAsMouseWheelListener() {
        EventListener[] ls = canvas.getListeners(java.awt.event.MouseWheelListener.class);
        assertTrue(Arrays.asList(ls).contains(handler),
                "handler must be registered as MouseWheelListener on canvas");
    }

    // -----------------------------------------------------------------------
    // ZOOM_STEP constant
    // -----------------------------------------------------------------------

    @Test
    void zoomStep_is1_05() {
        assertEquals(1.05, CanvasInteractionHandler.ZOOM_STEP, 1e-12);
    }

    // -----------------------------------------------------------------------
    // Drag → camera.pan()
    // -----------------------------------------------------------------------

    @Test
    void drag_pansCamera() {
        handler.mousePressed(press(50, 50));
        handler.mouseDragged(drag(80, 70));
        assertEquals(30.0, canvas.getCamera().getOffsetX(), 1e-9);
        assertEquals(20.0, canvas.getCamera().getOffsetY(), 1e-9);
    }

    @Test
    void drag_accumulatesOverMultipleEvents() {
        handler.mousePressed(press(0, 0));
        handler.mouseDragged(drag(10, 5));
        handler.mouseDragged(drag(25, 15));   // delta from (10,5) → (15,10)
        assertEquals(25.0, canvas.getCamera().getOffsetX(), 1e-9);
        assertEquals(15.0, canvas.getCamera().getOffsetY(), 1e-9);
    }

    @Test
    void drag_doesNotChangeZoom() {
        double zoomBefore = canvas.getCamera().getZoom();
        handler.mousePressed(press(0, 0));
        handler.mouseDragged(drag(100, 200));
        assertEquals(zoomBefore, canvas.getCamera().getZoom(), 1e-9);
    }

    // -----------------------------------------------------------------------
    // Wheel → camera.zoomAt()  (ZOOM_STEP = 1.05)
    // -----------------------------------------------------------------------

    @Test
    void wheel_negativeRotation_zoomsIn() {
        // rotation = -1 → factor = 1.05^1 = 1.05 → zoom becomes 1.05
        handler.mouseWheelMoved(wheel(0, 0, -1.0));
        assertEquals(1.05, canvas.getCamera().getZoom(), 1e-9);
    }

    @Test
    void wheel_positiveRotation_zoomsOut() {
        // rotation = +1 → factor = 1.05^(-1) ≈ 0.9524
        handler.mouseWheelMoved(wheel(0, 0, 1.0));
        assertEquals(1.0 / 1.05, canvas.getCamera().getZoom(), 1e-9);
    }

    @Test
    void wheel_centredOnCursor_pivotWorldPointUnchanged() {
        // World point at screen (100, 80) must stay fixed after wheel
        Vector2D worldBefore = canvas.getCamera().screenToWorld(
                new java.awt.Point(100, 80));
        handler.mouseWheelMoved(wheel(100, 80, -2.0));
        Vector2D worldAfter = canvas.getCamera().screenToWorld(
                new java.awt.Point(100, 80));
        assertEquals(worldBefore.getX(), worldAfter.getX(), 1e-9);
        assertEquals(worldBefore.getY(), worldAfter.getY(), 1e-9);
    }

    @Test
    void wheel_multipleTicks_zoomCompounds() {
        handler.mouseWheelMoved(wheel(0, 0, -1.0));
        handler.mouseWheelMoved(wheel(0, 0, -1.0));
        // zoom = 1.05 * 1.05 = 1.05^2
        assertEquals(1.05 * 1.05, canvas.getCamera().getZoom(), 1e-9);
    }

    // -----------------------------------------------------------------------
    // Single click — show/hide PreviewCard
    // -----------------------------------------------------------------------

    @Test
    void singleClick_emptyCanvas_previewCardHidden() {
        handler.mouseClicked(click(200, 200, 1));
        assertFalse(handler.getPreviewCard().isVisible());
    }

    @Test
    void singleClick_onBody_previewCardVisible() {
        // Single click on star highlights the star; PreviewCard is shown on hover, not click.
        canvas.setBodies(List.of(starAt("n1", 100.0, 100.0, "s1")));
        handler.mouseClicked(click(100, 100, 1));
        assertFalse(handler.getPreviewCard().isVisible(),
                "PreviewCard must not appear on single-click (hover-only card)");
    }

    @Test
    void singleClick_onBody_thenClickEmpty_cardHidden() {
        canvas.setBodies(List.of(starAt("n1", 100.0, 100.0, "s1")));
        handler.mouseClicked(click(100, 100, 1));  // show
        handler.mouseClicked(click(400, 400, 1));  // hide (no body there)
        assertFalse(handler.getPreviewCard().isVisible());
    }

    @Test
    void singleClick_topmostBodyHit_whenOverlapping() {
        // Both stars at same world position; second is topmost — clicking highlights a star.
        Star s1 = starAt("n1", 50.0, 50.0, "s1");
        Star s2 = starAt("n2", 50.0, 50.0, "s1");
        canvas.setBodies(List.of(s1, s2));
        handler.mouseClicked(click(50, 50, 1));
        // Highlight set must be non-empty (topmost star n2 is highlighted)
        assertFalse(canvas.getHighlightedNotes().isEmpty());
    }

    @Test
    void singleClick_outsideRadius_cardNotShown() {
        // Body at (50,50) radius=8; 1.5× = 12. Click at (100,100): dist≈70 → miss
        canvas.setBodies(List.of(starAt("n1", 50.0, 50.0, "s1")));
        handler.mouseClicked(click(100, 100, 1));
        assertFalse(handler.getPreviewCard().isVisible());
    }

    @Test
    void singleClick_onNebula_previewCardVisible() {
        // Nebula clicks fall through to empty-space handling; preview card stays hidden.
        canvas.setBodies(List.of(nebulaAt("s2", 200.0, 200.0)));
        handler.mouseClicked(click(200, 200, 1));
        assertFalse(handler.getPreviewCard().isVisible());
    }

    // -----------------------------------------------------------------------
    // Double click — hide card, open file
    // -----------------------------------------------------------------------

    @Test
    void doubleClick_onStar_hidesPreviewCard() {
        canvas.setBodies(List.of(starAt("n1", 100.0, 100.0, "s1")));
        handler.mouseClicked(click(100, 100, 1));   // show first
        handler.mouseClicked(click(100, 100, 2));   // double click hides
        assertFalse(handler.getPreviewCard().isVisible());
    }

    @Test
    void doubleClick_onStar_doesNotThrow() {
        // File does not exist; Desktop.open() will fail — must be swallowed
        canvas.setBodies(List.of(starAt("n1", 100.0, 100.0, "s1")));
        assertDoesNotThrow(() -> handler.mouseClicked(click(100, 100, 2)));
    }

    @Test
    void doubleClick_onNebula_doesNotThrow() {
        // Nebula is not a Star → openFile is a no-op
        canvas.setBodies(List.of(nebulaAt("s1", 100.0, 100.0)));
        assertDoesNotThrow(() -> handler.mouseClicked(click(100, 100, 2)));
    }

    @Test
    void doubleClick_emptyCanvas_doesNotThrow() {
        assertDoesNotThrow(() -> handler.mouseClicked(click(200, 200, 2)));
    }

    @Test
    void doubleClick_emptyCanvas_cardRemainsHidden() {
        handler.mouseClicked(click(200, 200, 2));
        assertFalse(handler.getPreviewCard().isVisible());
    }

    // -----------------------------------------------------------------------
    // PreviewCard — content
    // -----------------------------------------------------------------------

    @Test
    void previewCard_titleReflectsBody_afterClick() {
        // Single click highlights the star; preview card is hover-only and stays hidden.
        Star s = starAt("myNote", 50.0, 50.0, "s1");
        canvas.setBodies(List.of(s));
        handler.mouseClicked(click(50, 50, 1));
        assertFalse(handler.getPreviewCard().isVisible());
        // The star must be in the highlighted set instead
        assertTrue(canvas.getHighlightedNotes().contains("myNote"));
    }
}
