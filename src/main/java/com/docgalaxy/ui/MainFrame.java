package com.docgalaxy.ui;

import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.persistence.EmbeddingStore;
import com.docgalaxy.persistence.IndexStore;
import com.docgalaxy.persistence.PersistenceManager;
import com.docgalaxy.ui.canvas.GalaxyCanvas;
import com.docgalaxy.ui.components.Sidebar;
import com.docgalaxy.ui.components.StatusBar;
import com.docgalaxy.ui.components.ToolBar;
import com.docgalaxy.ui.dialogs.ProgressDialog;
import com.docgalaxy.ui.dialogs.SettingsDialog;
import com.docgalaxy.ui.dialogs.WelcomeOverlay;
import com.docgalaxy.util.AppConstants;
import com.docgalaxy.util.DemoLoader;
import com.docgalaxy.watcher.KnowledgeBaseManager;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Root application window (1400 × 900).
 *
 * Layout (BorderLayout):
 *   NORTH  → ToolBar
 *   CENTER → JSplitPane( Sidebar [240px] | GalaxyCanvas )
 *   SOUTH  → StatusBar
 */
public class MainFrame extends JFrame {

    private static final Logger LOGGER = Logger.getLogger(MainFrame.class.getName());

    // Core UI components
    private final GalaxyCanvas galaxyCanvas;
    private final ToolBar      toolBar;
    private final StatusBar    statusBar;
    private final Sidebar      sidebar;

    // Backend services – initialised when a KB is opened
    private KnowledgeBaseManager kbManager;
    private PersistenceManager   persistenceManager;
    private Path                 currentStoreDir;

    // ----------------------------------------------------------------
    // Construction
    // ----------------------------------------------------------------

    public MainFrame() {
        super("DocGalaxy");

        galaxyCanvas = new GalaxyCanvas();
        toolBar      = new ToolBar();
        statusBar    = new StatusBar();
        sidebar      = new Sidebar();

        setupLayout();
        wireSidebar();
        wireToolBar();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(AppConstants.DEFAULT_WINDOW_WIDTH, AppConstants.DEFAULT_WINDOW_HEIGHT);
        setLocationRelativeTo(null);

        SwingUtilities.invokeLater(this::showWelcomeOverlay);
    }

    // ----------------------------------------------------------------
    // Layout
    // ----------------------------------------------------------------

    private void setupLayout() {
        setLayout(new BorderLayout());
        add(toolBar, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            sidebar,
            galaxyCanvas
        );
        split.setDividerLocation(AppConstants.SIDEBAR_WIDTH);
        split.setDividerSize(1);
        split.setBorder(null);
        split.setBackground(ThemeManager.BG_PRIMARY);
        add(split, BorderLayout.CENTER);

        add(statusBar, BorderLayout.SOUTH);
    }

    // ----------------------------------------------------------------
    // Sidebar wiring
    // ----------------------------------------------------------------

    private void wireSidebar() {
        // Search → highlight matching notes on canvas
        sidebar.setOnSearch(query -> {
            if (kbManager == null) return;
            KnowledgeBase kb = kbManager.getKnowledgeBase();
            var ids = kb.getAllNotes().stream()
                .filter(n -> n.getFileName().toLowerCase()
                              .contains(query.toLowerCase()))
                .map(n -> n.getId())
                .collect(Collectors.toSet());
            galaxyCanvas.highlightNotes(ids);
            statusBar.setStatus("Found " + ids.size() + " note(s) matching \"" + query + "\"");
        });

        sidebar.setOnSearchClear(galaxyCanvas::clearHighlight);

        // Sector click → update status bar
        sidebar.setOnSectorSelected(sector ->
            statusBar.setStatus("Sector: " + sector.getLabel()));

        // Incubator note click → update status bar
        sidebar.setOnIncubatorNoteSelected(note ->
            statusBar.setStatus("Incubator: " + note.getFileName()
                + " (add more content to index)"));

        // Navigator highlight → pass to canvas
        sidebar.setOnNavigatorHighlight(galaxyCanvas::highlightNotes);
    }

    // ----------------------------------------------------------------
    // ToolBar wiring
    // ----------------------------------------------------------------

    private void wireToolBar() {
        toolBar.setOnOpenKnowledgeBase(this::openKnowledgeBase);

        toolBar.setOnRefresh(() -> {
            if (kbManager != null) {
                statusBar.setStatus("Refreshing…");
                kbManager.reconcile();
                refreshUIFromKB(kbManager.getKnowledgeBase());
            }
        });

        toolBar.setOnLayoutSwitch(layoutName ->
            statusBar.setStatus("Layout: " + layoutName));

        toolBar.setOnSettings(() -> {
            if (currentStoreDir != null) {
                SettingsDialog dlg = new SettingsDialog(this, currentStoreDir);
                dlg.setOnSaved(cfg ->
                    statusBar.setStatus("Settings saved"));
                dlg.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this,
                    "Please open a knowledge base first.",
                    "No Knowledge Base", JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }

    // ----------------------------------------------------------------
    // Knowledge base operations
    // ----------------------------------------------------------------

    /** Open (or re-open) a knowledge base folder. */
    public void openKnowledgeBase(Path rootPath) {
        currentStoreDir = rootPath.resolve(AppConstants.DOT_DIR);

        KnowledgeBase kb = new KnowledgeBase(rootPath);
        IndexStore    is = new IndexStore(currentStoreDir);
        EmbeddingStore es = new EmbeddingStore(currentStoreDir, 1536);

        persistenceManager = new PersistenceManager();
        persistenceManager.init(kb, is, es);

        kbManager = new KnowledgeBaseManager(kb, is, persistenceManager);
        kbManager.loadPersistedIndex();

        kbManager.setOnGalaxyRefresh(ignored ->
            SwingUtilities.invokeLater(() -> refreshUIFromKB(kb)));

        refreshUIFromKB(kb);
        statusBar.setStatus("Opened: " + rootPath.getFileName());
    }

    /** Load the bundled demo knowledge base from classpath resources. */
    public void openDemoKnowledgeBase() {
        ProgressDialog progress = ProgressDialog.showIndeterminate(
            this, "Loading demo knowledge base…");

        SwingWorker<Path, Void> worker = new SwingWorker<>() {
            @Override
            protected Path doInBackground() throws Exception {
                return DemoLoader.extract();
            }

            @Override
            protected void done() {
                progress.close();
                try {
                    Path demoRoot = get();
                    openKnowledgeBase(demoRoot);
                    statusBar.setStatus("Demo knowledge base loaded – " +
                        kbManager.getKnowledgeBase().getNoteCount() + " notes");
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Failed to load demo KB", ex);
                    statusBar.setStatus("Failed to load demo: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    // ----------------------------------------------------------------
    // UI refresh helpers
    // ----------------------------------------------------------------

    private void refreshUIFromKB(KnowledgeBase kb) {
        galaxyCanvas.setKnowledgeBase(kb);
        sidebar.refresh(kb);
        statusBar.setNoteCount(kb.getNoteCount());
        statusBar.setSectorCount(kb.getSectors().size());
        statusBar.setZoom(galaxyCanvas.getZoom());
    }

    // ----------------------------------------------------------------
    // Welcome overlay
    // ----------------------------------------------------------------

    private void showWelcomeOverlay() {
        WelcomeOverlay overlay = new WelcomeOverlay(this);
        overlay.setOnFolderSelected(this::openKnowledgeBase);
        overlay.setOnDemoSelected(this::openDemoKnowledgeBase);
        overlay.setVisible(true);
    }

    // ----------------------------------------------------------------
    // Accessors
    // ----------------------------------------------------------------

    public GalaxyCanvas          getGalaxyCanvas()       { return galaxyCanvas; }
    public StatusBar             getStatusBar()          { return statusBar; }
    public Sidebar               getSidebar()            { return sidebar; }
    public KnowledgeBaseManager  getKbManager()          { return kbManager; }
    public PersistenceManager    getPersistenceManager() { return persistenceManager; }
}
