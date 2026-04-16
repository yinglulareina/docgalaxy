package com.docgalaxy.ui.canvas;

import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.ui.ThemeManager;

import javax.swing.JPanel;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.Set;

/**
 * Main galaxy canvas panel.
 *
 * This is a compilation stub so that MainFrame compiles immediately.
 * Full implementation – Camera, CanvasInteractionHandler, and all
 * RenderLayers (Background, Edge, Nebula, Star, Label, Overlay) –
 * will be added by Ying in Phase 2.
 *
 * The public API surface (setKnowledgeBase, highlightNotes, clearHighlight,
 * getZoom) is already defined here so the rest of the codebase can compile
 * against it without modification.
 */
public class GalaxyCanvas extends JPanel {

    private KnowledgeBase knowledgeBase;
    private Set<String>   highlightedNoteIds = Collections.emptySet();

    // Camera and render layers – to be injected / constructed by Ying
    // private Camera camera;
    // private List<RenderLayer> layers;

    public GalaxyCanvas() {
        setDoubleBuffered(true);
        setBackground(ThemeManager.BG_PRIMARY);
        // CanvasInteractionHandler will be attached here by Ying
    }

    // ----------------------------------------------------------------
    // Rendering (stub – Ying replaces this with layer-based rendering)
    // ----------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            if (knowledgeBase == null || knowledgeBase.getNoteCount() == 0) {
                drawEmptyState(g2);
            }
            // TODO (Ying): apply camera transform, iterate RenderLayers
        } finally {
            g2.dispose();
        }
    }

    private void drawEmptyState(Graphics2D g) {
        g.setColor(ThemeManager.TEXT_SECONDARY);
        g.setFont(ThemeManager.FONT_BODY);
        String msg = "Open a knowledge base to explore your galaxy";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(msg,
            (getWidth()  - fm.stringWidth(msg)) / 2,
            (getHeight() + fm.getAscent())       / 2);
    }

    // ----------------------------------------------------------------
    // Public API (used by MainFrame, Sidebar, NavigatorPanel, etc.)
    // ----------------------------------------------------------------

    public void setKnowledgeBase(KnowledgeBase kb) {
        this.knowledgeBase = kb;
        repaint();
    }

    public KnowledgeBase getKnowledgeBase() { return knowledgeBase; }

    /** Highlight the given note IDs (e.g. from a search or navigation route). */
    public void highlightNotes(Set<String> noteIds) {
        this.highlightedNoteIds = noteIds != null ? noteIds : Collections.emptySet();
        repaint();
    }

    public void clearHighlight() {
        highlightedNoteIds = Collections.emptySet();
        repaint();
    }

    public Set<String> getHighlightedNoteIds() { return highlightedNoteIds; }

    /** Current zoom level – returns 1.0 until Camera is wired in. */
    public double getZoom() {
        // TODO (Ying): return camera.getZoom();
        return 1.0;
    }
}
