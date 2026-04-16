package com.docgalaxy.ui.canvas.layer;

import com.docgalaxy.model.Note;
import com.docgalaxy.model.Sector;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.Star;
import com.docgalaxy.util.AppConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StarLayer}.
 *
 * <p>All rendering is exercised against an off-screen {@link BufferedImage};
 * no real display is required.
 */
class StarLayerTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Star star(String id, double wx, double wy, double radius, Color color) {
        Note   note   = new Note(id, "/notes/" + id + ".md", id + ".md");
        Sector sector = new Sector(id + "-sector", "S", color);
        return new Star(note, new Vector2D(wx, wy), radius, sector);
    }

    private static Graphics2D offscreen(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).createGraphics();
    }

    /** Identity camera: zoom=1, offset=(0,0). */
    private static AffineTransform identityCamera() {
        return new AffineTransform();
    }

    /** Camera with given zoom and translation. */
    private static AffineTransform camera(double zoom, double tx, double ty) {
        AffineTransform at = new AffineTransform();
        at.translate(tx, ty);
        at.scale(zoom, zoom);
        return at;
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Test
    void constructor_null_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new StarLayer(null));
    }

    @Test
    void constructor_emptyList_ok() {
        assertDoesNotThrow(() -> new StarLayer(new ArrayList<>()));
    }

    @Test
    void getStars_returnsUnmodifiableView() {
        List<Star> list = new ArrayList<>();
        list.add(star("n1", 0, 0, 8, Color.CYAN));
        StarLayer layer = new StarLayer(list);
        assertThrows(UnsupportedOperationException.class,
                () -> layer.getStars().add(star("n2", 1, 1, 8, Color.RED)));
    }

    // -----------------------------------------------------------------------
    // needsRepaint
    // -----------------------------------------------------------------------

    @Test
    void needsRepaint_alwaysTrue() {
        assertTrue(new StarLayer(new ArrayList<>()).needsRepaint());
    }

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    @Test
    void glowRadiusFactor_is3() {
        assertEquals(3.0, StarLayer.GLOW_RADIUS_FACTOR, 1e-12);
    }

    @Test
    void glowAlpha_is40() {
        assertEquals(40, StarLayer.GLOW_ALPHA);
    }

    @Test
    void labelFadeRange_is0_2() {
        assertEquals(0.2, StarLayer.LABEL_FADE_RANGE, 1e-12);
    }

    @Test
    void lodPixelMax_is2() {
        assertEquals(2.0, StarLayer.LOD_PIXEL_MAX, 1e-12);
    }

    @Test
    void lodSimpleMax_is5() {
        assertEquals(5.0, StarLayer.LOD_SIMPLE_MAX, 1e-12);
    }

    // -----------------------------------------------------------------------
    // render — does not throw on various inputs
    // -----------------------------------------------------------------------

    @Test
    void render_emptyList_doesNotThrow() {
        StarLayer layer = new StarLayer(new ArrayList<>());
        Graphics2D g = offscreen(200, 200);
        try {
            assertDoesNotThrow(() -> layer.render(g, identityCamera(), 1.0));
        } finally {
            g.dispose();
        }
    }

    @Test
    void render_singleStarPixelLOD_doesNotThrow() {
        // zoom=0.1 → screenRadius = 8*0.1 = 0.8 < 2 → pixel LOD
        List<Star> stars = List.of(star("n1", 50, 50, 8, Color.CYAN));
        StarLayer layer = new StarLayer(stars);
        Graphics2D g = offscreen(200, 200);
        try {
            assertDoesNotThrow(() -> layer.render(g, camera(0.1, 0, 0), 0.1));
        } finally {
            g.dispose();
        }
    }

    @Test
    void render_singleStarSimpleLOD_doesNotThrow() {
        // zoom=0.4 → screenRadius = 8*0.4 = 3.2 (2..5] → simple circle
        List<Star> stars = List.of(star("n1", 50, 50, 8, Color.MAGENTA));
        StarLayer layer = new StarLayer(stars);
        Graphics2D g = offscreen(200, 200);
        try {
            assertDoesNotThrow(() -> layer.render(g, camera(0.4, 0, 0), 0.4));
        } finally {
            g.dispose();
        }
    }

    @Test
    void render_singleStarFullLOD_doesNotThrow() {
        // zoom=1 → screenRadius = 8 > 5 → full rendering
        List<Star> stars = List.of(star("n1", 50, 50, 8, Color.YELLOW));
        StarLayer layer = new StarLayer(stars);
        Graphics2D g = offscreen(400, 400);
        try {
            assertDoesNotThrow(() -> layer.render(g, camera(1.0, 0, 0), 1.0));
        } finally {
            g.dispose();
        }
    }

    @Test
    void render_fullLOD_withLabelZoom_doesNotThrow() {
        // zoom = LABEL_SHOW_THRESHOLD + 0.1 → label visible
        double zoom = AppConstants.LABEL_SHOW_THRESHOLD + 0.1;
        List<Star> stars = List.of(star("myNote", 50, 50, 8, Color.GREEN));
        StarLayer layer = new StarLayer(stars);
        Graphics2D g = offscreen(400, 400);
        try {
            assertDoesNotThrow(() -> layer.render(g, camera(zoom, 0, 0), zoom));
        } finally {
            g.dispose();
        }
    }

    @Test
    void render_multipleStars_doesNotThrow() {
        List<Star> stars = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            stars.add(star("n" + i, i * 20, i * 15, 6, Color.BLUE));
        }
        StarLayer layer = new StarLayer(stars);
        Graphics2D g = offscreen(600, 500);
        try {
            assertDoesNotThrow(() -> layer.render(g, camera(1.0, 0, 0), 1.0));
        } finally {
            g.dispose();
        }
    }

    // -----------------------------------------------------------------------
    // Viewport culling — off-screen star produces no pixels in canvas area
    // -----------------------------------------------------------------------

    @Test
    void render_culledStar_noPixelsDrawnOnCanvas() {
        // Star is at world (1000, 1000) — far off a 200×200 canvas at zoom=1
        BufferedImage buf = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buf.createGraphics();
        // Clip to the canvas bounds explicitly
        g.setClip(new Rectangle(0, 0, 200, 200));

        List<Star> stars = List.of(star("far", 1000, 1000, 8, Color.RED));
        StarLayer layer = new StarLayer(stars);
        layer.render(g, identityCamera(), 1.0);
        g.dispose();

        // Every pixel must remain transparent (nothing drawn)
        boolean anyNonTransparent = false;
        for (int y = 0; y < 200 && !anyNonTransparent; y++) {
            for (int x = 0; x < 200; x++) {
                if ((buf.getRGB(x, y) >>> 24) != 0) {
                    anyNonTransparent = true;
                    break;
                }
            }
        }
        assertFalse(anyNonTransparent, "Culled star must not produce any pixels");
    }

    @Test
    void render_visibleStar_drawsPixels() {
        // Star at world (50, 50) with radius=8, zoom=2 → screen (100,100) radius=16
        BufferedImage buf = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buf.createGraphics();

        List<Star> stars = List.of(star("visible", 50, 50, 8, new Color(0, 200, 100)));
        new StarLayer(stars).render(g, camera(2.0, 0, 0), 2.0);
        g.dispose();

        boolean anyDrawn = false;
        outer:
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 200; x++) {
                if ((buf.getRGB(x, y) >>> 24) != 0) { anyDrawn = true; break outer; }
            }
        }
        assertTrue(anyDrawn, "Visible star must produce at least one non-transparent pixel");
    }

    // -----------------------------------------------------------------------
    // Live list update reflected on next render
    // -----------------------------------------------------------------------

    @Test
    void liveList_addStar_reflectedOnNextRender() {
        List<Star> list = new ArrayList<>();
        StarLayer layer = new StarLayer(list);

        // First render — no stars
        BufferedImage buf1 = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g1 = buf1.createGraphics();
        layer.render(g1, camera(2.0, 0, 0), 2.0);
        g1.dispose();

        // Add star and render again
        list.add(star("n1", 50, 50, 8, Color.ORANGE));
        BufferedImage buf2 = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = buf2.createGraphics();
        layer.render(g2, camera(2.0, 0, 0), 2.0);
        g2.dispose();

        // buf1 must be fully transparent; buf2 must have some pixels
        boolean anyInBuf1 = false;
        boolean anyInBuf2 = false;
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 200; x++) {
                if ((buf1.getRGB(x, y) >>> 24) != 0) anyInBuf1 = true;
                if ((buf2.getRGB(x, y) >>> 24) != 0) anyInBuf2 = true;
            }
        }
        assertFalse(anyInBuf1, "Empty list must produce no pixels");
        assertTrue(anyInBuf2,  "Non-empty list must produce pixels");
    }

    // -----------------------------------------------------------------------
    // No-clip rendering (clip == null) must not throw
    // -----------------------------------------------------------------------

    @Test
    void render_noClip_doesNotThrow() {
        List<Star> stars = List.of(star("n1", 50, 50, 8, Color.PINK));
        StarLayer layer = new StarLayer(stars);
        // createGraphics() from BufferedImage usually has clip = image bounds,
        // but clearing it simulates null clip
        Graphics2D g = offscreen(200, 200);
        g.setClip(null);
        try {
            assertDoesNotThrow(() -> layer.render(g, identityCamera(), 1.0));
        } finally {
            g.dispose();
        }
    }
}
