package com.docgalaxy;

import com.docgalaxy.model.celestial.Star;
import com.docgalaxy.ui.ThemeManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link App#buildFakeStars}.
 */
class AppTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    // -----------------------------------------------------------------------
    // buildFakeStars
    // -----------------------------------------------------------------------

    @Test
    void buildFakeStars_returnsRequestedCount() {
        assertEquals(20, App.buildFakeStars(20, 42L).size());
    }

    @Test
    void buildFakeStars_zeroCount_returnsEmptyList() {
        assertTrue(App.buildFakeStars(0, 1L).isEmpty());
    }

    @Test
    void buildFakeStars_allHaveUniqueIds() {
        List<Star> stars = App.buildFakeStars(20, 42L);
        long distinct = stars.stream().map(Star::getId).distinct().count();
        assertEquals(20, distinct, "Every star must have a unique ID");
    }

    @Test
    void buildFakeStars_allHavePositions() {
        App.buildFakeStars(20, 42L)
           .forEach(s -> assertNotNull(s.getPosition(), "Position must not be null"));
    }

    @Test
    void buildFakeStars_allHaveValidRadius() {
        App.buildFakeStars(20, 42L)
           .forEach(s -> assertTrue(s.getRadius() >= 5.0 && s.getRadius() <= 12.0,
                   "Radius must be in [5, 12], got " + s.getRadius()));
    }

    @Test
    void buildFakeStars_allHaveColorsFromPalette() {
        var palette = java.util.Set.of(ThemeManager.SECTOR_PALETTE);
        App.buildFakeStars(20, 42L)
           .forEach(s -> assertTrue(palette.contains(s.getColor()),
                   "Star color must come from SECTOR_PALETTE"));
    }

    @Test
    void buildFakeStars_deterministic_sameSeedSamePositions() {
        List<Star> a = App.buildFakeStars(20, 42L);
        List<Star> b = App.buildFakeStars(20, 42L);
        for (int i = 0; i < 20; i++) {
            assertEquals(a.get(i).getPosition().getX(),
                         b.get(i).getPosition().getX(), 1e-12,
                         "X position must be deterministic at index " + i);
            assertEquals(a.get(i).getPosition().getY(),
                         b.get(i).getPosition().getY(), 1e-12,
                         "Y position must be deterministic at index " + i);
        }
    }

    @Test
    void buildFakeStars_differentSeed_differentPositions() {
        List<Star> a = App.buildFakeStars(20, 42L);
        List<Star> b = App.buildFakeStars(20, 99L);
        boolean anyDiff = false;
        for (int i = 0; i < 20; i++) {
            if (a.get(i).getPosition().getX() != b.get(i).getPosition().getX()) {
                anyDiff = true;
                break;
            }
        }
        assertTrue(anyDiff, "Different seeds must produce different positions");
    }

    @Test
    void buildFakeStars_allHaveNoteWithFilePath() {
        App.buildFakeStars(20, 42L)
           .forEach(s -> {
               assertNotNull(s.getNote().getFilePath());
               assertFalse(s.getNote().getFilePath().isBlank());
           });
    }
}
