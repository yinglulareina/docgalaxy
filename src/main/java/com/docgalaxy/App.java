package com.docgalaxy;

import com.docgalaxy.model.Note;
import com.docgalaxy.model.Sector;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.Star;
import com.docgalaxy.ui.MainFrame;
import com.docgalaxy.ui.ThemeManager;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class App {

    public static void main(String[] args) {
        FlatDarkLaf.setup();

        // FlatLaf global rounded-corner settings
        UIManager.put("Component.arc",        8);
        UIManager.put("TextComponent.arc",    8);
        UIManager.put("Button.arc",           8);
        UIManager.put("ComboBox.arc",         8);
        UIManager.put("TabbedPane.tabArc",    8);
        UIManager.put("TabbedPane.tabSelectionArc", 8);

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    /**
     * Builds a list of deterministic fake {@link Star}s for demos and tests.
     *
     * @param count number of stars to create
     * @param seed  random seed for reproducibility
     * @return list of fake stars
     */
    public static List<Star> buildFakeStars(int count, long seed) {
        if (count <= 0) return Collections.emptyList();
        java.util.Random rng = new java.util.Random(seed);
        java.awt.Color[] palette = ThemeManager.SECTOR_PALETTE;
        String[] sectorIds = {"s1", "s2", "s3", "s4"};

        List<Star> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double x      = (rng.nextDouble() - 0.5) * 800;
            double y      = (rng.nextDouble() - 0.5) * 800;
            double radius = 5.0 + rng.nextDouble() * 7.0;   // [5, 12]
            java.awt.Color color    = palette[rng.nextInt(palette.length)];
            String         sectorId = sectorIds[rng.nextInt(sectorIds.length)];
            Sector         sector   = new Sector(sectorId, "Sector " + sectorId, color);
            String         noteId   = "note_" + i;
            Note           note     = new Note(noteId, "/fake/" + noteId + ".md", noteId + ".md");
            result.add(new Star(note, new Vector2D(x, y), radius, sector));
        }
        return result;
    }
}
