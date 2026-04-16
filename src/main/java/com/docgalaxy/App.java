package com.docgalaxy;

import com.docgalaxy.model.Edge;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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

        // ── Generate fake edges (3-5 nearest neighbours per star) ─────────────
        List<Edge> edges = buildFakeEdges(stars, 42L);

        // ── Star world-positions for BackgroundLayer gradient centroid ────────
        List<Vector2D> starWorldPositions = new ArrayList<>();
        for (Star s : stars) starWorldPositions.add(s.getPosition());

        // ── Register layers (back-to-front) ──────────────────────────────────
        canvas.addLayer(new BackgroundLayer(starWorldPositions));
        canvas.addLayer(new StarLayer(stars));
        canvas.addLayer(new EdgeLayer(edges, positions));
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

    /**
     * Generates fake edges by connecting each star to its 3-5 nearest neighbours
     * (by Euclidean distance in world space). Similarity is a random value in
     * [0.5, 0.9]. Duplicate pairs are deduplicated so each connection appears once.
     *
     * @param stars list of stars (positions must already be set)
     * @param seed  RNG seed for reproducible results
     * @return mutable list of edges
     */
    static List<Edge> buildFakeEdges(List<Star> stars, long seed) {
        Random rng = new Random(seed);
        List<Edge> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();   // "idA:idB" with idA < idB

        for (int i = 0; i < stars.size(); i++) {
            Star src = stars.get(i);
            Vector2D srcPos = src.getPosition();

            // Sort all other stars by distance to src
            int finalI = i;
            List<Star> byDist = new ArrayList<>(stars);
            byDist.remove(finalI);
            byDist.sort(Comparator.comparingDouble(
                    s -> srcPos.distanceTo(s.getPosition())));

            // Pick 3-5 nearest neighbours
            int k = 3 + rng.nextInt(3);   // [3, 5]
            for (int j = 0; j < Math.min(k, byDist.size()); j++) {
                Star dst = byDist.get(j);
                String a = src.getId().compareTo(dst.getId()) < 0
                        ? src.getId() : dst.getId();
                String b = a.equals(src.getId()) ? dst.getId() : src.getId();
                String key = a + ":" + b;
                if (seen.add(key)) {
                    double similarity = 0.5 + rng.nextDouble() * 0.4;  // [0.5, 0.9]
                    edges.add(new Edge(src.getId(), dst.getId(), similarity));
                }
            }
        }
        return edges;
    }
}
