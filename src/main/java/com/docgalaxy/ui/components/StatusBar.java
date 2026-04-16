package com.docgalaxy.ui.components;

import com.docgalaxy.ui.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * Thin status bar at the bottom of MainFrame (SOUTH region).
 *
 * Left  : note count | sector count
 * Centre: transient status message (e.g. "Embedding 23 notes…")
 * Right : zoom percentage
 */
public class StatusBar extends JPanel {

    private final JLabel noteCountLabel;
    private final JLabel sectorCountLabel;
    private final JLabel statusLabel;
    private final JLabel zoomLabel;

    public StatusBar() {
        setLayout(new BorderLayout());
        setBackground(ThemeManager.BG_SECONDARY);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x2A2A4A)));
        setPreferredSize(new Dimension(0, 24));

        // Left: counters
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
        left.setOpaque(false);
        noteCountLabel   = smallLabel("0 notes");
        sectorCountLabel = smallLabel("0 sectors");
        left.add(noteCountLabel);
        left.add(verticalSep());
        left.add(sectorCountLabel);
        add(left, BorderLayout.WEST);

        // Centre: status message
        statusLabel = smallLabel("Ready");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(statusLabel, BorderLayout.CENTER);

        // Right: zoom
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 3));
        right.setOpaque(false);
        zoomLabel = smallLabel("100%");
        right.add(zoomLabel);
        add(right, BorderLayout.EAST);
    }

    // ----------------------------------------------------------------
    // Setters called by MainFrame / Camera
    // ----------------------------------------------------------------

    public void setNoteCount(int count) {
        noteCountLabel.setText(count + " note" + (count == 1 ? "" : "s"));
    }

    public void setSectorCount(int count) {
        sectorCountLabel.setText(count + " sector" + (count == 1 ? "" : "s"));
    }

    public void setZoom(double zoom) {
        zoomLabel.setText(String.format("%.0f%%", zoom * 100));
    }

    /** Show a transient message (e.g. progress, errors). */
    public void setStatus(String message) {
        statusLabel.setText(message != null ? message : "Ready");
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private JLabel smallLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(ThemeManager.TEXT_SECONDARY);
        lbl.setFont(ThemeManager.FONT_SMALL);
        return lbl;
    }

    private JSeparator verticalSep() {
        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setForeground(new Color(0x3A3A5A));
        sep.setPreferredSize(new Dimension(1, 14));
        return sep;
    }
}
