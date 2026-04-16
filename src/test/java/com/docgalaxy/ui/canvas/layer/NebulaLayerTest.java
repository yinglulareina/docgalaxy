package com.docgalaxy.ui.canvas.layer;

import com.docgalaxy.model.Sector;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.Nebula;
import com.docgalaxy.util.AppConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NebulaLayer}.
 *
 * <p>All rendering is verified against off-screen {@link BufferedImage}s;
 * no real display or windowing system is required.
 */
class NebulaLayerTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Nebula nebula(String sectorId, double wx, double wy,
                                 double radius, Color color) {
        Sector sector = new Sector(sectorId, "Label-" + sectorId, color);
        return new Nebula(sector, new Vector2D(wx, wy), radius);
    }

    private static Graphics2D offscreen(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).createGraphics();
    }

    private static BufferedImage buf(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    /** Identity camera: zoom=1, offset=(0,0). */
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

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Test
    void constructor_null_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new NebulaLayer(null));
    }

    @Test
    void constructor_emptyList_ok() {
        assertDoesNotThrow(() -> new NebulaLayer(new ArrayList<>()));
    }

    @Test
    void getNebulae_returnsUnmodifiableView() {
        List<Nebula> list = new ArrayList<>();
        list.add(nebula("s1", 0, 0, 50, Color.BLUE));
        NebulaLayer layer = new NebulaLayer(list);
        assertThrows(UnsupportedOperationException.class,
                () -> layer.getNebulae().add(nebula("s2", 1, 1, 50, Color.RED)));
    }

    // -----------------------------------------------------------------------
    // needsRepaint
    // -----------------------------------------------------------------------

    @Test
    void needsRepaint_alwaysTrue() {
        assertTrue(new NebulaLayer(new ArrayList<>()).needsRepaint());
    }

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    @Test
    void showThreshold_matchesAppConstants() {
        assertEquals(AppConstants.NEBULA_SHOW_THRESHOLD, NebulaLayer.SHOW_THRESHOLD, 1e-12);
    }

    @Test
    void fullAlphaZoom_is0_3() {
        assertEquals(0.3, NebulaLayer.FULL_ALPHA_ZOOM, 1e-12);
    }

    @Test
    void blobPeakAlpha_is55() {
        assertEquals(55, NebulaLayer.BLOB_PEAK_ALPHA);
    }

    // -----------------------------------------------------------------------
    // render — no-throw on various inputs
    // -----------------------------------------------------------------------

    @Test
    void render_emptyList_doesNotThrow() {
        NebulaLayer layer = new NebulaLayer(new ArrayList<>());
        Graphics2D g = offscreen(400, 400);
        try { assertDoesNotThrow(() -> layer.render(g, identity(), 0.2)); }
        finally { g.dispose(); }
    }

    @Test
    void render_singleNebula_lowZoom_doesNotThrow() {
        NebulaLayer layer = new NebulaLayer(List.of(nebula("s1", 100, 100, 80, Color.CYAN)));
        Graphics2D g = offscreen(600, 600);
        try { assertDoesNotThrow(() -> layer.render(g, camera(0.2, 0, 0), 0.2)); }
        finally { g.dispose(); }
    }

    @Test
    void render_multipleNebulae_doesNotThrow() {
        List<Nebula> ns = List.of(
                nebula("s1", 100, 100, 80, Color.BLUE),
                nebula("s2", 400, 300, 60, Color.GREEN),
                nebula("s3", 200, 400, 70, Color.MAGENTA));
        NebulaLayer layer = new NebulaLayer(ns);
        Graphics2D g = offscreen(800, 600);
        try { assertDoesNotThrow(() -> layer.render(g, camera(0.1, 0, 0), 0.1)); }
        finally { g.dispose(); }
    }

    // -----------------------------------------------------------------------
    // Visibility gating: skip when zoom > SHOW_THRESHOLD
    // -----------------------------------------------------------------------

    @Test
    void render_zoomAboveThreshold_noPixelsDrawn() {
        BufferedImage img = buf(400, 400);
        Graphics2D g = img.createGraphics();
        List<Nebula> ns = List.of(nebula("s1", 100, 100, 80, Color.RED));
        new NebulaLayer(ns).render(g, camera(0.7, 0, 0), 0.7);
        g.dispose();
        assertFalse(hasAnyPixel(img), "No pixels expected when zoom > SHOW_THRESHOLD");
    }

    @Test
    void render_zoomExactlyAtThreshold_noPixelsDrawn() {
        // At zoom == SHOW_THRESHOLD, (0.6 - 0.6)/0.3 = 0 → alpha = 0
        double zoom = NebulaLayer.SHOW_THRESHOLD;
        BufferedImage img = buf(400, 400);
        Graphics2D g = img.createGraphics();
        new NebulaLayer(List.of(nebula("s1", 100, 100, 80, Color.RED)))
                .render(g, camera(zoom, 0, 0), zoom);
        g.dispose();
        assertFalse(hasAnyPixel(img), "Alpha=0 at threshold — no pixels expected");
    }

    @Test
    void render_zoomBelowThreshold_drawsPixels() {
        double zoom = 0.1;  // well below threshold → fully opaque
        BufferedImage img = buf(600, 600);
        Graphics2D g = img.createGraphics();
        new NebulaLayer(List.of(nebula("s1", 100, 100, 80, Color.BLUE)))
                .render(g, camera(zoom, 0, 0), zoom);
        g.dispose();
        assertTrue(hasAnyPixel(img), "Pixels expected at low zoom");
    }

    // -----------------------------------------------------------------------
    // Alpha formula
    // -----------------------------------------------------------------------

    @Test
    void alphaFormula_atFullAlphaZoom_isOne() {
        // zoom = FULL_ALPHA_ZOOM (0.3) → (0.6 - 0.3) / 0.3 = 1.0
        double zoom = NebulaLayer.FULL_ALPHA_ZOOM;
        float expected = 1.0f;
        float computed = (float) Math.min(1.0,
                Math.max(0.0, (NebulaLayer.SHOW_THRESHOLD - zoom)
                             / (NebulaLayer.SHOW_THRESHOLD - NebulaLayer.FULL_ALPHA_ZOOM)));
        assertEquals(expected, computed, 1e-6f);
    }

    @Test
    void alphaFormula_atZeroZoom_clampedToOne() {
        // zoom=0 → (0.6 - 0) / 0.3 = 2.0 → clamped to 1.0
        double zoom = 0.0;
        float computed = (float) Math.min(1.0,
                Math.max(0.0, (NebulaLayer.SHOW_THRESHOLD - zoom)
                             / (NebulaLayer.SHOW_THRESHOLD - NebulaLayer.FULL_ALPHA_ZOOM)));
        assertEquals(1.0f, computed, 1e-6f);
    }

    @Test
    void alphaFormula_atMidpoint_isHalf() {
        // zoom = 0.45 → (0.6 - 0.45) / 0.3 = 0.5
        double zoom = 0.45;
        float computed = (float) Math.min(1.0,
                Math.max(0.0, (NebulaLayer.SHOW_THRESHOLD - zoom)
                             / (NebulaLayer.SHOW_THRESHOLD - NebulaLayer.FULL_ALPHA_ZOOM)));
        assertEquals(0.5f, computed, 1e-5f);
    }

    // -----------------------------------------------------------------------
    // Live list update reflected on next render
    // -----------------------------------------------------------------------

    @Test
    void liveList_addNebula_reflectedOnNextRender() {
        List<Nebula> list = new ArrayList<>();
        NebulaLayer layer = new NebulaLayer(list);

        BufferedImage buf1 = buf(400, 400);
        Graphics2D g1 = buf1.createGraphics();
        layer.render(g1, camera(0.1, 0, 0), 0.1);
        g1.dispose();

        list.add(nebula("s1", 100, 100, 80, Color.ORANGE));

        BufferedImage buf2 = buf(400, 400);
        Graphics2D g2 = buf2.createGraphics();
        layer.render(g2, camera(0.1, 0, 0), 0.1);
        g2.dispose();

        assertFalse(hasAnyPixel(buf1), "Empty list must produce no pixels");
        assertTrue(hasAnyPixel(buf2),  "Non-empty list must produce pixels");
    }

    // -----------------------------------------------------------------------
    // Nebula with alpha=0 produces no pixels
    // -----------------------------------------------------------------------

    @Test
    void nebula_withZeroAlpha_noPixelsDrawn() {
        Sector sector = new Sector("s1", "S1", Color.RED);
        Nebula n = new Nebula(sector, new Vector2D(100, 100), 80);
        n.setAlpha(0f);

        BufferedImage img = buf(400, 400);
        Graphics2D g = img.createGraphics();
        new NebulaLayer(List.of(n)).render(g, camera(0.1, 0, 0), 0.1);
        g.dispose();
        assertFalse(hasAnyPixel(img), "Nebula with alpha=0 must produce no pixels");
    }

    // -----------------------------------------------------------------------
    // Graphics state restored after render
    // -----------------------------------------------------------------------

    @Test
    void render_restoresGraphicsState() {
        NebulaLayer layer = new NebulaLayer(
                List.of(nebula("s1", 100, 100, 80, Color.CYAN)));
        Graphics2D g = offscreen(400, 400);
        try {
            Color colorBefore = g.getColor();
            var   compositeBefore = g.getComposite();
            AffineTransform txBefore = g.getTransform();

            layer.render(g, camera(0.2, 0, 0), 0.2);

            assertEquals(colorBefore,     g.getColor(),     "Color must be restored");
            assertEquals(compositeBefore, g.getComposite(), "Composite must be restored");
            assertEquals(txBefore,        g.getTransform(), "Transform must be restored");
        } finally {
            g.dispose();
        }
    }
}
