package com.docgalaxy.ui.components;

import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.model.Note;
import com.docgalaxy.model.Sector;
import com.docgalaxy.ui.ThemeManager;
import com.docgalaxy.util.AppConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Left sidebar (fixed 240 px wide).
 *
 * Layout (top → bottom, BoxLayout.Y_AXIS):
 *   SearchPanel
 *   ─── separator ───
 *   SectorListPanel
 *   ─── separator ───
 *   IncubatorPanel
 *   ─── separator ───
 *   NavigatorPanel   (collapsible, grows to fill remaining space)
 */
public class Sidebar extends JPanel {

    private final SearchPanel     searchPanel;
    private final SectorListPanel sectorListPanel;
    private final IncubatorPanel  incubatorPanel;
    private final NavigatorPanel  navigatorPanel;

    public Sidebar() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ThemeManager.BG_SECONDARY);
        setPreferredSize(new Dimension(AppConstants.SIDEBAR_WIDTH, 0));
        setMinimumSize(new Dimension(AppConstants.SIDEBAR_WIDTH, 0));
        setMaximumSize(new Dimension(AppConstants.SIDEBAR_WIDTH, Integer.MAX_VALUE));
        setBorder(new MatteBorder(0, 0, 0, 1, ThemeManager.BG_SURFACE));

        searchPanel     = new SearchPanel();
        sectorListPanel = new SectorListPanel();
        incubatorPanel  = new IncubatorPanel();
        navigatorPanel  = new NavigatorPanel();

        // Make each child fill the full width
        makeFullWidth(searchPanel);
        makeFullWidth(sectorListPanel);
        makeFullWidth(incubatorPanel);
        makeFullWidth(navigatorPanel);

        add(searchPanel);
        add(separator());
        add(sectorListPanel);
        add(separator());
        add(incubatorPanel);
        add(separator());
        add(navigatorPanel);

        // Filler so the panels don't stretch to fill height
        add(Box.createVerticalGlue());
    }

    // ----------------------------------------------------------------
    // Wiring – delegates to child panels
    // ----------------------------------------------------------------

    public void setOnSearch(Consumer<String> callback) {
        searchPanel.setOnSearch(callback);
    }

    public void setOnSearchClear(Runnable callback) {
        searchPanel.setOnClear(callback);
    }

    public void setOnSectorSelected(Consumer<Sector> callback) {
        sectorListPanel.setOnSectorSelected(callback);
    }

    public void setOnIncubatorNoteSelected(Consumer<Note> callback) {
        incubatorPanel.setOnNoteSelected(callback);
    }

    public void setOnNavigatorHighlight(Consumer<Set<String>> callback) {
        navigatorPanel.setOnHighlight(callback);
    }

    // ----------------------------------------------------------------
    // Data refresh
    // ----------------------------------------------------------------

    /** Called whenever the knowledge base changes (open KB, reconcile, etc.). */
    public void refresh(KnowledgeBase kb) {
        sectorListPanel.refresh(kb);
        incubatorPanel.refresh(kb);
        navigatorPanel.setKnowledgeBase(kb);
    }

    // ----------------------------------------------------------------
    // Accessors
    // ----------------------------------------------------------------

    public SearchPanel     getSearchPanel()     { return searchPanel; }
    public SectorListPanel getSectorListPanel() { return sectorListPanel; }
    public IncubatorPanel  getIncubatorPanel()  { return incubatorPanel; }
    public NavigatorPanel  getNavigatorPanel()  { return navigatorPanel; }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private static void makeFullWidth(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(AppConstants.SIDEBAR_WIDTH, c.getMaximumSize().height));
    }

    private static JSeparator separator() {
        JSeparator sep = new JSeparator();
        sep.setForeground(ThemeManager.BG_SURFACE);
        sep.setBackground(ThemeManager.BG_SURFACE);
        sep.setMaximumSize(new Dimension(AppConstants.SIDEBAR_WIDTH, 1));
        return sep;
    }
}
