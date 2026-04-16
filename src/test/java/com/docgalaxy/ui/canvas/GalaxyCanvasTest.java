package com.docgalaxy.ui.canvas;

import com.docgalaxy.model.Note;
import com.docgalaxy.model.Sector;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.CelestialBody;
import com.docgalaxy.model.celestial.Nebula;
import com.docgalaxy.model.celestial.Star;
import com.docgalaxy.ui.ThemeManager;
import com.docgalaxy.ui.canvas.layer.OverlayLayer;
import com.docgalaxy.ui.canvas.layer.RenderLayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GalaxyCanvas}.
 *
 * <p>All tests run headless (no display required).  Rendering is verified by
 * painting onto an off-screen {@link BufferedImage}.
 */
class GalaxyCanvasTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private GalaxyCanvas canvas;

    @BeforeEach
    void setUp() {
        canvas = new GalaxyCanvas();
    }

    // -----------------------------------------------------------------------
    // Stubs
    // -----------------------------------------------------------------------

    /** Minimal RenderLayer that counts render() calls. */
    static final class CountingLayer implements RenderLayer {
        final AtomicInteger renderCount = new AtomicInteger();
        @Override public void render(Graphics2D g, AffineTransform t, double z) {
            renderCount.incrementAndGet();
        }
        @Override public boolean needsRepaint() { return false; }
    }

    /** Stub OverlayLayer that records the last values forwarded to it. */
    static final class StubOverlay implements OverlayLayer {
        final AtomicReference<Set<String>>  lastHighlight = new AtomicReference<>(null);
        final AtomicReference<List<String>> lastRoute     = new AtomicReference<>(null);
        @Override public void setHighlightedNotes(Set<String> ids)    { lastHighlight.set(ids); }
        @Override public void setNavigationRoute(List<String> ids)    { lastRoute.set(ids); }
        @Override public void render(Graphics2D g, AffineTransform t, double z) {}
        @Override public boolean needsRepaint() { return false; }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Star star(String noteId, Vector2D pos, String sectorId) {
        Note note     = new Note(noteId, "/path/" + noteId, noteId + ".md");
        Sector sector = new Sector(sectorId, "Sector " + sectorId, Color.BLUE);
        return new Star(note, pos, 8.0, sector);
    }

    private static Nebula nebula(String sectorId, Vector2D pos) {
        Sector sector = new Sector(sectorId, "Sector " + sectorId, Color.GREEN);
        return new Nebula(sector, pos, 40.0);
    }

    private static Graphics2D offscreenGraphics() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        return img.createGraphics();
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Test
    void constructor_cameraIsNotNull() {
        assertNotNull(canvas.getCamera());
    }

    @Test
    void constructor_doubleBufferedEnabled() {
        assertTrue(canvas.isDoubleBuffered());
    }

    @Test
    void constructor_backgroundIsThemePrimary() {
        assertEquals(ThemeManager.BG_PRIMARY, canvas.getBackground());
    }

    @Test
    void constructor_layersEmptyInitially() {
        assertTrue(canvas.getLayers().isEmpty());
    }

    @Test
    void constructor_bodiesEmptyInitially() {
        assertTrue(canvas.getBodies().isEmpty());
    }

    @Test
    void constructor_highlightedNotesEmptyInitially() {
        assertTrue(canvas.getHighlightedNotes().isEmpty());
    }

    @Test
    void constructor_navigationRouteEmptyInitially() {
        assertTrue(canvas.getNavigationRoute().isEmpty());
    }

    // -----------------------------------------------------------------------
    // Layer management
    // -----------------------------------------------------------------------

    @Test
    void addLayer_null_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> canvas.addLayer(null));
    }

    @Test
    void addLayer_appearsInLayers() {
        CountingLayer layer = new CountingLayer();
        canvas.addLayer(layer);
        assertTrue(canvas.getLayers().contains(layer));
    }

    @Test
    void addLayer_multipleLayersPreserveOrder() {
        CountingLayer l1 = new CountingLayer();
        CountingLayer l2 = new CountingLayer();
        CountingLayer l3 = new CountingLayer();
        canvas.addLayer(l1);
        canvas.addLayer(l2);
        canvas.addLayer(l3);
        assertEquals(List.of(l1, l2, l3), canvas.getLayers());
    }

    @Test
    void removeLayer_removesFromList() {
        CountingLayer layer = new CountingLayer();
        canvas.addLayer(layer);
        canvas.removeLayer(layer);
        assertFalse(canvas.getLayers().contains(layer));
    }

    @Test
    void addLayer_overlayLayer_autoDetected() {
        StubOverlay overlay = new StubOverlay();
        canvas.addLayer(overlay);
        // Verify forwarding works (highlight should reach the overlay)
        canvas.highlightNotes(Set.of("n1"));
        assertNotNull(overlay.lastHighlight.get());
        assertTrue(overlay.lastHighlight.get().contains("n1"));
    }

    @Test
    void removeLayer_overlayLayer_clearsForwarding() {
        StubOverlay overlay = new StubOverlay();
        canvas.addLayer(overlay);
        canvas.removeLayer(overlay);
        // After removal, highlightNotes must not crash, and overlay must not receive anything
        canvas.highlightNotes(Set.of("x"));
        assertNull(overlay.lastHighlight.get()); // never set after removal
    }

    @Test
    void getLayers_resultIsUnmodifiable() {
        canvas.addLayer(new CountingLayer());
        assertThrows(UnsupportedOperationException.class,
                () -> canvas.getLayers().add(new CountingLayer()));
    }

    // -----------------------------------------------------------------------
    // Body management
    // -----------------------------------------------------------------------

    @Test
    void setBodies_replacesPrevious() {
        canvas.setBodies(List.of(star("n1", new Vector2D(0, 0), "s1")));
        canvas.setBodies(List.of(star("n2", new Vector2D(10, 10), "s1"),
                                  star("n3", new Vector2D(20, 20), "s1")));
        assertEquals(2, canvas.getBodies().size());
    }

    @Test
    void setBodies_null_clearsBodies() {
        canvas.setBodies(List.of(star("n1", new Vector2D(0, 0), "s1")));
        canvas.setBodies(null);
        assertTrue(canvas.getBodies().isEmpty());
    }

    @Test
    void getBodies_resultIsUnmodifiable() {
        canvas.setBodies(List.of(star("n1", new Vector2D(0, 0), "s1")));
        assertThrows(UnsupportedOperationException.class,
                () -> canvas.getBodies().clear());
    }

    // -----------------------------------------------------------------------
    // Hit testing
    // -----------------------------------------------------------------------

    @Test
    void hitTest_null_returnsNull() {
        assertNull(canvas.hitTest(null));
    }

    @Test
    void hitTest_noBodies_returnsNull() {
        assertNull(canvas.hitTest(new Point(100, 100)));
    }

    @Test
    void hitTest_pointOnBody_returnsBody() {
        // Camera default: zoom=1, offset=(0,0) → screen == world
        Star s = star("n1", new Vector2D(100.0, 100.0), "s1");
        canvas.setBodies(List.of(s));
        // Point exactly at body centre → distance 0 < radius*1.5=12
        CelestialBody hit = canvas.hitTest(new Point(100, 100));
        assertSame(s, hit);
    }

    @Test
    void hitTest_pointFarFromAllBodies_returnsNull() {
        canvas.setBodies(List.of(star("n1", new Vector2D(100.0, 100.0), "s1")));
        assertNull(canvas.hitTest(new Point(500, 500)));
    }

    @Test
    void hitTest_multipleOverlappingBodies_returnsTopmost() {
        // Topmost = last in list (rendered on top)
        Star s1 = star("n1", new Vector2D(50.0, 50.0), "s1");
        Star s2 = star("n2", new Vector2D(50.0, 50.0), "s1");
        canvas.setBodies(List.of(s1, s2));
        assertSame(s2, canvas.hitTest(new Point(50, 50)));
    }

    // -----------------------------------------------------------------------
    // highlightNotes
    // -----------------------------------------------------------------------

    @Test
    void highlightNotes_storesIds() {
        canvas.highlightNotes(Set.of("a", "b"));
        assertTrue(canvas.getHighlightedNotes().containsAll(Set.of("a", "b")));
        assertEquals(2, canvas.getHighlightedNotes().size());
    }

    @Test
    void highlightNotes_null_clearsState() {
        canvas.highlightNotes(Set.of("a"));
        canvas.highlightNotes(null);
        assertTrue(canvas.getHighlightedNotes().isEmpty());
    }

    @Test
    void highlightNotes_replacesExistingSet() {
        canvas.highlightNotes(Set.of("a", "b"));
        canvas.highlightNotes(Set.of("c"));
        assertEquals(Set.of("c"), canvas.getHighlightedNotes());
    }

    @Test
    void highlightNotes_forwardsToOverlayLayer() {
        StubOverlay overlay = new StubOverlay();
        canvas.addLayer(overlay);
        canvas.highlightNotes(Set.of("x", "y"));
        assertNotNull(overlay.lastHighlight.get());
        assertTrue(overlay.lastHighlight.get().containsAll(Set.of("x", "y")));
    }

    @Test
    void highlightNotes_returnedSetIsUnmodifiable() {
        canvas.highlightNotes(Set.of("a"));
        assertThrows(UnsupportedOperationException.class,
                () -> canvas.getHighlightedNotes().add("b"));
    }

    // -----------------------------------------------------------------------
    // clearHighlight
    // -----------------------------------------------------------------------

    @Test
    void clearHighlight_removesAllHighlights() {
        canvas.highlightNotes(Set.of("a", "b", "c"));
        canvas.clearHighlight();
        assertTrue(canvas.getHighlightedNotes().isEmpty());
    }

    @Test
    void clearHighlight_forwardsEmptySetToOverlayLayer() {
        StubOverlay overlay = new StubOverlay();
        canvas.addLayer(overlay);
        canvas.highlightNotes(Set.of("a"));
        canvas.clearHighlight();
        assertNotNull(overlay.lastHighlight.get());
        assertTrue(overlay.lastHighlight.get().isEmpty());
    }

    // -----------------------------------------------------------------------
    // showNavigationRoute
    // -----------------------------------------------------------------------

    @Test
    void showNavigationRoute_storesRoute() {
        canvas.showNavigationRoute(List.of("a", "b", "c"));
        assertEquals(List.of("a", "b", "c"), canvas.getNavigationRoute());
    }

    @Test
    void showNavigationRoute_null_clearsRoute() {
        canvas.showNavigationRoute(List.of("a", "b"));
        canvas.showNavigationRoute(null);
        assertTrue(canvas.getNavigationRoute().isEmpty());
    }

    @Test
    void showNavigationRoute_forwardsToOverlayLayer() {
        StubOverlay overlay = new StubOverlay();
        canvas.addLayer(overlay);
        canvas.showNavigationRoute(List.of("p", "q"));
        assertNotNull(overlay.lastRoute.get());
        assertEquals(List.of("p", "q"), overlay.lastRoute.get());
    }

    @Test
    void getNavigationRoute_resultIsUnmodifiable() {
        canvas.showNavigationRoute(List.of("a", "b"));
        assertThrows(UnsupportedOperationException.class,
                () -> canvas.getNavigationRoute().add("c"));
    }

    // -----------------------------------------------------------------------
    // getZoomLevel
    // -----------------------------------------------------------------------

    @Test
    void getZoomLevel_defaultIsOne() {
        assertEquals(1.0, canvas.getZoomLevel(), 1e-9);
    }

    @Test
    void getZoomLevel_afterCameraZoom_reflects() {
        canvas.getCamera().zoomAt(0, 0, 2.0);
        assertEquals(2.0, canvas.getZoomLevel(), 1e-9);
    }

    // -----------------------------------------------------------------------
    // fitAll
    // -----------------------------------------------------------------------

    @Test
    void fitAll_noBodies_doesNotThrow() {
        assertDoesNotThrow(() -> canvas.fitAll());
    }

    @Test
    void fitAll_withBodies_cameraRecentred() {
        // Two bodies at (0,0) and (200,0); world centre = (100,0)
        // After fitAll the camera should have moved from its initial state
        canvas.setBodies(List.of(
                star("n1", new Vector2D(0.0, 0.0), "s"),
                star("n2", new Vector2D(200.0, 0.0), "s")));
        double zoomBefore = canvas.getCamera().getZoom();
        canvas.fitAll();
        // World centre (100,0) must map to viewport centre
        Point p = canvas.getCamera().worldToScreen(new Vector2D(100.0, 0.0));
        assertEquals(canvas.getCamera().getViewportWidth() / 2, p.x,
                "world centre x must map to viewport centre after fitAll");
    }

    // -----------------------------------------------------------------------
    // navigateToNote
    // -----------------------------------------------------------------------

    @Test
    void navigateToNote_null_doesNotThrow() {
        assertDoesNotThrow(() -> canvas.navigateToNote(null));
    }

    @Test
    void navigateToNote_unknownId_doesNotThrow() {
        canvas.setBodies(List.of(star("n1", new Vector2D(50, 50), "s1")));
        assertDoesNotThrow(() -> canvas.navigateToNote("no-such-id"));
    }

    @Test
    void navigateToNote_knownId_centresNoteAtViewportCentre() {
        // Camera default: zoom=1, offset=(0,0), viewportW=1400, viewportH=900
        // Note at (300, 400)
        // After navigate: worldToScreen((300,400)) == (700, 450)
        Star s = star("n1", new Vector2D(300.0, 400.0), "s1");
        canvas.setBodies(List.of(s));
        canvas.navigateToNote("n1");

        Point screen = canvas.getCamera().worldToScreen(new Vector2D(300.0, 400.0));
        assertEquals(canvas.getCamera().getViewportWidth()  / 2, screen.x,
                "navigated note must appear at viewport centre x");
        assertEquals(canvas.getCamera().getViewportHeight() / 2, screen.y,
                "navigated note must appear at viewport centre y");
    }

    @Test
    void navigateToNote_doesNotChangeZoom() {
        canvas.setBodies(List.of(star("n1", new Vector2D(100, 100), "s1")));
        double zoomBefore = canvas.getCamera().getZoom();
        canvas.navigateToNote("n1");
        assertEquals(zoomBefore, canvas.getCamera().getZoom(), 1e-9);
    }

    // -----------------------------------------------------------------------
    // navigateToSector
    // -----------------------------------------------------------------------

    @Test
    void navigateToSector_null_doesNotThrow() {
        assertDoesNotThrow(() -> canvas.navigateToSector(null));
    }

    @Test
    void navigateToSector_unknownId_doesNotThrow() {
        canvas.setBodies(List.of(star("n1", new Vector2D(0, 0), "s1")));
        assertDoesNotThrow(() -> canvas.navigateToSector("no-such-sector"));
    }

    @Test
    void navigateToSector_knownId_centresCentroidAtViewportCentre() {
        // Two stars in "s1" at (100,100) and (300,100) → centroid = (200,100)
        canvas.setBodies(List.of(
                star("n1", new Vector2D(100.0, 100.0), "s1"),
                star("n2", new Vector2D(300.0, 100.0), "s1")));
        canvas.navigateToSector("s1");

        Point screen = canvas.getCamera().worldToScreen(new Vector2D(200.0, 100.0));
        assertEquals(canvas.getCamera().getViewportWidth()  / 2, screen.x,
                "sector centroid must appear at viewport centre x");
        assertEquals(canvas.getCamera().getViewportHeight() / 2, screen.y,
                "sector centroid must appear at viewport centre y");
    }

    @Test
    void navigateToSector_nebulaIncludedInCentroid() {
        // Star in s1 at (0,0); Nebula in s1 at (200,0) → centroid = (100,0)
        canvas.setBodies(List.of(
                star("n1", new Vector2D(0.0, 0.0), "s1"),
                nebula("s1", new Vector2D(200.0, 0.0))));
        canvas.navigateToSector("s1");

        Point screen = canvas.getCamera().worldToScreen(new Vector2D(100.0, 0.0));
        assertEquals(canvas.getCamera().getViewportWidth()  / 2, screen.x);
        assertEquals(canvas.getCamera().getViewportHeight() / 2, screen.y);
    }

    @Test
    void navigateToSector_onlyMatchingSectorBodiesConsidered() {
        // Bodies in s1 and s2; navigate to s2 which has single body at (500,500)
        canvas.setBodies(List.of(
                star("n1", new Vector2D(0.0, 0.0), "s1"),
                star("n2", new Vector2D(500.0, 500.0), "s2")));
        canvas.navigateToSector("s2");

        Point screen = canvas.getCamera().worldToScreen(new Vector2D(500.0, 500.0));
        assertEquals(canvas.getCamera().getViewportWidth()  / 2, screen.x);
        assertEquals(canvas.getCamera().getViewportHeight() / 2, screen.y);
    }

    // -----------------------------------------------------------------------
    // paintComponent
    // -----------------------------------------------------------------------

    @Test
    void paintComponent_noLayers_doesNotThrow() {
        Graphics2D g2 = offscreenGraphics();
        try {
            assertDoesNotThrow(() -> canvas.paintComponent(g2));
        } finally {
            g2.dispose();
        }
    }

    @Test
    void paintComponent_withLayers_callsEachLayer() {
        CountingLayer l1 = new CountingLayer();
        CountingLayer l2 = new CountingLayer();
        canvas.addLayer(l1);
        canvas.addLayer(l2);

        Graphics2D g2 = offscreenGraphics();
        try {
            canvas.paintComponent(g2);
        } finally {
            g2.dispose();
        }

        assertEquals(1, l1.renderCount.get(), "layer 1 must be rendered once");
        assertEquals(1, l2.renderCount.get(), "layer 2 must be rendered once");
    }

    @Test
    void paintComponent_throwingLayer_doesNotPropagateException() {
        canvas.addLayer(new RenderLayer() {
            @Override public void render(Graphics2D g, AffineTransform t, double z) {
                throw new RuntimeException("boom");
            }
            @Override public boolean needsRepaint() { return false; }
        });
        Graphics2D g2 = offscreenGraphics();
        try {
            assertDoesNotThrow(() -> canvas.paintComponent(g2));
        } finally {
            g2.dispose();
        }
    }
}
