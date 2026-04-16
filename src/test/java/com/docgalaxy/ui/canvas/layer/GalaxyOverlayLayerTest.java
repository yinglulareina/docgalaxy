package com.docgalaxy.ui.canvas.layer;

import com.docgalaxy.model.Note;
import com.docgalaxy.model.Sector;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.Star;
import com.docgalaxy.util.AppConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GalaxyOverlayLayer}.
 */
class GalaxyOverlayLayerTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Star star(String id, double wx, double wy, double r, Color color) {
        Note note = new Note(id, "/notes/" + id + ".md", id + ".md");
        Sector sector = new Sector(id + "-s", "S-" + id, color);
        return new Star(note, new Vector2D(wx, wy), r, sector);
    }

    private static BufferedImage buf(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    private static Graphics2D offscreen(int w, int h) {
        return buf(w, h).createGraphics();
    }

    private static AffineTransform identity() { return new AffineTransform(); }

    private static AffineTransform camera(double zoom, double tx, double ty) {
        AffineTransform at = new AffineTransform();
        at.translate(tx, ty);
        at.scale(zoom, zoom);
        return at;
    }

    private static boolean hasAnyPixel(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                if ((img.getRGB(x, y) >>> 24) != 0) return true;
        return false;
    }

    private static int maxAlpha(BufferedImage img) {
        int max = 0;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                max = Math.max(max, (img.getRGB(x, y) >>> 24) & 0xFF);
        return max;
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Test
    void constructor_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> new GalaxyOverlayLayer(null));
    }

    @Test
    void constructor_emptyList_ok() {
        assertDoesNotThrow(() -> new GalaxyOverlayLayer(new ArrayList<>()));
    }

    // -----------------------------------------------------------------------
    // OverlayLayer API — setHighlightedNotes
    // -----------------------------------------------------------------------

    @Test
    void setHighlightedNotes_storesIds() {
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(new ArrayList<>());
        layer.setHighlightedNotes(Set.of("a", "b"));
        assertTrue(layer.getHighlightedIds().containsAll(Set.of("a", "b")));
    }

    @Test
    void setHighlightedNotes_null_clearsSet() {
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(new ArrayList<>());
        layer.setHighlightedNotes(Set.of("a"));
        layer.setHighlightedNotes(null);
        assertTrue(layer.getHighlightedIds().isEmpty());
    }

    @Test
    void setHighlightedNotes_replacesExisting() {
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(new ArrayList<>());
        layer.setHighlightedNotes(Set.of("a", "b"));
        layer.setHighlightedNotes(Set.of("c"));
        assertEquals(Set.of("c"), layer.getHighlightedIds());
    }

    // -----------------------------------------------------------------------
    // OverlayLayer API — setNavigationRoute
    // -----------------------------------------------------------------------

    @Test
    void setNavigationRoute_storesRoute() {
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(new ArrayList<>());
        layer.setNavigationRoute(List.of("a", "b", "c"));
        assertEquals(List.of("a", "b", "c"), layer.getNavigationRoute());
    }

    @Test
    void setNavigationRoute_null_clearsRoute() {
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(new ArrayList<>());
        layer.setNavigationRoute(List.of("a", "b"));
        layer.setNavigationRoute(null);
        assertTrue(layer.getNavigationRoute().isEmpty());
    }

    // -----------------------------------------------------------------------
    // needsRepaint
    // -----------------------------------------------------------------------

    @Test
    void needsRepaint_alwaysTrue() {
        assertTrue(new GalaxyOverlayLayer(new ArrayList<>()).needsRepaint());
    }

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    @Test
    void maskAlpha_matchesAppConstants() {
        assertEquals(AppConstants.SEARCH_MASK_ALPHA, GalaxyOverlayLayer.MASK_ALPHA, 1e-6f);
    }

    @Test
    void glowRadiusMultiplier_matchesAppConstants() {
        assertEquals(AppConstants.SEARCH_GLOW_RADIUS_MULTIPLIER,
                     GalaxyOverlayLayer.GLOW_RADIUS_MULTIPLIER);
    }

    // -----------------------------------------------------------------------
    // render — no-throw
    // -----------------------------------------------------------------------

    @Test
    void render_noHighlightNoRoute_doesNotThrow() {
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(new ArrayList<>());
        Graphics2D g = offscreen(400, 300);
        try { assertDoesNotThrow(() -> layer.render(g, identity(), 1.0)); }
        finally { g.dispose(); }
    }

    @Test
    void render_withHighlight_doesNotThrow() {
        List<Star> stars = List.of(star("n1", 100, 100, 8, Color.CYAN));
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(stars);
        layer.setHighlightedNotes(Set.of("n1"));
        Graphics2D g = offscreen(400, 300);
        try { assertDoesNotThrow(() -> layer.render(g, camera(1.0, 0, 0), 1.0)); }
        finally { g.dispose(); }
    }

    @Test
    void render_withRoute_doesNotThrow() {
        List<Star> stars = List.of(
                star("a", 50, 50, 8, Color.BLUE),
                star("b", 150, 100, 8, Color.GREEN),
                star("c", 250, 50, 8, Color.RED));
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(stars);
        layer.setNavigationRoute(List.of("a", "b", "c"));
        Graphics2D g = offscreen(400, 300);
        try { assertDoesNotThrow(() -> layer.render(g, camera(1.0, 0, 0), 1.0)); }
        finally { g.dispose(); }
    }

    @Test
    void render_highlightAndRoute_doesNotThrow() {
        List<Star> stars = List.of(
                star("a", 50, 50, 8, Color.BLUE),
                star("b", 150, 100, 8, Color.GREEN));
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(stars);
        layer.setHighlightedNotes(Set.of("a"));
        layer.setNavigationRoute(List.of("a", "b"));
        Graphics2D g = offscreen(400, 300);
        try { assertDoesNotThrow(() -> layer.render(g, camera(1.0, 0, 0), 1.0)); }
        finally { g.dispose(); }
    }

    @Test
    void render_unknownHighlightId_doesNotThrow() {
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(new ArrayList<>());
        layer.setHighlightedNotes(Set.of("ghost"));
        Graphics2D g = offscreen(200, 200);
        try { assertDoesNotThrow(() -> layer.render(g, identity(), 1.0)); }
        finally { g.dispose(); }
    }

    @Test
    void render_routeWithUnknownIds_doesNotThrow() {
        List<Star> stars = List.of(star("known", 50, 50, 8, Color.CYAN));
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(stars);
        layer.setNavigationRoute(List.of("known", "ghost", "known"));
        Graphics2D g = offscreen(400, 300);
        try { assertDoesNotThrow(() -> layer.render(g, camera(1.0, 0, 0), 1.0)); }
        finally { g.dispose(); }
    }

    @Test
    void render_routeWithSingleElement_doesNotThrow() {
        // Single-element route → no segments → no-op (needs >= 2)
        List<Star> stars = List.of(star("a", 50, 50, 8, Color.CYAN));
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(stars);
        layer.setNavigationRoute(List.of("a"));
        Graphics2D g = offscreen(200, 200);
        try { assertDoesNotThrow(() -> layer.render(g, camera(1.0, 0, 0), 1.0)); }
        finally { g.dispose(); }
    }

    // -----------------------------------------------------------------------
    // Highlight draws mask — produces pixels
    // -----------------------------------------------------------------------

    @Test
    void highlight_drawsMask_producesPixels() {
        // Even if the highlighted star is off-screen, the mask is drawn
        List<Star> stars = List.of(star("n1", 100, 100, 8, Color.RED));
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(stars);
        layer.setHighlightedNotes(Set.of("n1"));

        BufferedImage img = buf(400, 300);
        Graphics2D g = img.createGraphics();
        g.setClip(0, 0, 400, 300);
        layer.render(g, camera(1.0, 0, 0), 1.0);
        g.dispose();

        assertTrue(hasAnyPixel(img), "Highlight mask must produce pixels");
    }

    @Test
    void noHighlightNoRoute_noPixelsDrawn() {
        List<Star> stars = List.of(star("n1", 100, 100, 8, Color.RED));
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(stars);
        // No highlight, no route set

        BufferedImage img = buf(200, 200);
        Graphics2D g = img.createGraphics();
        layer.render(g, camera(1.0, 0, 0), 1.0);
        g.dispose();

        assertFalse(hasAnyPixel(img), "Nothing to render → no pixels");
    }

    // -----------------------------------------------------------------------
    // Route draws pixels when stars are on-screen
    // -----------------------------------------------------------------------

    @Test
    void route_drawsPixels() {
        List<Star> stars = new ArrayList<>();
        stars.add(star("a", 20, 50, 6, Color.BLUE));
        stars.add(star("b", 180, 50, 6, Color.GREEN));
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(stars);
        layer.setNavigationRoute(List.of("a", "b"));

        BufferedImage img = buf(200, 100);
        Graphics2D g = img.createGraphics();
        layer.render(g, camera(1.0, 0, 0), 1.0);
        g.dispose();

        assertTrue(hasAnyPixel(img), "Route line must produce pixels");
    }

    // -----------------------------------------------------------------------
    // Clearing highlight removes mask
    // -----------------------------------------------------------------------

    @Test
    void clearHighlight_noMoreMask() {
        List<Star> stars = List.of(star("n1", 100, 100, 8, Color.CYAN));
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(stars);
        layer.setHighlightedNotes(Set.of("n1"));
        layer.setHighlightedNotes(Collections.emptySet());

        BufferedImage img = buf(200, 200);
        Graphics2D g = img.createGraphics();
        layer.render(g, camera(1.0, 0, 0), 1.0);
        g.dispose();

        assertFalse(hasAnyPixel(img), "Cleared highlight → no mask → no pixels");
    }

    // -----------------------------------------------------------------------
    // Graphics state restored
    // -----------------------------------------------------------------------

    @Test
    void render_restoresGraphicsState_withHighlight() {
        List<Star> stars = List.of(star("n1", 100, 100, 8, Color.CYAN));
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(stars);
        layer.setHighlightedNotes(Set.of("n1"));

        Graphics2D g = offscreen(400, 300);
        try {
            var colorBefore     = g.getColor();
            var compositeBefore = g.getComposite();
            var strokeBefore    = g.getStroke();

            g.setClip(0, 0, 400, 300);
            layer.render(g, camera(1.0, 0, 0), 1.0);

            assertEquals(colorBefore,     g.getColor(),     "Color must be restored");
            assertEquals(compositeBefore, g.getComposite(), "Composite must be restored");
            assertEquals(strokeBefore,    g.getStroke(),    "Stroke must be restored");
        } finally {
            g.dispose();
        }
    }

    @Test
    void render_restoresGraphicsState_withRoute() {
        List<Star> stars = new ArrayList<>();
        stars.add(star("a", 50, 50, 8, Color.BLUE));
        stars.add(star("b", 150, 50, 8, Color.RED));
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(stars);
        layer.setNavigationRoute(List.of("a", "b"));

        Graphics2D g = offscreen(400, 200);
        try {
            var colorBefore     = g.getColor();
            var compositeBefore = g.getComposite();
            var strokeBefore    = g.getStroke();

            layer.render(g, camera(1.0, 0, 0), 1.0);

            assertEquals(colorBefore,     g.getColor(),     "Color must be restored");
            assertEquals(compositeBefore, g.getComposite(), "Composite must be restored");
            assertEquals(strokeBefore,    g.getStroke(),    "Stroke must be restored");
        } finally {
            g.dispose();
        }
    }

    // -----------------------------------------------------------------------
    // Implements OverlayLayer interface contract
    // -----------------------------------------------------------------------

    @Test
    void implementsOverlayLayer() {
        GalaxyOverlayLayer layer = new GalaxyOverlayLayer(new ArrayList<>());
        assertInstanceOf(OverlayLayer.class, layer);
        assertInstanceOf(RenderLayer.class,  layer);
    }
}
