package com.docgalaxy.ui.canvas.layer;

import com.docgalaxy.util.AppConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BackgroundLayer}.
 *
 * <p>All tests run in headless mode; rendering is verified against the cached
 * {@link BufferedImage} directly — no real display is required.
 */
class BackgroundLayerTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    // -----------------------------------------------------------------------
    // Constructor — default
    // -----------------------------------------------------------------------

    @Test
    void defaultConstructor_imageSizedToWindowDefaults() {
        BackgroundLayer layer = new BackgroundLayer();
        assertEquals(AppConstants.DEFAULT_WINDOW_WIDTH,  layer.getImageWidth());
        assertEquals(AppConstants.DEFAULT_WINDOW_HEIGHT, layer.getImageHeight());
    }

    @Test
    void defaultConstructor_cacheIsNotNull() {
        assertNotNull(new BackgroundLayer().getCache());
    }

    // -----------------------------------------------------------------------
    // Constructor — custom size
    // -----------------------------------------------------------------------

    @Test
    void customConstructor_zeroWidth_throws() {
        assertThrows(IllegalArgumentException.class, () -> new BackgroundLayer(0, 100));
    }

    @Test
    void customConstructor_negativeWidth_throws() {
        assertThrows(IllegalArgumentException.class, () -> new BackgroundLayer(-1, 100));
    }

    @Test
    void customConstructor_zeroHeight_throws() {
        assertThrows(IllegalArgumentException.class, () -> new BackgroundLayer(100, 0));
    }

    @Test
    void customConstructor_negativeHeight_throws() {
        assertThrows(IllegalArgumentException.class, () -> new BackgroundLayer(100, -5));
    }

    @Test
    void customConstructor_dimensionsMatchRequest() {
        BackgroundLayer layer = new BackgroundLayer(400, 300);
        assertEquals(400, layer.getImageWidth());
        assertEquals(300, layer.getImageHeight());
    }

    @Test
    void customConstructor_cacheHasCorrectDimensions() {
        BackgroundLayer layer = new BackgroundLayer(320, 240);
        BufferedImage img = layer.getCache();
        assertEquals(320, img.getWidth());
        assertEquals(240, img.getHeight());
    }

    // -----------------------------------------------------------------------
    // needsRepaint
    // -----------------------------------------------------------------------

    @Test
    void needsRepaint_alwaysFalse() {
        assertFalse(new BackgroundLayer(100, 100).needsRepaint());
    }

    // -----------------------------------------------------------------------
    // Determinism — fixed seed
    // -----------------------------------------------------------------------

    @Test
    void cache_samePixelsOnRepeatedConstruction() {
        BackgroundLayer a = new BackgroundLayer(200, 150);
        BackgroundLayer b = new BackgroundLayer(200, 150);
        BufferedImage ia = a.getCache();
        BufferedImage ib = b.getCache();
        // Compare every pixel
        for (int y = 0; y < 150; y++) {
            for (int x = 0; x < 200; x++) {
                assertEquals(ia.getRGB(x, y), ib.getRGB(x, y),
                        "Pixel mismatch at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void cache_containsNonTransparentPixels() {
        BackgroundLayer layer = new BackgroundLayer(200, 200);
        BufferedImage img = layer.getCache();
        boolean foundDot = false;
        outer:
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha > 0) { foundDot = true; break outer; }
            }
        }
        assertTrue(foundDot, "Cache must contain at least one non-transparent pixel");
    }

    // -----------------------------------------------------------------------
    // DOT_COUNT constant
    // -----------------------------------------------------------------------

    @Test
    void dotCount_is300() {
        assertEquals(300, BackgroundLayer.DOT_COUNT);
    }

    @Test
    void seed_is42() {
        assertEquals(42L, BackgroundLayer.SEED);
    }

    // -----------------------------------------------------------------------
    // render() — does not throw, same-instance cache reused
    // -----------------------------------------------------------------------

    @Test
    void render_doesNotThrow() {
        BackgroundLayer layer = new BackgroundLayer(100, 100);
        java.awt.image.BufferedImage buf =
                new java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = buf.createGraphics();
        try {
            assertDoesNotThrow(() ->
                    layer.render(g, new AffineTransform(), 1.0));
        } finally {
            g.dispose();
        }
    }

    @Test
    void render_cacheNotRecreatedBetweenCalls() {
        BackgroundLayer layer = new BackgroundLayer(100, 100);
        BufferedImage before = layer.getCache();

        java.awt.image.BufferedImage buf =
                new java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = buf.createGraphics();
        try {
            layer.render(g, new AffineTransform(), 1.0);
            layer.render(g, new AffineTransform(), 2.0);
        } finally {
            g.dispose();
        }

        assertSame(before, layer.getCache(), "Cache image must be the same instance after rendering");
    }
}
