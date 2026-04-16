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
import com.docgalaxy.model.Edge;
import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.model.Note;
import com.docgalaxy.model.Sector;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.CelestialBody;
import com.docgalaxy.model.celestial.Nebula;
import com.docgalaxy.model.celestial.Star;
import com.docgalaxy.persistence.EmbeddingStore;
import com.docgalaxy.persistence.IndexStore;
import com.docgalaxy.persistence.PersistenceManager;
import com.docgalaxy.ui.canvas.GalaxyCanvas;
import com.docgalaxy.ui.canvas.layer.BackgroundLayer;
import com.docgalaxy.ui.canvas.layer.EdgeLayer;
import com.docgalaxy.ui.canvas.layer.GalaxyOverlayLayer;
import com.docgalaxy.ui.canvas.layer.LabelLayer;
import com.docgalaxy.ui.canvas.layer.NebulaLayer;
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
    private Map<String, String>   lastNoteToSector;
    private Map<String, Sector>   lastSectorById;
    private Map<String, Vector2D> lastPositions;

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
            // Choose strategy by name (Tree falls back to Radial)
            var strategy = switch (layoutName) {
                case "Radial" -> new RadialLayout();
                default       -> new ForceDirectedLayout();
            };
            statusBar.setStatus("Computing " + layoutName + " layout…");
            new SwingWorker<Map<String, Vector2D>, Void>() {
                @Override
                protected Map<String, Vector2D> doInBackground() throws Exception {
                    return strategy.calculate(lastNodeDataList);
                }
                @Override
                protected void done() {
                    try {
                        applyNewLayout(get());
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
            new PipelineWorker(rootPath, kb).execute();
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

        PipelineWorker(Path folder, KnowledgeBase kb) {
            this.folder = folder;
            this.kb     = kb;
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

            List<Note>   pendingNotes    = new ArrayList<>();
            List<String> pendingContents = new ArrayList<>();
            for (Path file : files) {
                try {
                    pendingNotes.add(new Note(file.toAbsolutePath().toString(),
                                              file.getFileName().toString()));
                    pendingContents.add(Files.readString(file));
                } catch (IOException e) {
                    System.err.println("  Skipping unreadable: " + file.getFileName());
                }
            }

            final int BATCH = 20;
            List<Note>     notes   = new ArrayList<>();
            List<double[]> vectors = new ArrayList<>();
            int total = pendingNotes.size();

            long embedStart = System.currentTimeMillis();
            for (int start = 0; start < total; start += BATCH) {
                int end = Math.min(start + BATCH, total);
                System.out.println("Embedding " + (start + 1) + "-" + end + "/" + total + "…");
                try {
                    List<String>   batch   = pendingContents.subList(start, end);
                    List<double[]> results = embedder.batchEmbed(batch);
                    for (int i = 0; i < results.size(); i++) {
                        Note note = pendingNotes.get(start + i);
                        db.add(note.getId(), results.get(i));
                        notes.add(note);
                        vectors.add(results.get(i));
                    }
                } catch (AIServiceException e) {
                    System.err.println("  Batch error: " + e.getMessage());
                }
            }
            System.out.printf("Embedding: %dms (%d files)%n",
                    System.currentTimeMillis() - embedStart, notes.size());

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

            // Clustering
            long clusterStart = System.currentTimeMillis();
            List<String>          noteIds   = notes.stream().map(Note::getId).collect(Collectors.toList());
            HybridClusterStrategy clusterer = new HybridClusterStrategy();
            List<Cluster>         clusters  = clusterer.cluster(points2D, noteIds);
            System.out.printf("Clustering: %dms (%d sectors)%n",
                    System.currentTimeMillis() - clusterStart, clusters.size());

            Map<String, String> noteIdToFileName = new HashMap<>();
            for (Note note : notes) noteIdToFileName.put(note.getId(), note.getFileName());

            List<Sector>        sectors      = new ArrayList<>();
            Map<String, String> noteToSector = new HashMap<>();
            Map<String, Sector> sectorById   = new HashMap<>();
            Set<String>         usedLabels   = new HashSet<>();

            for (int i = 0; i < clusters.size(); i++) {
                Cluster cluster  = clusters.get(i);
                String  sectorId = "sector-" + i;
                Color   color    = ThemeManager.getSectorColor(i);

                List<String> memberFileNames = cluster.getMemberNoteIds().stream()
                        .map(id -> noteIdToFileName.getOrDefault(id, id))
                        .collect(Collectors.toList());

                String label = labelClusterLLM(memberFileNames, chatProvider, usedLabels, i);
                usedLabels.add(label);

                Sector sector = new Sector(sectorId, label, color);
                sector.setNoteIds(new ArrayList<>(cluster.getMemberNoteIds()));
                sector.setCentroid(cluster.getCentroid());
                sector.setDendrogram(cluster.getDendrogram());
                sectors.add(sector);
                sectorById.put(sectorId, sector);
                for (String nid : cluster.getMemberNoteIds()) noteToSector.put(nid, sectorId);
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
    // Cluster label helpers
    // ----------------------------------------------------------------

    static String labelClusterLLM(List<String> memberFileNames,
                                   OpenAIChatProvider chatProvider,
                                   Set<String> usedLabels,
                                   int index) {
        if (memberFileNames.isEmpty()) return "Cluster " + (index + 1);

        String fileList     = String.join(", ", memberFileNames);
        String systemPrompt = "You are naming a topic cluster in a knowledge base.";

        if (chatProvider != null) {
            String userPrompt = "The cluster contains these notes: " + fileList
                    + ". Give this cluster a short descriptive name, 2-4 words in English."
                    + " Output only the name, nothing else.";
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
     * repaints the canvas.  Called on the EDT from the layout-switch SwingWorker.
     */
    private void applyNewLayout(Map<String, Vector2D> newPositions) {
        if (lastNotes == null || newPositions == null || newPositions.isEmpty()) return;

        List<Star> newStars = new ArrayList<>();
        Map<String, Vector2D> posMap = new HashMap<>();
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
            posMap.put(note.getId(), pos);
        }

        // Recompute sector centroids for nebulae
        for (Sector sector : lastSectors) {
            List<String> members = sector.getNoteIds();
            if (members.isEmpty()) continue;
            double cx = 0, cy = 0;
            int    cnt = 0;
            for (String nid : members) {
                Vector2D p = newPositions.get(nid);
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
                Vector2D p = newPositions.get(nid);
                if (p != null) maxDist = Math.max(maxDist, centroid.distanceTo(p));
            }
            newNebulae.add(new Nebula(sector, centroid, maxDist * 1.5));
        }

        List<Vector2D> worldPositions = new ArrayList<>(posMap.values());
        new ArrayList<>(galaxyCanvas.getLayers()).forEach(galaxyCanvas::removeLayer);
        List<com.docgalaxy.model.celestial.CelestialBody> bodies = new ArrayList<>(newStars);
        bodies.addAll(newNebulae);
        galaxyCanvas.setBodies(bodies);
        galaxyCanvas.setEdges(lastEdges);
        galaxyCanvas.addLayer(new BackgroundLayer(worldPositions));
        galaxyCanvas.addLayer(new NebulaLayer(newNebulae));
        galaxyCanvas.addLayer(new StarLayer(newStars));
        galaxyCanvas.addLayer(new EdgeLayer(lastEdges, posMap));
        galaxyCanvas.addLayer(new LabelLayer(newStars));
        galaxyCanvas.addLayer(new GalaxyOverlayLayer(newStars));
        galaxyCanvas.fitAll();
        galaxyCanvas.repaint();

        lastPositions = new HashMap<>(newPositions);
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
