package com.docgalaxy.ui;

import com.docgalaxy.ai.AIServiceException;
import com.docgalaxy.ai.ChatResponse;
import com.docgalaxy.ai.Neighbor;
import com.docgalaxy.ai.OpenAIChatProvider;
import com.docgalaxy.ai.OpenAIEmbeddingProvider;
import com.docgalaxy.ai.VectorDatabase;
import com.docgalaxy.ai.cluster.Cluster;
import com.docgalaxy.ai.cluster.HybridClusterStrategy;
import com.docgalaxy.ai.navigator.NavigatorService;
import com.docgalaxy.ai.navigator.RAGNavigator;
import com.docgalaxy.layout.DimensionReducer;
import com.docgalaxy.layout.ForceDirectedLayout;
import com.docgalaxy.layout.NodeData;
import com.docgalaxy.layout.RadialLayout;
import com.docgalaxy.layout.TreeLayout;
import com.docgalaxy.model.Edge;
import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.model.Note;
import com.docgalaxy.model.Sector;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.CelestialBody;
import com.docgalaxy.model.celestial.Nebula;
import com.docgalaxy.model.celestial.Star;
import com.docgalaxy.persistence.ClusterStore;
import com.docgalaxy.persistence.EmbeddingStore;
import com.docgalaxy.persistence.IndexStore;
import com.docgalaxy.persistence.NoteIndex;
import com.docgalaxy.persistence.PersistenceManager;
import com.docgalaxy.ui.canvas.GalaxyCanvas;
import com.docgalaxy.ui.canvas.layer.BackgroundLayer;
import com.docgalaxy.ui.canvas.layer.EdgeLayer;
import com.docgalaxy.ui.canvas.layer.GalaxyOverlayLayer;
import com.docgalaxy.ui.canvas.layer.LabelLayer;
import com.docgalaxy.ui.canvas.layer.NebulaLayer;
import com.docgalaxy.ui.canvas.layer.RadialRingLayer;
import com.docgalaxy.ui.canvas.layer.StarLayer;
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
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // Pipeline state – stored for layout re-computation after KB is loaded
    private List<NodeData>        lastNodeDataList;
    private List<Note>            lastNotes;
    private List<Sector>          lastSectors;
    private List<Edge>            lastEdges;
    private List<Nebula>          lastNebulae;
    private Map<String, String>   lastNoteToSector;
    private Map<String, Sector>   lastSectorById;
    private Map<String, Vector2D> lastPositions;
    private List<Cluster>          lastClusters;

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
        sidebar.setOnSearch(query -> {
            if (kbManager == null) return;
            if (query.length() < 2) return;
            String lower = query.toLowerCase();

            // Restrict to notes that actually have a Star on the canvas (#2)
            Set<String> canvasStarIds = galaxyCanvas.getBodies().stream()
                    .filter(b -> b instanceof Star)
                    .map(b -> (Star) b)
                    .map(s -> s.getNote().getId())
                    .collect(Collectors.toSet());
            if (canvasStarIds.isEmpty()) return;

            record Scored(Note note, int priority) {}

            // Phase 1: filename matches only (#3)
            List<Scored> fileNameMatches = new ArrayList<>();
            for (Note note : kbManager.getKnowledgeBase().getAllNotes()) {
                if (!canvasStarIds.contains(note.getId())) continue;
                String fileName = note.getFileName().toLowerCase();
                String baseName = fileName.replaceAll("\\.(md|txt)$", "");
                if (baseName.equals(lower)) {
                    fileNameMatches.add(new Scored(note, 1));
                } else if (fileName.contains(lower)) {
                    fileNameMatches.add(new Scored(note, 2));
                }
            }

            // Phase 2: fall back to content only when no filename matched (#3)
            List<Scored> results;
            if (!fileNameMatches.isEmpty()) {
                results = fileNameMatches;
            } else {
                results = new ArrayList<>();
                for (Note note : kbManager.getKnowledgeBase().getAllNotes()) {
                    if (!canvasStarIds.contains(note.getId())) continue;
                    if (readFirst500(note.getFilePath()).toLowerCase().contains(lower)) {
                        results.add(new Scored(note, 3));
                    }
                }
            }

            results.sort((a, b) -> {
                if (a.priority() != b.priority()) return Integer.compare(a.priority(), b.priority());
                return a.note().getFileName().compareToIgnoreCase(b.note().getFileName());
            });

            Set<String> ids = results.stream()
                    .map(s -> s.note().getId())
                    .collect(Collectors.toSet());

            if (ids.isEmpty()) {
                galaxyCanvas.clearHighlight();
                statusBar.setStatus("No results found");
                return;
            }

            galaxyCanvas.highlightNotes(ids);
            statusBar.setStatus("Found " + ids.size() + " note(s) for \"" + query + "\"");
        });

        sidebar.setOnSearchClear(galaxyCanvas::clearHighlight);

        sidebar.setOnSectorSelected(sector -> {
            galaxyCanvas.navigateToSector(sector.getId());
            statusBar.setStatus("Sector: " + sector.getLabel());
        });

        sidebar.setOnIncubatorNoteSelected(note ->
            statusBar.setStatus("Incubator: " + note.getFileName()
                + " (add more content to index)"));

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

        toolBar.setOnLayoutSwitch(layoutName -> {
            if (lastNodeDataList == null || lastNodeDataList.isEmpty()) {
                statusBar.setStatus("Layout: " + layoutName + " (no KB loaded)");
                return;
            }

            if ("Tree".equals(layoutName)) {
                // Tree layout: driven by real cluster dendrograms from HybridClusterStrategy
                if (lastClusters == null || lastClusters.isEmpty()) {
                    statusBar.setStatus("Tree layout: no cluster data — load a knowledge base first");
                    return;
                }
                int w = galaxyCanvas.getWidth()  > 0 ? galaxyCanvas.getWidth()
                                                      : AppConstants.DEFAULT_WINDOW_WIDTH;
                int h = galaxyCanvas.getHeight() > 0 ? galaxyCanvas.getHeight()
                                                      : AppConstants.DEFAULT_WINDOW_HEIGHT;
                TreeLayout treeLayout = new TreeLayout(lastClusters, w, h);
                statusBar.setStatus("Computing Tree layout…");
                new SwingWorker<Map<String, Vector2D>, Void>() {
                    @Override
                    protected Map<String, Vector2D> doInBackground() {
                        return treeLayout.calculate(lastNodeDataList);
                    }
                    @Override
                    protected void done() {
                        try {
                            applyNewLayout(get(), treeLayout.getTreeEdges());
                            statusBar.setStatus("Layout: Tree");
                        } catch (Exception ex) {
                            statusBar.setStatus("Layout switch failed: " + ex.getMessage());
                        }
                    }
                }.execute();
                return;
            }

            if ("Radial".equals(layoutName)) {
                int w = galaxyCanvas.getWidth()  > 0 ? galaxyCanvas.getWidth()
                                                      : AppConstants.DEFAULT_WINDOW_WIDTH;
                int h = galaxyCanvas.getHeight() > 0 ? galaxyCanvas.getHeight()
                                                      : AppConstants.DEFAULT_WINDOW_HEIGHT;
                RadialLayout radialLayout = new RadialLayout(w, h);
                statusBar.setStatus("Computing Radial layout…");
                new SwingWorker<Map<String, Vector2D>, Void>() {
                    @Override
                    protected Map<String, Vector2D> doInBackground() {
                        return radialLayout.calculate(lastNodeDataList);
                    }
                    @Override
                    protected void done() {
                        try {
                            applyNewLayout(get(), Collections.emptyList());
                            galaxyCanvas.addLayer(new RadialRingLayer(
                                    radialLayout.getCenterPosition(),
                                    radialLayout.getRingCount(),
                                    RadialLayout.RING_SPACING));
                            galaxyCanvas.repaint();
                            statusBar.setStatus("Layout: Radial");
                        } catch (Exception ex) {
                            statusBar.setStatus("Layout switch failed: " + ex.getMessage());
                        }
                    }
                }.execute();
                return;
            }

            // Galaxy (ForceDirected) path
            ForceDirectedLayout fdLayout = new ForceDirectedLayout();
            statusBar.setStatus("Computing Galaxy layout…");
            new SwingWorker<Map<String, Vector2D>, Void>() {
                @Override
                protected Map<String, Vector2D> doInBackground() throws Exception {
                    return fdLayout.calculate(lastNodeDataList);
                }
                @Override
                protected void done() {
                    try {
                        applyNewLayout(get(), lastEdges);
                        statusBar.setStatus("Layout: " + layoutName);
                    } catch (Exception ex) {
                        statusBar.setStatus("Layout switch failed: " + ex.getMessage());
                    }
                }
            }.execute();
        });

        // Wire zoom changes to status bar (#9)
        galaxyCanvas.setOnZoomChange(
            () -> statusBar.setZoom(galaxyCanvas.getZoom()));

        toolBar.setOnSettings(() -> {
            if (currentStoreDir != null) {
                SettingsDialog dlg = new SettingsDialog(this, currentStoreDir);
                dlg.setOnSaved(cfg -> statusBar.setStatus("Settings saved"));
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

    /**
     * Opens a knowledge base folder and runs the full AI pipeline:
     * scan → batchEmbed → PCA → clustering → LLM sector names →
     * ForceDirectedLayout → build Star/Nebula/Edge → populate canvas.
     */
    public void openKnowledgeBase(Path rootPath) {
        try {
            currentStoreDir = rootPath.resolve(AppConstants.DOT_DIR);

            KnowledgeBase kb = new KnowledgeBase(rootPath);
            IndexStore    is = new IndexStore(currentStoreDir);
            EmbeddingStore es = new EmbeddingStore(currentStoreDir,
                                                   AppConstants.DEFAULT_EMBEDDING_DIMENSION);

            persistenceManager = new PersistenceManager();
            persistenceManager.init(kb, is, es);

            kbManager = new KnowledgeBaseManager(kb, is, persistenceManager);
            kbManager.loadPersistedIndex();

            kbManager.setOnGalaxyRefresh(ignored ->
                SwingUtilities.invokeLater(() -> refreshUIFromKB(kb)));

            statusBar.setStatus("Opened: " + rootPath.getFileName() + " — building galaxy…");

            // Run the heavy pipeline off the EDT
            new PipelineWorker(rootPath, kb, currentStoreDir).execute();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to open knowledge base", e);
            JOptionPane.showMessageDialog(this,
                "Failed to open knowledge base: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
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
                    statusBar.setStatus("Demo knowledge base loaded");
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Failed to load demo KB", ex);
                    statusBar.setStatus("Failed to load demo: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    // ----------------------------------------------------------------
    // AI Pipeline SwingWorker
    // ----------------------------------------------------------------

    private final class PipelineWorker extends SwingWorker<Void, Void> {

        private final Path          folder;
        private final KnowledgeBase kb;
        private final Path          storeDir;

        PipelineWorker(Path folder, KnowledgeBase kb, Path storeDir) {
            this.folder   = folder;
            this.kb       = kb;
            this.storeDir = storeDir;
        }

        @Override
        protected Void doInBackground() {
            long pipelineStart = System.currentTimeMillis();

            // Clear stale notes from previous run / persisted index (#1)
            kb.getAllNotes().clear();

            List<Path> files = scanFiles(folder);
            if (files.isEmpty()) {
                System.out.println("No eligible .md/.txt files found.");
                return null;
            }
            System.out.println("Scanning complete: " + files.size() + " files to embed.");

            OpenAIEmbeddingProvider embedder;
            try {
                embedder = new OpenAIEmbeddingProvider();
            } catch (IllegalStateException e) {
                System.err.println("ERROR: " + e.getMessage() + " — set OPENAI_API_KEY.");
                SwingUtilities.invokeLater(() ->
                    statusBar.setStatus("Missing OPENAI_API_KEY — cannot build galaxy."));
                return null;
            }

            OpenAIChatProvider chatProvider = null;
            try { chatProvider = new OpenAIChatProvider(); }
            catch (IllegalStateException ignored) { }

            VectorDatabase db = VectorDatabase.getInstance();
            db.clear();

            // Load persisted index to reuse stable UUIDs across restarts.
            // Use RELATIVE paths as the lookup key so we match regardless of
            // whether index.json stored absolute or relative paths.
            Map<String, String> filePathToId = new HashMap<>();
            try {
                NoteIndex existingIndex = new IndexStore(storeDir).load();
                for (NoteIndex.NoteRecord r : existingIndex.getNotes()) {
                    if (r.getFilePath() == null || r.getId() == null) continue;
                    try {
                        Path p = Path.of(r.getFilePath());
                        // Normalize to relative path from KB root for consistent lookup
                        String relKey = p.isAbsolute()
                                ? folder.toAbsolutePath().normalize()
                                        .relativize(p.toAbsolutePath().normalize()).toString()
                                : p.normalize().toString();
                        filePathToId.put(relKey, r.getId());
                    } catch (Exception ignored) {
                        filePathToId.put(r.getFilePath(), r.getId()); // fallback
                    }
                }
            } catch (Exception ex) {
                System.err.println("[PIPELINE] Could not load index.json for UUID reuse: "
                        + ex.getMessage());
            }

            List<Note>   pendingNotes    = new ArrayList<>();
            List<String> pendingContents = new ArrayList<>();
            Path absRoot = folder.toAbsolutePath().normalize();
            for (Path file : files) {
                try {
                    // Use relative path as lookup key (matches what index.json stores)
                    String relPath    = absRoot.relativize(
                            file.toAbsolutePath().normalize()).toString();
                    String absPath    = file.toAbsolutePath().toString();
                    String existingId = filePathToId.get(relPath);
                    boolean reused    = existingId != null;
                    Note   note       = reused
                            ? new Note(existingId, absPath, file.getFileName().toString())
                            : new Note(absPath, file.getFileName().toString());
                    pendingNotes.add(note);
                    pendingContents.add(Files.readString(file));
                } catch (IOException e) {
                    System.err.println("  Skipping unreadable: " + file.getFileName());
                }
            }

            // --- Load cached embeddings from disk ---
            Map<String, double[]> cachedVectors = new HashMap<>();
            try {
                EmbeddingStore es = new EmbeddingStore(storeDir,
                        AppConstants.DEFAULT_EMBEDDING_DIMENSION);
                cachedVectors = es.load();
                if (!cachedVectors.isEmpty()) {
                    System.out.println("[EMBED] Loaded " + cachedVectors.size()
                            + " cached vectors from embeddings.bin");
                }
            } catch (Exception ex) {
                System.err.println("[EMBED] Could not load embeddings.bin: " + ex.getMessage());
            }

            // --- Split: cached vs. needs-embed ---
            List<Note>     notes         = new ArrayList<>();
            List<double[]> vectors       = new ArrayList<>();
            List<Note>     toEmbed       = new ArrayList<>();
            List<String>   toEmbedContents = new ArrayList<>();

            for (int i = 0; i < pendingNotes.size(); i++) {
                Note     note   = pendingNotes.get(i);
                double[] cached = cachedVectors.get(note.getId());
                if (cached != null) {
                    db.add(note.getId(), cached);
                    notes.add(note);
                    vectors.add(cached);
                } else {
                    toEmbed.add(note);
                    toEmbedContents.add(pendingContents.get(i));
                }
            }
            System.out.printf("[EMBED] %d cached, %d to embed%n",
                    notes.size(), toEmbed.size());

            // --- Batch-embed only the uncached notes ---
            final int BATCH = 20;
            long embedStart = System.currentTimeMillis();
            for (int start = 0; start < toEmbed.size(); start += BATCH) {
                int end = Math.min(start + BATCH, toEmbed.size());
                System.out.println("Embedding " + (start + 1) + "-" + end
                        + "/" + toEmbed.size() + "…");
                try {
                    List<String>   batch   = toEmbedContents.subList(start, end);
                    List<double[]> results = embedder.batchEmbed(batch);
                    for (int i = 0; i < results.size(); i++) {
                        Note note = toEmbed.get(start + i);
                        db.add(note.getId(), results.get(i));
                        notes.add(note);
                        vectors.add(results.get(i));
                    }
                } catch (AIServiceException e) {
                    System.err.println("  Batch error: " + e.getMessage());
                }
            }
            System.out.printf("Embedding: %dms (%d total, %d new)%n",
                    System.currentTimeMillis() - embedStart,
                    notes.size(), toEmbed.size());

            // --- Persist new embeddings so next run can skip API calls ---
            if (!toEmbed.isEmpty()) {
                Map<String, double[]> allVectors = new HashMap<>();
                for (int i = 0; i < notes.size(); i++) {
                    allVectors.put(notes.get(i).getId(), vectors.get(i));
                }
                persistenceManager.setCurrentVectors(allVectors);
                persistenceManager.markDirty(PersistenceManager.STORE_EMBEDDINGS);
            }

            if (notes.isEmpty()) return null;

            // PCA
            long pcaStart = System.currentTimeMillis();
            DimensionReducer reducer = new DimensionReducer();
            reducer.fit(vectors);
            List<Vector2D> points2D = reducer.transformAll(vectors);
            System.out.printf("PCA: %dms%n", System.currentTimeMillis() - pcaStart);

            Map<String, Vector2D> pcaPositions = new LinkedHashMap<>();
            for (int i = 0; i < notes.size(); i++) {
                pcaPositions.put(notes.get(i).getId(), points2D.get(i));
            }

            // Clustering (with cache)
            long clusterStart = System.currentTimeMillis();
            List<String> noteIds  = notes.stream().map(Note::getId).collect(Collectors.toList());
            ClusterStore clusterStore = new ClusterStore(storeDir);
            List<Cluster> clusters = tryLoadClusterCache(clusterStore, noteIds.size(), noteIds);
            boolean fromCache = (clusters != null);
            if (fromCache) {
                System.out.printf("Clustering: cache hit (%d sectors)%n", clusters.size());
            } else {
                HybridClusterStrategy clusterer = new HybridClusterStrategy();
                // Wrap in mutable list so we can set colors + labels before saving
                clusters = new ArrayList<>(clusterer.cluster(points2D, noteIds));
                System.out.printf("Clustering: %dms (%d sectors)%n",
                        System.currentTimeMillis() - clusterStart, clusters.size());
                // Assign sector colors (labels will be set in the sector loop below)
                for (int ci = 0; ci < clusters.size(); ci++) {
                    clusters.get(ci).setColor(ThemeManager.getSectorColor(ci));
                }
                // Don't save yet — wait until labels are assigned in the sector loop
            }

            Map<String, String> noteIdToFileName = new HashMap<>();
            for (Note note : notes) noteIdToFileName.put(note.getId(), note.getFileName());

            List<Sector>        sectors      = new ArrayList<>();
            Map<String, String> noteToSector = new HashMap<>();
            Map<String, Sector> sectorById   = new HashMap<>();
            Set<String>         usedLabels   = new HashSet<>();
            boolean             labelsAdded  = false;   // true if any label was freshly generated

            for (int i = 0; i < clusters.size(); i++) {
                Cluster cluster  = clusters.get(i);
                String  sectorId = "sector-" + i;
                // Prefer persisted color; fall back to index-based assignment
                Color   color    = cluster.getColor() != null
                        ? cluster.getColor() : ThemeManager.getSectorColor(i);

                List<String> memberFileNames = cluster.getMemberNoteIds().stream()
                        .map(id -> noteIdToFileName.getOrDefault(id, id))
                        .collect(Collectors.toList());

                // Use cached label if available (avoids re-calling LLM on cache hit)
                String label;
                if (cluster.getLabel() != null && !cluster.getLabel().isBlank()) {
                    label = cluster.getLabel();
                } else {
                    label = labelClusterLLM(memberFileNames, chatProvider, usedLabels, i);
                    cluster.setLabel(label);
                    labelsAdded = true;
                }
                usedLabels.add(label);

                Sector sector = new Sector(sectorId, label, color);
                sector.setNoteIds(new ArrayList<>(cluster.getMemberNoteIds()));
                sector.setCentroid(cluster.getCentroid());
                sector.setDendrogram(cluster.getDendrogram());
                sectors.add(sector);
                sectorById.put(sectorId, sector);
                for (String nid : cluster.getMemberNoteIds()) noteToSector.put(nid, sectorId);
            }

            // Save cluster cache whenever labels were freshly generated
            // (covers both fresh clustering AND first hit on an old cache without labels)
            if (!fromCache || labelsAdded) {
                try { clusterStore.save(clusters); }
                catch (Exception ex) {
                    System.err.println("  Cluster cache save failed: " + ex.getMessage());
                }
            }

            String fallbackSectorId = sectors.isEmpty() ? null : sectors.get(0).getId();

            // KNN + ForceDirected layout
            List<NodeData> nodeDataList = new ArrayList<>();
            for (int i = 0; i < notes.size(); i++) {
                Note   note    = notes.get(i);
                Vector2D initPos = pcaPositions.get(note.getId());
                String   sid     = noteToSector.getOrDefault(note.getId(), fallbackSectorId);
                List<Neighbor> knn = db.searchTopK(vectors.get(i), AppConstants.KNN_K + 1)
                        .stream()
                        .filter(n -> !n.getNoteId().equals(note.getId()))
                        .limit(AppConstants.KNN_K)
                        .collect(Collectors.toList());
                nodeDataList.add(new NodeData(note.getId(), initPos, sid, knn));
            }

            long layoutStart = System.currentTimeMillis();
            ForceDirectedLayout layout         = new ForceDirectedLayout();
            Map<String, Vector2D> finalPositions = layout.calculate(nodeDataList);
            System.out.printf("Layout: %dms (%d iterations)%n",
                    System.currentTimeMillis() - layoutStart,
                    layout.getLastIterationCount());

            // Build scene objects
            List<Star>        stars    = new ArrayList<>();
            Map<String, Star> starById = new HashMap<>();
            for (Note note : notes) {
                Vector2D pos    = finalPositions.getOrDefault(note.getId(), pcaPositions.get(note.getId()));
                String   sid    = noteToSector.getOrDefault(note.getId(), fallbackSectorId);
                Sector   sector = sectorById.getOrDefault(sid, sectors.get(0));
                Star     star   = new Star(note, pos, 8.0, sector);
                stars.add(star);
                starById.put(note.getId(), star);
            }

            for (Sector sector : sectors) {
                List<String> members = sector.getNoteIds();
                if (members.isEmpty()) continue;
                double cx = 0, cy = 0;
                for (String nid : members) {
                    Vector2D p = finalPositions.getOrDefault(nid, pcaPositions.get(nid));
                    if (p != null) { cx += p.getX(); cy += p.getY(); }
                }
                sector.setCentroid(new Vector2D(cx / members.size(), cy / members.size()));
            }

            List<Nebula> nebulae = new ArrayList<>();
            for (Sector sector : sectors) {
                Vector2D centroid = sector.getCentroid();
                if (centroid == null) continue;
                double maxDist = 50.0;
                for (String nid : sector.getNoteIds()) {
                    Vector2D p = finalPositions.getOrDefault(nid, pcaPositions.get(nid));
                    if (p != null) maxDist = Math.max(maxDist, centroid.distanceTo(p));
                }
                nebulae.add(new Nebula(sector, centroid, maxDist * 1.5));
            }

            List<Edge>  edges     = new ArrayList<>();
            Set<String> seenEdges = new HashSet<>();
            for (int i = 0; i < notes.size(); i++) {
                Note note = notes.get(i);
                for (Neighbor neighbor : db.searchTopK(vectors.get(i), AppConstants.KNN_K + 1)) {
                    if (neighbor.getNoteId().equals(note.getId())) continue;
                    String a   = note.getId().compareTo(neighbor.getNoteId()) < 0
                                 ? note.getId() : neighbor.getNoteId();
                    String b   = a.equals(note.getId()) ? neighbor.getNoteId() : note.getId();
                    String key = a + ":" + b;
                    if (seenEdges.add(key)) {
                        edges.add(new Edge(note.getId(), neighbor.getNoteId(),
                                           neighbor.getSimilarity()));
                    }
                }
            }

            System.out.printf("Total pipeline: %dms — %d stars, %d nebulae, %d edges%n",
                    System.currentTimeMillis() - pipelineStart,
                    stars.size(), nebulae.size(), edges.size());

            final List<Star>              fStars        = stars;
            final List<Nebula>            fNebulae      = nebulae;
            final List<Edge>              fEdges        = edges;
            final List<Sector>            fSectors      = sectors;
            final List<Note>              fNotes        = notes;
            final List<NodeData>          fNodeDataList = nodeDataList;
            final List<Cluster>           fClusters     = clusters;
            final Map<String, String>     fNoteToSector = noteToSector;
            final Map<String, Sector>     fSectorById   = sectorById;
            final Map<String, Vector2D>   fFinalPositions = new HashMap<>(finalPositions);
            final OpenAIChatProvider      fChatProvider = chatProvider;

            SwingUtilities.invokeLater(() -> {
                // Clear old layers
                new ArrayList<>(galaxyCanvas.getLayers()).forEach(galaxyCanvas::removeLayer);

                List<Vector2D> worldPositions = new ArrayList<>();
                for (Star s : fStars) worldPositions.add(s.getPosition());

                Map<String, Vector2D> posMap = new HashMap<>();
                for (Star s : fStars) posMap.put(s.getId(), s.getPosition());

                List<CelestialBody> bodies = new ArrayList<>(fStars);
                bodies.addAll(fNebulae);
                galaxyCanvas.setBodies(bodies);
                galaxyCanvas.setEdges(fEdges);

                galaxyCanvas.addLayer(new BackgroundLayer(worldPositions));
                galaxyCanvas.addLayer(new NebulaLayer(fNebulae));
                galaxyCanvas.addLayer(new StarLayer(fStars));
                galaxyCanvas.addLayer(new EdgeLayer(fEdges, posMap));
                galaxyCanvas.addLayer(new LabelLayer(fStars));
                galaxyCanvas.addLayer(new GalaxyOverlayLayer(fStars));

                galaxyCanvas.fitAll();
                galaxyCanvas.repaint();

                // Sync KB with pipeline results
                kb.setSectors(fSectors);
                for (Star s : fStars) kb.addNote(s.getNote());
                refreshUIFromKB(kb);
                statusBar.setStatus("Galaxy ready — " + fStars.size() + " stars, "
                        + fSectors.size() + " sectors");

                // Store pipeline data for layout switching (#5)
                lastNotes         = new ArrayList<>(fNotes);
                lastNodeDataList  = new ArrayList<>(fNodeDataList);
                lastNoteToSector  = new HashMap<>(fNoteToSector);
                lastSectorById    = new HashMap<>(fSectorById);
                lastPositions     = new HashMap<>(fFinalPositions);
                lastSectors       = new ArrayList<>(fSectors);
                lastEdges         = new ArrayList<>(fEdges);
                lastClusters      = new ArrayList<>(fClusters);
                lastNebulae       = fNebulae;   // same list object referenced by NebulaLayer

                // Clean up empty / singleton sectors
                cleanupEmptySectors(kb);

                // Inject NavigatorService into sidebar panel (#4)
                if (fChatProvider != null) {
                    try {
                        OpenAIEmbeddingProvider navEmbedder = new OpenAIEmbeddingProvider();
                        NavigatorService navService = new RAGNavigator(
                            navEmbedder, fChatProvider, kb, VectorDatabase.getInstance());
                        sidebar.getNavigatorPanel().setNavigatorService(navService);
                    } catch (Exception ignored) { /* no API key — navigator stays disabled */ }
                }
            });

            return null;
        }
    }

    // ----------------------------------------------------------------
    // Cluster cache helpers
    // ----------------------------------------------------------------

    /**
     * Loads the cached clusters and returns them if:
     * <ol>
     *   <li>Note count change is &lt; 20 %.</li>
     *   <li>Overlap between cached note IDs and current note IDs is &ge; 80 %.</li>
     * </ol>
     * Returns {@code null} if the cache is absent, empty, or fails either check.
     */
    private static List<Cluster> tryLoadClusterCache(ClusterStore store,
                                                      int currentNoteCount,
                                                      List<String> currentNoteIds) {
        try {
            List<Cluster> cached = store.load();
            if (cached == null || cached.isEmpty()) return null;

            List<String> cachedIds = cached.stream()
                    .flatMap(c -> c.getMemberNoteIds().stream())
                    .collect(Collectors.toList());
            int totalCached = cachedIds.size();
            if (totalCached == 0) return null;

            Set<String> currentSet = new HashSet<>(currentNoteIds);
            long overlapCount = cachedIds.stream().filter(currentSet::contains).count();

            // Count check: < 20% change
            double delta = Math.abs(currentNoteCount - totalCached) / (double) totalCached;
            if (delta >= 0.20) return null;

            // Overlap check: >= 80% of cached IDs must exist in current run
            double overlapRatio = (double) overlapCount / totalCached;
            if (overlapRatio < 0.80) return null;

            return cached;
        } catch (Exception ex) {
            System.err.println("  Cluster cache load failed: " + ex.getMessage());
            return null;
        }
    }

    // ----------------------------------------------------------------
    // Sector cleanup (empty + singleton sectors)
    // ----------------------------------------------------------------

    /**
     * Removes empty sectors (0 members) and merges singleton sectors (1 member)
     * into the nearest neighbour sector by centroid distance.
     *
     * <p>Must be called on the EDT after {@code lastNebulae} and {@code lastSectors}
     * have been assigned.
     */
    private void cleanupEmptySectors(KnowledgeBase kb) {
        if (lastSectors == null || lastNebulae == null) return;

        // Build a live star lookup from canvas bodies (needed for sector reassignment)
        Map<String, Star> starById = new HashMap<>();
        for (CelestialBody b : galaxyCanvas.getBodies()) {
            if (b instanceof Star s) starById.put(s.getNote().getId(), s);
        }

        List<Sector> toRemove = new ArrayList<>();

        // --- Pass 1: collect empty sectors ---
        for (Sector sector : new ArrayList<>(lastSectors)) {
            if (sector.getNoteIds().isEmpty()) {
                toRemove.add(sector);
            }
        }

        // --- Pass 2: collect singleton sectors and merge them ---
        for (Sector sector : new ArrayList<>(lastSectors)) {
            if (toRemove.contains(sector)) continue;
            if (sector.getNoteIds().size() != 1) continue;

            // Find the nearest other active sector by centroid distance
            String orphanId  = sector.getNoteIds().get(0);
            Vector2D orphanPos = (lastPositions != null)
                    ? lastPositions.get(orphanId) : null;
            if (orphanPos == null && sector.getCentroid() != null) {
                orphanPos = sector.getCentroid();
            }

            Sector nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (Sector other : lastSectors) {
                if (other == sector || toRemove.contains(other)) continue;
                if (other.getCentroid() == null) continue;
                double dist = (orphanPos != null)
                        ? orphanPos.distanceTo(other.getCentroid())
                        : sector.getCentroid() != null
                            ? sector.getCentroid().distanceTo(other.getCentroid())
                            : Double.MAX_VALUE;
                if (dist < nearestDist) { nearestDist = dist; nearest = other; }
            }

            if (nearest != null) {
                // Merge orphan note into the nearest sector
                nearest.getNoteIds().add(orphanId);
                if (lastNoteToSector != null) lastNoteToSector.put(orphanId, nearest.getId());
                Star orphanStar = starById.get(orphanId);
                if (orphanStar != null) orphanStar.setSector(nearest);

                toRemove.add(sector);
            }
        }

        if (toRemove.isEmpty()) return;

        // --- Remove sectors from all data structures ---
        Set<String> removeIds = toRemove.stream()
                .map(Sector::getId).collect(Collectors.toSet());

        lastSectors.removeIf(s -> removeIds.contains(s.getId()));
        lastNebulae.removeIf(n -> removeIds.contains(n.getSector().getId()));
        kb.setSectors(new ArrayList<>(lastSectors));

        // Update hit-testing body list in canvas
        List<CelestialBody> bodies = galaxyCanvas.getBodies().stream()
                .filter(b -> !(b instanceof Nebula n && removeIds.contains(n.getSector().getId())))
                .collect(Collectors.toList());
        galaxyCanvas.setBodies(bodies);

        // Persist updated cluster cache (without removed sectors)
        if (currentStoreDir != null) {
            try {
                ClusterStore cs = new ClusterStore(currentStoreDir);
                List<Cluster> remaining = cs.load();
                remaining.removeIf(c -> {
                    // A cluster is "removed" if all its members now belong to other sectors
                    Set<String> memberSet = new HashSet<>(c.getMemberNoteIds());
                    return lastSectors.stream()
                            .noneMatch(s -> s.getNoteIds().stream().anyMatch(memberSet::contains));
                });
                cs.save(remaining);
            } catch (Exception ex) {
                System.err.println("[CLEANUP] Failed to update clusters.json: " + ex.getMessage());
            }
        }

        // Refresh UI
        refreshUIFromKB(kb);
        galaxyCanvas.repaint();
    }

    // ----------------------------------------------------------------
    // Cluster label helpers
    // ----------------------------------------------------------------

    static String labelClusterLLM(List<String> memberFileNames,
                                   OpenAIChatProvider chatProvider,
                                   Set<String> usedLabels,
                                   int index) {
        if (memberFileNames.isEmpty()) return "Cluster " + (index + 1);

        String fileList     = String.join(", ", memberFileNames);
        String systemPrompt = "You are naming a topic cluster in a student's knowledge base."
                + " Give clear, specific 2-3 word topic names."
                + " Avoid generic words like 'overview', 'concepts', 'cluster', 'basics'."
                + " Good examples: 'CPU Architecture', 'Hash-Based Structures', 'OOP Design Patterns'."
                + " Bad examples: 'Programming Concepts', 'Data Structure Overview'."
                + " Output only the name.";

        if (chatProvider != null) {
            String userPrompt = "The cluster contains notes about: " + fileList + ".";
            try {
                ChatResponse resp = chatProvider.chatWithSystem(systemPrompt, userPrompt);
                if (resp.isSuccess() && resp.getContent() != null && !resp.getContent().isBlank()) {
                    String label = resp.getContent().trim();
                    if (!usedLabels.contains(label)) return label;

                    String usedList   = String.join(", ", usedLabels);
                    String retryPrompt = userPrompt
                            + " The name must be different from: " + usedList
                            + ". Focus on what makes this cluster unique.";
                    ChatResponse resp2 = chatProvider.chatWithSystem(systemPrompt, retryPrompt);
                    if (resp2.isSuccess() && resp2.getContent() != null
                            && !resp2.getContent().isBlank()) {
                        String label2 = resp2.getContent().trim();
                        if (!usedLabels.contains(label2)) return label2;
                    }
                }
            } catch (AIServiceException e) {
                System.err.println("  Sector LLM failed: " + e.getMessage() + " — fallback.");
            }
        }

        return memberFileNames.stream().limit(3).collect(Collectors.joining(", "));
    }

    // ----------------------------------------------------------------
    // File scanner
    // ----------------------------------------------------------------

    private static String readFirst500(String filePath) {
        try {
            String content = Files.readString(Path.of(filePath));
            return content.length() <= 500 ? content : content.substring(0, 500);
        } catch (Exception e) {
            return "";
        }
    }

    private static List<Path> scanFiles(Path folder) {
        try {
            return Files.walk(folder)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".md") || name.endsWith(".txt");
                    })
                    .filter(p -> {
                        try {
                            return Files.readString(p).length() >= AppConstants.MIN_CONTENT_LENGTH;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("ERROR scanning folder: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ----------------------------------------------------------------
    // Layout application
    // ----------------------------------------------------------------

    /**
     * Rebuilds all stars/nebulae/layers with the given new positions and
     * repaints the canvas.  Uses KNN edges.  Called on the EDT from the
     * layout-switch SwingWorker for Galaxy and Radial modes.
     */
    private void applyNewLayout(Map<String, Vector2D> newPositions) {
        applyNewLayout(newPositions, lastEdges);
    }

    /**
     * Core layout-application method.
     *
     * <p>{@code displayEdges} controls what {@link EdgeLayer} draws:
     * <ul>
     *   <li>Galaxy / Radial — pass {@code lastEdges} (KNN similarity edges)</li>
     *   <li>Tree — pass {@code TreeLayout.getTreeEdges()} (parent→child edges);
     *       {@code newPositions} then includes internal-node positions so the
     *       EdgeLayer can resolve all edge endpoints.</li>
     * </ul>
     */
    private void applyNewLayout(Map<String, Vector2D> newPositions, List<Edge> displayEdges) {
        if (lastNotes == null || newPositions == null || newPositions.isEmpty()) return;

        List<Star> newStars = new ArrayList<>();
        Map<String, Vector2D> notePosMap = new HashMap<>();  // note positions only (for stars/nebulae)
        for (Note note : lastNotes) {
            Vector2D pos = newPositions.getOrDefault(note.getId(),
                           lastPositions != null ? lastPositions.getOrDefault(note.getId(),
                                                   new Vector2D(0, 0)) : new Vector2D(0, 0));
            String sid     = lastNoteToSector.getOrDefault(note.getId(),
                             lastSectors.isEmpty() ? "" : lastSectors.get(0).getId());
            Sector sector  = lastSectorById.getOrDefault(sid,
                             lastSectors.isEmpty() ? null : lastSectors.get(0));
            if (sector == null) continue;
            Star star = new Star(note, pos, 8.0, sector);
            newStars.add(star);
            notePosMap.put(note.getId(), pos);
        }

        // Recompute sector centroids for nebulae
        for (Sector sector : lastSectors) {
            List<String> members = sector.getNoteIds();
            if (members.isEmpty()) continue;
            double cx = 0, cy = 0;
            int    cnt = 0;
            for (String nid : members) {
                Vector2D p = notePosMap.get(nid);
                if (p != null) { cx += p.getX(); cy += p.getY(); cnt++; }
            }
            if (cnt > 0) sector.setCentroid(new Vector2D(cx / cnt, cy / cnt));
        }

        List<Nebula> newNebulae = new ArrayList<>();
        for (Sector sector : lastSectors) {
            Vector2D centroid = sector.getCentroid();
            if (centroid == null) continue;
            double maxDist = 50.0;
            for (String nid : sector.getNoteIds()) {
                Vector2D p = notePosMap.get(nid);
                if (p != null) maxDist = Math.max(maxDist, centroid.distanceTo(p));
            }
            newNebulae.add(new Nebula(sector, centroid, maxDist * 1.5));
        }

        // newPositions includes internal tree-node positions when in Tree mode,
        // which EdgeLayer needs to resolve tree edge endpoints.
        List<Vector2D> worldPositions = new ArrayList<>(notePosMap.values());
        new ArrayList<>(galaxyCanvas.getLayers()).forEach(galaxyCanvas::removeLayer);
        List<com.docgalaxy.model.celestial.CelestialBody> bodies = new ArrayList<>(newStars);
        bodies.addAll(newNebulae);
        galaxyCanvas.setBodies(bodies);
        galaxyCanvas.setEdges(displayEdges);
        galaxyCanvas.addLayer(new BackgroundLayer(worldPositions));
        galaxyCanvas.addLayer(new NebulaLayer(newNebulae));
        galaxyCanvas.addLayer(new StarLayer(newStars));
        galaxyCanvas.addLayer(new EdgeLayer(displayEdges, new HashMap<>(newPositions)));
        galaxyCanvas.addLayer(new LabelLayer(newStars));
        galaxyCanvas.addLayer(new GalaxyOverlayLayer(newStars));
        galaxyCanvas.fitAll();
        galaxyCanvas.repaint();

        lastPositions = new HashMap<>(notePosMap);
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

    public GalaxyCanvas          getCanvas()              { return galaxyCanvas; }
    public GalaxyCanvas          getGalaxyCanvas()        { return galaxyCanvas; }
    public StatusBar             getStatusBar()           { return statusBar; }
    public Sidebar               getSidebar()             { return sidebar; }
    public KnowledgeBaseManager  getKbManager()           { return kbManager; }
    public PersistenceManager    getPersistenceManager()  { return persistenceManager; }
}
