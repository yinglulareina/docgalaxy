package com.docgalaxy.ui;

import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.persistence.EmbeddingStore;
import com.docgalaxy.persistence.IndexStore;
import com.docgalaxy.persistence.PersistenceManager;
import com.docgalaxy.ui.canvas.GalaxyCanvas;
import com.docgalaxy.ui.components.StatusBar;
import com.docgalaxy.ui.components.ToolBar;
import com.docgalaxy.ui.dialogs.WelcomeOverlay;
import com.docgalaxy.util.AppConstants;
import com.docgalaxy.watcher.KnowledgeBaseManager;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;

/**
 * Root application window (1400 × 900).
 *
 * Layout (BorderLayout):
 *   NORTH  → ToolBar
 *   CENTER → JSplitPane( Sidebar [240px] | GalaxyCanvas )
 *   SOUTH  → StatusBar
 *
 * Phase 2 wiring (Yongxuan Day 4-6):
 *   - Replace sidebarPlaceholder with the real Sidebar
 *   - Wire ToolBar callbacks → KnowledgeBaseManager / LayoutManager
 *   - Wire NavigatorPanel → RAGNavigator → GalaxyCanvas
 */
public class MainFrame extends JFrame {

    // Core UI components
    private final GalaxyCanvas galaxyCanvas;
    private final ToolBar      toolBar;
    private final StatusBar    statusBar;

    // Sidebar placeholder – replaced by Sidebar in Phase 2
    private final JPanel sidebarPlaceholder;

    // Backend services – initialised when a KB is opened
    private KnowledgeBaseManager kbManager;
    private PersistenceManager   persistenceManager;

    // ----------------------------------------------------------------
    // Construction
    // ----------------------------------------------------------------

    public MainFrame() {
        super("DocGalaxy");

        galaxyCanvas      = new GalaxyCanvas();
        toolBar           = new ToolBar();
        statusBar         = new StatusBar();
        sidebarPlaceholder = buildSidebarPlaceholder();

        setupLayout();
        wireToolBar();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(AppConstants.DEFAULT_WINDOW_WIDTH, AppConstants.DEFAULT_WINDOW_HEIGHT);
        setLocationRelativeTo(null);

        // Show welcome overlay after the frame is visible
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
            sidebarPlaceholder,
            galaxyCanvas
        );
        split.setDividerLocation(AppConstants.SIDEBAR_WIDTH);
        split.setDividerSize(1);
        split.setBorder(null);
        split.setBackground(ThemeManager.BG_PRIMARY);
        add(split, BorderLayout.CENTER);

        add(statusBar, BorderLayout.SOUTH);
    }

    private JPanel buildSidebarPlaceholder() {
        JPanel panel = new JPanel();
        panel.setBackground(ThemeManager.BG_SECONDARY);
        panel.setPreferredSize(new Dimension(AppConstants.SIDEBAR_WIDTH, 0));
        panel.setMinimumSize(new Dimension(AppConstants.SIDEBAR_WIDTH, 0));
        panel.setBorder(BorderFactory.createMatteBorder(
            0, 0, 0, 1, ThemeManager.BG_SURFACE));
        return panel;
    }

    // ----------------------------------------------------------------
    // ToolBar wiring
    // ----------------------------------------------------------------

    private void wireToolBar() {
        toolBar.setOnOpenKnowledgeBase(this::openKnowledgeBase);
        toolBar.setOnRefresh(() -> {
            if (kbManager != null) statusBar.setStatus("Refreshing…");
            galaxyCanvas.repaint();
        });
        toolBar.setOnLayoutSwitch(layoutName -> {
            // TODO (Phase 2): LayoutManager.switchLayout(layoutName)
            statusBar.setStatus("Layout: " + layoutName);
        });
        toolBar.setOnSettings(() -> {
            // TODO (Phase 2): open SettingsDialog
        });
    }

    // ----------------------------------------------------------------
    // Knowledge base operations
    // ----------------------------------------------------------------

    /** Open (or re-open) a knowledge base folder. */
    public void openKnowledgeBase(Path rootPath) {
        Path storeDir = rootPath.resolve(AppConstants.DOT_DIR);

        KnowledgeBase kb = new KnowledgeBase(rootPath);
        IndexStore    is = new IndexStore(storeDir);

        // Dimension from config – default to OpenAI 1536 until ConfigStore is wired
        EmbeddingStore es = new EmbeddingStore(storeDir, 1536);

        persistenceManager = new PersistenceManager();
        persistenceManager.init(kb, is, es);

        kbManager = new KnowledgeBaseManager(kb, is, persistenceManager);
        kbManager.loadPersistedIndex();

        // Refresh canvas on new embeddings
        kbManager.setOnGalaxyRefresh(ignored -> {
            galaxyCanvas.setKnowledgeBase(kb);
            statusBar.setNoteCount(kb.getNoteCount());
            statusBar.setSectorCount(kb.getSectors().size());
        });

        galaxyCanvas.setKnowledgeBase(kb);
        statusBar.setNoteCount(kb.getNoteCount());
        statusBar.setSectorCount(kb.getSectors().size());
        statusBar.setStatus("Opened: " + rootPath.getFileName());
    }

    /** Load the bundled demo knowledge base from classpath resources. */
    public void openDemoKnowledgeBase() {
        // Extract demo resources to a temp directory and open
        try {
            java.io.File tempDir = java.io.File.createTempFile("docgalaxy-demo", "");
            tempDir.delete();
            tempDir.mkdirs();
            // TODO: copy demo resources from classpath to tempDir
            statusBar.setStatus("Demo knowledge base loaded");
        } catch (Exception e) {
            statusBar.setStatus("Failed to load demo: " + e.getMessage());
        }
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
    // Accessors (used by Sidebar, NavigatorPanel, etc.)
    // ----------------------------------------------------------------

    public GalaxyCanvas          getGalaxyCanvas()       { return galaxyCanvas; }
    public StatusBar             getStatusBar()          { return statusBar; }
    public KnowledgeBaseManager  getKbManager()          { return kbManager; }
    public PersistenceManager    getPersistenceManager() { return persistenceManager; }
}
