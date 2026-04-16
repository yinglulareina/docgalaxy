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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LabelLayer}.
 */
class LabelLayerTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Star star(String id, double wx, double wy, double radius) {
        Note note = new Note(id, "/notes/" + id + ".md", id + ".md");
        Sector sector = new Sector(id + "-s", "S", Color.CYAN);
        return new Star(note, new Vector2D(wx, wy), radius, sector);
    }

    private static BufferedImage buf(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    private static Graphics2D offscreen(int w, int h) {
        return buf(w, h).createGraphics();
    }

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
        assertThrows(IllegalArgumentException.class, () -> new LabelLayer(null));
    }

    @Test
    void constructor_emptyList_ok() {
        assertDoesNotThrow(() -> new LabelLayer(new ArrayList<>()));
    }

    @Test
    void getStars_returnsUnmodifiableView() {
        List<Star> list = new ArrayList<>();
        list.add(star("n1", 0, 0, 8));
        LabelLayer layer = new LabelLayer(list);
        assertThrows(UnsupportedOperationException.class,
                () -> layer.getStars().add(star("n2", 1, 1, 8)));
    }

    // -----------------------------------------------------------------------
    // needsRepaint
    // -----------------------------------------------------------------------

    @Test
    void needsRepaint_alwaysTrue() {
        assertTrue(new LabelLayer(new ArrayList<>()).needsRepaint());
    }

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    @Test
    void showThreshold_matchesAppConstants() {
        assertEquals(AppConstants.LABEL_SHOW_THRESHOLD, LabelLayer.SHOW_THRESHOLD, 1e-12);
    }

    @Test
    void showThreshold_is0_5() {
        assertEquals(0.5, LabelLayer.SHOW_THRESHOLD, 1e-12);
    }

    @Test
    void fadeRange_is0_3() {
        assertEquals(0.3, LabelLayer.FADE_RANGE, 1e-12);
    }

    // -----------------------------------------------------------------------
    // render — no-throw
    // -----------------------------------------------------------------------

    @Test
    void render_emptyList_doesNotThrow() {
        LabelLayer layer = new LabelLayer(new ArrayList<>());
        Graphics2D g = offscreen(400, 400);
        try { assertDoesNotThrow(() -> layer.render(g, camera(1.0, 0, 0), 1.0)); }
        finally { g.dispose(); }
    }

    @Test
    void render_singleStar_doesNotThrow() {
        LabelLayer layer = new LabelLayer(List.of(star("note", 100, 100, 8)));
        Graphics2D g = offscreen(400, 400);
        try { assertDoesNotThrow(() -> layer.render(g, camera(1.0, 0, 0), 1.0)); }
        finally { g.dispose(); }
    }

    @Test
    void render_multipleStars_doesNotThrow() {
        List<Star> stars = List.of(
                star("a", 50, 50, 8), star("b", 150, 100, 8), star("c", 300, 200, 6));
        LabelLayer layer = new LabelLayer(stars);
        Graphics2D g = offscreen(600, 400);
        try { assertDoesNotThrow(() -> layer.render(g, camera(1.0, 0, 0), 1.0)); }
        finally { g.dispose(); }
    }

    // -----------------------------------------------------------------------
    // Zoom gating
    // -----------------------------------------------------------------------

    @Test
    void render_zoomBelowThreshold_noPixels() {
        BufferedImage img = buf(400, 300);
        Graphics2D g = img.createGraphics();
        new LabelLayer(List.of(star("n1", 100, 100, 8)))
                .render(g, camera(0.4, 0, 0), 0.4);
        g.dispose();
        assertFalse(hasAnyPixel(img), "zoom 0.4 < 0.5 → no pixels");
    }

    @Test
    void render_zoomExactlyAtThreshold_noPixels() {
        // alpha = min(1, (0.5-0.5)/0.3) = 0 → nothing drawn
        double zoom = LabelLayer.SHOW_THRESHOLD;
        BufferedImage img = buf(400, 300);
        Graphics2D g = img.createGraphics();
        new LabelLayer(List.of(star("n1", 100, 100, 8)))
                .render(g, camera(zoom, 0, 0), zoom);
        g.dispose();
        assertFalse(hasAnyPixel(img), "alpha=0 at threshold → no pixels");
    }

    @Test
    void render_zoomAboveThreshold_drawsPixels() {
        // zoom=1.0 → alpha=min(1,(1.0-0.5)/0.3)=1
        BufferedImage img = buf(400, 300);
        Graphics2D g = img.createGraphics();
        new LabelLayer(List.of(star("hello", 100, 100, 8)))
                .render(g, camera(1.0, 0, 0), 1.0);
        g.dispose();
        assertTrue(hasAnyPixel(img), "Pixels expected when zoom > threshold");
    }

    // -----------------------------------------------------------------------
    // Alpha formula
    // -----------------------------------------------------------------------

    @Test
    void alphaFormula_atThreshold_isZero() {
        double zoom = LabelLayer.SHOW_THRESHOLD;
        float alpha = (float) Math.min(1.0, (zoom - LabelLayer.SHOW_THRESHOLD) / LabelLayer.FADE_RANGE);
        assertEquals(0f, alpha, 1e-6f);
    }

    @Test
    void alphaFormula_atThresholdPlusFadeRange_isOne() {
        double zoom = LabelLayer.SHOW_THRESHOLD + LabelLayer.FADE_RANGE;
        float alpha = (float) Math.min(1.0, (zoom - LabelLayer.SHOW_THRESHOLD) / LabelLayer.FADE_RANGE);
        assertEquals(1f, alpha, 1e-6f);
    }

    @Test
    void alphaFormula_atMidpoint_isHalf() {
        double zoom = LabelLayer.SHOW_THRESHOLD + LabelLayer.FADE_RANGE / 2.0;
        float alpha = (float) Math.min(1.0, (zoom - LabelLayer.SHOW_THRESHOLD) / LabelLayer.FADE_RANGE);
        assertEquals(0.5f, alpha, 1e-5f);
    }

    @Test
    void alphaFormula_beyondFadeRange_clampedToOne() {
        double zoom = 10.0;  // far above threshold
        float alpha = (float) Math.min(1.0, (zoom - LabelLayer.SHOW_THRESHOLD) / LabelLayer.FADE_RANGE);
        assertEquals(1f, alpha, 1e-6f);
    }

    // -----------------------------------------------------------------------
    // Higher zoom → more opaque labels
    // -----------------------------------------------------------------------

    @Test
    void higherZoom_producesHigherAlpha() {
        Star s = star("myfile", 100, 50, 8);

        BufferedImage imgLow = buf(400, 200);
        Graphics2D gLow = imgLow.createGraphics();
        new LabelLayer(List.of(s)).render(gLow, camera(0.6, 0, 0), 0.6);
        gLow.dispose();

        BufferedImage imgHigh = buf(400, 200);
        Graphics2D gHigh = imgHigh.createGraphics();
        new LabelLayer(List.of(s)).render(gHigh, camera(0.9, 0, 0), 0.9);
        gHigh.dispose();

        int alphaLow  = maxAlpha(imgLow);
        int alphaHigh = maxAlpha(imgHigh);
        assertTrue(alphaHigh > alphaLow,
                "Higher zoom must produce higher label alpha; low=" + alphaLow
                + " high=" + alphaHigh);
    }

    // -----------------------------------------------------------------------
    // Live list reflected on next render
    // -----------------------------------------------------------------------

    @Test
    void liveList_addStar_reflectedOnNextRender() {
        List<Star> list = new ArrayList<>();
        LabelLayer layer = new LabelLayer(list);

        BufferedImage buf1 = buf(400, 300);
        Graphics2D g1 = buf1.createGraphics();
        layer.render(g1, camera(1.0, 0, 0), 1.0);
        g1.dispose();

        list.add(star("test", 100, 100, 8));

        BufferedImage buf2 = buf(400, 300);
        Graphics2D g2 = buf2.createGraphics();
        layer.render(g2, camera(1.0, 0, 0), 1.0);
        g2.dispose();

        assertFalse(hasAnyPixel(buf1), "Empty list → no pixels");
        assertTrue(hasAnyPixel(buf2),  "After adding star → pixels expected");
    }

    // -----------------------------------------------------------------------
    // Graphics state restored
    // -----------------------------------------------------------------------

    @Test
    void render_restoresGraphicsState() {
        LabelLayer layer = new LabelLayer(List.of(star("n1", 100, 100, 8)));
        Graphics2D g = offscreen(400, 300);
        try {
            var colorBefore     = g.getColor();
            var compositeBefore = g.getComposite();
            var fontBefore      = g.getFont();

            layer.render(g, camera(1.0, 0, 0), 1.0);

            assertEquals(colorBefore,     g.getColor(),     "Color must be restored");
            assertEquals(compositeBefore, g.getComposite(), "Composite must be restored");
            assertEquals(fontBefore,      g.getFont(),      "Font must be restored");
        } finally {
            g.dispose();
        }
    }
}
