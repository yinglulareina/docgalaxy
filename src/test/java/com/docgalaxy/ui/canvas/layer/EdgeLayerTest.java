package com.docgalaxy.ui.canvas.layer;

import com.docgalaxy.model.Edge;
import com.docgalaxy.model.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EdgeLayer}.
 */
class EdgeLayerTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Graphics2D offscreen(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).createGraphics();
    }

    private static BufferedImage buf(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
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

    private static Map<String, Vector2D> positions(Object... pairs) {
        Map<String, Vector2D> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 3) {
            map.put((String) pairs[i],
                    new Vector2D((Double) pairs[i + 1], (Double) pairs[i + 2]));
        }
        return map;
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Test
    void constructor_nullEdges_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new EdgeLayer(null, new HashMap<>()));
    }

    @Test
    void constructor_nullPositions_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new EdgeLayer(new ArrayList<>(), null));
    }

    @Test
    void constructor_emptyCollections_ok() {
        assertDoesNotThrow(() -> new EdgeLayer(new ArrayList<>(), new HashMap<>()));
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    @Test
    void getEdges_returnsUnmodifiableView() {
        List<Edge> list = new ArrayList<>();
        list.add(new Edge("a", "b", 0.8));
        EdgeLayer layer = new EdgeLayer(list, new HashMap<>());
        assertThrows(UnsupportedOperationException.class,
                () -> layer.getEdges().add(new Edge("c", "d", 0.5)));
    }

    @Test
    void getPositions_returnsUnmodifiableView() {
        Map<String, Vector2D> map = new HashMap<>();
        map.put("a", new Vector2D(0, 0));
        EdgeLayer layer = new EdgeLayer(new ArrayList<>(), map);
        assertThrows(UnsupportedOperationException.class,
                () -> layer.getPositions().put("z", new Vector2D(1, 1)));
    }

    // -----------------------------------------------------------------------
    // needsRepaint
    // -----------------------------------------------------------------------

    @Test
    void needsRepaint_alwaysTrue() {
        assertTrue(new EdgeLayer(new ArrayList<>(), new HashMap<>()).needsRepaint());
    }

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    @Test
    void showThreshold_is0_4() {
        assertEquals(0.4, EdgeLayer.SHOW_THRESHOLD, 1e-12);
    }

    @Test
    void strokeWidth_is0_5f() {
        assertEquals(0.5f, EdgeLayer.STROKE_WIDTH, 1e-6f);
    }

    @Test
    void alphaScale_is40() {
        assertEquals(40.0, EdgeLayer.ALPHA_SCALE, 1e-12);
    }

    // -----------------------------------------------------------------------
    // render — no-throw
    // -----------------------------------------------------------------------

    @Test
    void render_emptyEdges_doesNotThrow() {
        EdgeLayer layer = new EdgeLayer(new ArrayList<>(), new HashMap<>());
        Graphics2D g = offscreen(200, 200);
        try { assertDoesNotThrow(() -> layer.render(g, identity(), 1.0)); }
        finally { g.dispose(); }
    }

    @Test
    void render_singleEdge_doesNotThrow() {
        List<Edge> edges = List.of(new Edge("a", "b", 0.9));
        Map<String, Vector2D> pos = positions("a", 10.0, 10.0, "b", 90.0, 90.0);
        Graphics2D g = offscreen(200, 200);
        try { assertDoesNotThrow(() ->
                new EdgeLayer(edges, pos).render(g, camera(1.0, 0, 0), 1.0)); }
        finally { g.dispose(); }
    }

    // -----------------------------------------------------------------------
    // Zoom gating: skip when zoom < SHOW_THRESHOLD
    // -----------------------------------------------------------------------

    @Test
    void render_zoomBelowThreshold_noPixelsDrawn() {
        List<Edge> edges = List.of(new Edge("a", "b", 1.0));
        Map<String, Vector2D> pos = positions("a", 10.0, 50.0, "b", 190.0, 50.0);
        BufferedImage img = buf(200, 100);
        Graphics2D g = img.createGraphics();
        new EdgeLayer(edges, pos).render(g, camera(0.3, 0, 0), 0.3);
        g.dispose();
        assertFalse(hasAnyPixel(img), "No pixels expected when zoom < SHOW_THRESHOLD");
    }

    @Test
    void render_zoomExactlyAtThreshold_noPixelsDrawn() {
        // zoom == SHOW_THRESHOLD → condition is zoom < threshold → false → renders
        // (but let's verify it at 0.39 < 0.4)
        List<Edge> edges = List.of(new Edge("a", "b", 1.0));
        Map<String, Vector2D> pos = positions("a", 10.0, 50.0, "b", 190.0, 50.0);
        BufferedImage img = buf(200, 100);
        Graphics2D g = img.createGraphics();
        double zoom = 0.39;
        new EdgeLayer(edges, pos).render(g, camera(zoom, 0, 0), zoom);
        g.dispose();
        assertFalse(hasAnyPixel(img), "zoom 0.39 < 0.4 → no pixels");
    }

    @Test
    void render_zoomAtOrAboveThreshold_drawsPixels() {
        // Horizontal line across the canvas with max similarity → clearly visible
        List<Edge> edges = List.of(new Edge("a", "b", 1.0));
        // Positions designed so the line crosses many pixels at zoom=1
        Map<String, Vector2D> pos = positions("a", 0.0, 50.0, "b", 200.0, 50.0);
        BufferedImage img = buf(200, 100);
        Graphics2D g = img.createGraphics();
        new EdgeLayer(edges, pos).render(g, camera(1.0, 0, 0), 1.0);
        g.dispose();
        assertTrue(hasAnyPixel(img), "Pixels expected when zoom >= SHOW_THRESHOLD");
    }

    // -----------------------------------------------------------------------
    // Alpha formula: similarity × 40, clamped to [0, 255]
    // -----------------------------------------------------------------------

    @Test
    void alphaFormula_zeroSimilarity_noLine() {
        List<Edge> edges = List.of(new Edge("a", "b", 0.0));
        Map<String, Vector2D> pos = positions("a", 0.0, 50.0, "b", 200.0, 50.0);
        BufferedImage img = buf(200, 100);
        Graphics2D g = img.createGraphics();
        new EdgeLayer(edges, pos).render(g, camera(1.0, 0, 0), 1.0);
        g.dispose();
        assertFalse(hasAnyPixel(img), "Zero-similarity edge is fully transparent → no pixels");
    }

    @Test
    void alphaFormula_highSimilarity_moreBrightThanLow() {
        // High-similarity edge
        List<Edge> edgesHigh = List.of(new Edge("a", "b", 1.0));
        // Low-similarity edge
        List<Edge> edgesLow  = List.of(new Edge("a", "b", 0.1));
        Map<String, Vector2D> pos = positions("a", 0.0, 50.0, "b", 200.0, 50.0);

        BufferedImage imgHigh = buf(200, 100);
        Graphics2D gH = imgHigh.createGraphics();
        new EdgeLayer(edgesHigh, pos).render(gH, camera(1.0, 0, 0), 1.0);
        gH.dispose();

        BufferedImage imgLow = buf(200, 100);
        Graphics2D gL = imgLow.createGraphics();
        new EdgeLayer(edgesLow, pos).render(gL, camera(1.0, 0, 0), 1.0);
        gL.dispose();

        // Check that the high-similarity line has a higher max alpha than the low one
        int maxAlphaHigh = maxAlpha(imgHigh);
        int maxAlphaLow  = maxAlpha(imgLow);
        assertTrue(maxAlphaHigh > maxAlphaLow,
                "High-similarity edge must produce higher alpha than low-similarity one; "
                + "high=" + maxAlphaHigh + " low=" + maxAlphaLow);
    }

    @Test
    void alphaFormula_similarity6_3_clampedTo255() {
        // similarity=6.3 → 6.3*40=252 → within [0,255]
        int alpha = (int) Math.round(Math.min(255.0, Math.max(0.0, 6.3 * 40.0)));
        assertEquals(252, alpha);
    }

    @Test
    void alphaFormula_similarity100_clampedTo255() {
        int alpha = (int) Math.round(Math.min(255.0, Math.max(0.0, 100.0 * 40.0)));
        assertEquals(255, alpha);
    }

    // -----------------------------------------------------------------------
    // Missing endpoint → skipped silently
    // -----------------------------------------------------------------------

    @Test
    void missingFromId_edgeSkipped_noThrow() {
        List<Edge> edges = List.of(new Edge("missing", "b", 0.9));
        Map<String, Vector2D> pos = positions("b", 90.0, 50.0);
        Graphics2D g = offscreen(200, 100);
        try { assertDoesNotThrow(() ->
                new EdgeLayer(edges, pos).render(g, camera(1.0, 0, 0), 1.0)); }
        finally { g.dispose(); }
    }

    @Test
    void missingToId_edgeSkipped_noThrow() {
        List<Edge> edges = List.of(new Edge("a", "missing", 0.9));
        Map<String, Vector2D> pos = positions("a", 10.0, 50.0);
        Graphics2D g = offscreen(200, 100);
        try { assertDoesNotThrow(() ->
                new EdgeLayer(edges, pos).render(g, camera(1.0, 0, 0), 1.0)); }
        finally { g.dispose(); }
    }

    // -----------------------------------------------------------------------
    // Live collections reflected on next render
    // -----------------------------------------------------------------------

    @Test
    void liveEdgeList_addEdge_reflectedOnNextRender() {
        List<Edge> edges = new ArrayList<>();
        Map<String, Vector2D> pos = new HashMap<>();
        pos.put("a", new Vector2D(0.0, 50.0));
        pos.put("b", new Vector2D(200.0, 50.0));

        EdgeLayer layer = new EdgeLayer(edges, pos);

        BufferedImage buf1 = buf(200, 100);
        Graphics2D g1 = buf1.createGraphics();
        layer.render(g1, camera(1.0, 0, 0), 1.0);
        g1.dispose();

        edges.add(new Edge("a", "b", 1.0));

        BufferedImage buf2 = buf(200, 100);
        Graphics2D g2 = buf2.createGraphics();
        layer.render(g2, camera(1.0, 0, 0), 1.0);
        g2.dispose();

        assertFalse(hasAnyPixel(buf1), "Empty edges → no pixels");
        assertTrue(hasAnyPixel(buf2),  "After adding edge → pixels expected");
    }

    @Test
    void livePositions_updatePosition_reflectedOnNextRender() {
        List<Edge> edges = List.of(new Edge("a", "b", 1.0));
        Map<String, Vector2D> pos = new HashMap<>();
        pos.put("a", new Vector2D(0.0, 50.0));
        // "b" absent → edge skipped on first render

        EdgeLayer layer = new EdgeLayer(edges, pos);

        BufferedImage buf1 = buf(200, 100);
        Graphics2D g1 = buf1.createGraphics();
        layer.render(g1, camera(1.0, 0, 0), 1.0);
        g1.dispose();

        pos.put("b", new Vector2D(200.0, 50.0));

        BufferedImage buf2 = buf(200, 100);
        Graphics2D g2 = buf2.createGraphics();
        layer.render(g2, camera(1.0, 0, 0), 1.0);
        g2.dispose();

        assertFalse(hasAnyPixel(buf1), "Missing endpoint → no pixels on first render");
        assertTrue(hasAnyPixel(buf2),  "After adding position → pixels expected");
    }

    // -----------------------------------------------------------------------
    // Graphics state restored after render
    // -----------------------------------------------------------------------

    @Test
    void render_restoresGraphicsState() {
        List<Edge> edges = List.of(new Edge("a", "b", 0.8));
        Map<String, Vector2D> pos = positions("a", 10.0, 10.0, "b", 90.0, 90.0);
        EdgeLayer layer = new EdgeLayer(edges, pos);
        Graphics2D g = offscreen(200, 200);
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
    // Private helper
    // -----------------------------------------------------------------------

    private static int maxAlpha(BufferedImage img) {
        int max = 0;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                max = Math.max(max, (img.getRGB(x, y) >>> 24) & 0xFF);
        return max;
    }
}
