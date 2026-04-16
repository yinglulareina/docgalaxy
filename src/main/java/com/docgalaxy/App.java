package com.docgalaxy;

import com.docgalaxy.model.Note;
import com.docgalaxy.model.Sector;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.CelestialBody;
import com.docgalaxy.model.celestial.Star;
import com.docgalaxy.ui.MainFrame;
import com.docgalaxy.ui.ThemeManager;
import com.docgalaxy.ui.canvas.GalaxyCanvas;
import com.docgalaxy.ui.canvas.layer.BackgroundLayer;
import com.docgalaxy.ui.canvas.layer.EdgeLayer;
import com.docgalaxy.ui.canvas.layer.LabelLayer;
import com.docgalaxy.ui.canvas.layer.StarLayer;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Application entry point.
 *
 * <p>Bootstraps FlatLaf, creates the {@link MainFrame}, populates the canvas
 * with 20 demo stars at random positions, and calls {@code fitAll()} so the
 * galaxy is centred on first launch.
 *
 * <p><em>Temporary visual test</em> — real data loading via the persistence
 * and AI layers will replace the fake stars once the full pipeline is wired.
 */
public class App {

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(App::launchUI);
    }

    // -------------------------------------------------------------------------
    // Private — UI bootstrap (runs on EDT)
    // -------------------------------------------------------------------------

    private static void launchUI() {
        MainFrame frame = new MainFrame();
        GalaxyCanvas canvas = frame.getCanvas();

        // ── Build 20 fake stars ───────────────────────────────────────────────
        List<Star> stars = buildFakeStars(20, 42L);

        // ── Flat body list for hit-testing ────────────────────────────────────
        List<CelestialBody> bodies = new ArrayList<>(stars);
        canvas.setBodies(bodies);

        // ── Position map for EdgeLayer ────────────────────────────────────────
        Map<String, Vector2D> positions = new HashMap<>();
        for (Star s : stars) positions.put(s.getId(), s.getPosition());

        // ── Register layers (back-to-front) ──────────────────────────────────
        canvas.addLayer(new BackgroundLayer());
        canvas.addLayer(new StarLayer(stars));
        canvas.addLayer(new EdgeLayer(Collections.emptyList(), positions));
        canvas.addLayer(new LabelLayer(stars));

        // ── Fit all stars into the initial viewport ───────────────────────────
        canvas.fitAll();

        frame.setVisible(true);
    }

    /**
     * Generates {@code count} stars scattered in a ±500 world-unit square,
     * drawn from the {@link ThemeManager#SECTOR_PALETTE} for colour variety.
     *
     * @param count number of stars to create
     * @param seed  RNG seed for reproducible placement
     * @return mutable list of fake stars
     */
    static List<Star> buildFakeStars(int count, long seed) {
        Random rng = new Random(seed);
        Color[] palette = ThemeManager.SECTOR_PALETTE;
        List<Star> stars = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String id     = "demo-" + i;
            double x      = (rng.nextDouble() * 1000.0) - 500.0;
            double y      = (rng.nextDouble() * 1000.0) - 500.0;
            double radius = 5.0 + rng.nextDouble() * 7.0;   // 5–12 px
            Color  color  = palette[rng.nextInt(palette.length)];

            Note   note   = new Note(id, "/demo/" + id + ".md", id + ".md");
            Sector sector = new Sector("sector-" + (i % palette.length),
                                       "Demo " + (i % palette.length), color);
            stars.add(new Star(note, new Vector2D(x, y), radius, sector));
        }
        return stars;
    }
}
