package com.docgalaxy;

import com.docgalaxy.ai.AIServiceException;
import com.docgalaxy.ai.ChatResponse;
import com.docgalaxy.ai.Neighbor;
import com.docgalaxy.ai.OpenAIChatProvider;
import com.docgalaxy.ai.OpenAIEmbeddingProvider;
import com.docgalaxy.ai.VectorDatabase;
import com.docgalaxy.ai.cluster.Cluster;
import com.docgalaxy.ai.cluster.HybridClusterStrategy;
import com.docgalaxy.layout.DimensionReducer;
import com.docgalaxy.layout.ForceDirectedLayout;
import com.docgalaxy.layout.NodeData;
import com.docgalaxy.model.Edge;
import com.docgalaxy.model.Note;
import com.docgalaxy.model.Sector;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.CelestialBody;
import com.docgalaxy.model.celestial.Nebula;
import com.docgalaxy.model.celestial.Star;
import com.docgalaxy.ui.MainFrame;
import com.docgalaxy.ui.ThemeManager;
import com.docgalaxy.ui.canvas.GalaxyCanvas;
import com.docgalaxy.ui.canvas.layer.BackgroundLayer;
import com.docgalaxy.ui.canvas.layer.EdgeLayer;
import com.docgalaxy.ui.canvas.layer.LabelLayer;
import com.docgalaxy.ui.canvas.layer.NebulaLayer;
import com.docgalaxy.ui.canvas.layer.StarLayer;
import com.docgalaxy.util.AppConstants;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
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
import java.util.stream.Collectors;

/**
 * Application entry point — end-to-end pipeline:
 * <ol>
 *   <li>Bootstrap FlatLaf dark theme.</li>
 *   <li>Let the user pick a folder of .md / .txt notes.</li>
 *   <li>Scan, filter, embed, reduce, cluster, layout — all in a
 *       {@link SwingWorker} so the EDT stays responsive.</li>
 *   <li>Populate the {@link GalaxyCanvas} layers and call {@code fitAll()}.</li>
 * </ol>
 */
public class App {

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(App::showFolderChooser);
    }

    // -------------------------------------------------------------------------
    // Step 1+2 — folder picker (EDT)
    // -------------------------------------------------------------------------

    private static void showFolderChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select your notes folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            System.out.println("No folder selected. Exiting.");
            System.exit(0);
        }

        Path folder = chooser.getSelectedFile().toPath();
        System.out.println("Folder: " + folder);

        MainFrame frame = new MainFrame();
        frame.setVisible(true);

        new PipelineWorker(folder, frame).execute();
    }

    // -------------------------------------------------------------------------
    // Pipeline SwingWorker — all heavy work off the EDT
    // -------------------------------------------------------------------------

    private static final class PipelineWorker extends SwingWorker<Void, Void> {

        private final Path      folder;
        private final MainFrame frame;

        PipelineWorker(Path folder, MainFrame frame) {
            this.folder = folder;
            this.frame  = frame;
        }

        @Override
        protected Void doInBackground() {
            long pipelineStart = System.currentTimeMillis();

            // ── Step 3: scan + filter ──────────────────────────────────────────
            List<Path> files = scanFiles(folder);
            if (files.isEmpty()) {
                System.out.println("No eligible .md / .txt files found (min "
                        + AppConstants.MIN_CONTENT_LENGTH + " chars). Exiting.");
                return null;
            }
            System.out.println("Scanning complete: " + files.size() + " files to embed.");

            // ── Step 4: embed ──────────────────────────────────────────────────
            OpenAIEmbeddingProvider embedder;
            try {
                embedder = new OpenAIEmbeddingProvider();
            } catch (IllegalStateException e) {
                System.err.println("ERROR: " + e.getMessage()
                        + " — set OPENAI_API_KEY and restart.");
                return null;
            }

            // Chat provider for LLM sector naming (optional — null if key absent)
            OpenAIChatProvider chatProvider = null;
            try {
                chatProvider = new OpenAIChatProvider();
            } catch (IllegalStateException ignored) { }

            VectorDatabase db = VectorDatabase.getInstance();
            db.clear();

            // ── Read all file contents first (skip unreadable) ────────────────
            List<Note>   pendingNotes    = new ArrayList<>();
            List<String> pendingContents = new ArrayList<>();
            for (Path file : files) {
                try {
                    pendingNotes.add(new Note(file.toAbsolutePath().toString(),
                                              file.getFileName().toString()));
                    pendingContents.add(Files.readString(file));
                } catch (IOException e) {
                    System.err.println("  ERROR reading " + file.getFileName()
                            + ": " + e.getMessage() + " — skipping.");
                }
            }

            // ── Batch embed in chunks of EMBED_BATCH_SIZE ─────────────────────
            final int EMBED_BATCH_SIZE = 20;
            List<Note>     notes   = new ArrayList<>();
            List<double[]> vectors = new ArrayList<>();
            int total = pendingNotes.size();

            long embedStart = System.currentTimeMillis();
            for (int start = 0; start < total; start += EMBED_BATCH_SIZE) {
                int end = Math.min(start + EMBED_BATCH_SIZE, total);
                System.out.println("Embedding " + (start + 1) + "-" + end
                        + "/" + total + "...");
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
                    System.err.println("  ERROR in batch [" + (start + 1) + "-" + end
                            + "]: " + e.getMessage() + " — skipping batch.");
                }
            }
            System.out.printf("Embedding: %dms (%d files)%n",
                    System.currentTimeMillis() - embedStart, notes.size());

            if (notes.isEmpty()) {
                System.out.println("No notes embedded successfully. Exiting.");
                return null;
            }

            // ── Step 5: PCA dimension reduction ───────────────────────────────
            long pcaStart = System.currentTimeMillis();
            System.out.println("Reducing dimensions with PCA ("
                    + vectors.get(0).length + "D → 2D)...");
            DimensionReducer reducer = new DimensionReducer();
            reducer.fit(vectors);
            List<Vector2D> points2D = reducer.transformAll(vectors);
            System.out.printf("PCA fit + transform: %dms%n",
                    System.currentTimeMillis() - pcaStart);

            Map<String, Vector2D> pcaPositions = new LinkedHashMap<>();
            for (int i = 0; i < notes.size(); i++) {
                pcaPositions.put(notes.get(i).getId(), points2D.get(i));
            }

            // ── Step 6: clustering ────────────────────────────────────────────
            long clusterStart = System.currentTimeMillis();
            System.out.println("Clustering " + notes.size() + " notes...");
            List<String>          noteIds   = notes.stream()
                                                    .map(Note::getId)
                                                    .collect(Collectors.toList());
            HybridClusterStrategy clusterer = new HybridClusterStrategy();
            List<Cluster>         clusters  = clusterer.cluster(points2D, noteIds);
            System.out.printf("Clustering: %dms (%d sectors)%n",
                    System.currentTimeMillis() - clusterStart, clusters.size());

            // Build filename lookup for LLM sector naming
            Map<String, String> noteIdToFileName = new HashMap<>();
            for (Note note : notes) {
                noteIdToFileName.put(note.getId(), note.getFileName());
            }

            // Sector objects + reverse lookup
            List<Sector>        sectors      = new ArrayList<>();
            Map<String, String> noteToSector = new HashMap<>();
            Map<String, Sector> sectorById   = new HashMap<>();

            Set<String> usedSectorLabels = new HashSet<>();

            for (int i = 0; i < clusters.size(); i++) {
                Cluster cluster  = clusters.get(i);
                String  sectorId = "sector-" + i;
                Color   color    = ThemeManager.getSectorColor(i);

                List<String> memberFileNames = cluster.getMemberNoteIds().stream()
                        .map(id -> noteIdToFileName.getOrDefault(id, id))
                        .collect(Collectors.toList());

                String label = labelClusterLLM(
                        memberFileNames, chatProvider, usedSectorLabels, i);
                usedSectorLabels.add(label);

                Sector sector = new Sector(sectorId, label, color);
                sector.setNoteIds(new ArrayList<>(cluster.getMemberNoteIds()));
                sector.setCentroid(cluster.getCentroid());
                sector.setDendrogram(cluster.getDendrogram());
                sectors.add(sector);
                sectorById.put(sectorId, sector);
                for (String nid : cluster.getMemberNoteIds()) {
                    noteToSector.put(nid, sectorId);
                }
            }

            String fallbackSectorId = sectors.isEmpty() ? null : sectors.get(0).getId();

            // ── Step 7: NodeData with KNN neighbours ──────────────────────────
            System.out.println("Building KNN graph (k=" + AppConstants.KNN_K + ")...");
            List<NodeData> nodeDataList = new ArrayList<>();
            for (int i = 0; i < notes.size(); i++) {
                Note       note    = notes.get(i);
                Vector2D   initPos = pcaPositions.get(note.getId());
                String     sid     = noteToSector.getOrDefault(note.getId(), fallbackSectorId);
                List<Neighbor> knn = db.searchTopK(vectors.get(i), AppConstants.KNN_K + 1)
                        .stream()
                        .filter(n -> !n.getNoteId().equals(note.getId()))
                        .limit(AppConstants.KNN_K)
                        .collect(Collectors.toList());
                nodeDataList.add(new NodeData(note.getId(), initPos, sid, knn));
            }

            // ── Step 8: force-directed layout ─────────────────────────────────
            long layoutStart = System.currentTimeMillis();
            System.out.println("Running force-directed layout (max "
                    + AppConstants.MAX_ITERATIONS + " iterations)...");
            ForceDirectedLayout layout        = new ForceDirectedLayout();
            Map<String, Vector2D> finalPositions = layout.calculate(nodeDataList);
            System.out.printf("Layout: %dms (converged in %d iterations)%n",
                    System.currentTimeMillis() - layoutStart,
                    layout.getLastIterationCount());

            // ── Step 9: build scene objects ───────────────────────────────────
            List<Star>        stars    = new ArrayList<>();
            Map<String, Star> starById = new HashMap<>();

            for (Note note : notes) {
                Vector2D pos    = finalPositions.getOrDefault(
                        note.getId(), pcaPositions.get(note.getId()));
                String   sid    = noteToSector.getOrDefault(note.getId(), fallbackSectorId);
                Sector   sector = sectorById.getOrDefault(sid, sectors.get(0));
                Star     star   = new Star(note, pos, 8.0, sector);
                stars.add(star);
                starById.put(note.getId(), star);
            }

            // Recompute sector centroids from final layout positions
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

            // Nebulae — radius = max member-star distance from centroid × 1.5
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

            // Edges from KNN (deduplicated)
            List<Edge>  edges     = new ArrayList<>();
            Set<String> seenEdges = new HashSet<>();
            for (int i = 0; i < notes.size(); i++) {
                Note           note = notes.get(i);
                List<Neighbor> knn  = db.searchTopK(vectors.get(i), AppConstants.KNN_K + 1);
                for (Neighbor neighbor : knn) {
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

            System.out.printf("Total pipeline: %dms%n",
                    System.currentTimeMillis() - pipelineStart);
            System.out.println("Scene built: " + stars.size() + " stars, "
                    + nebulae.size() + " nebulae, " + edges.size() + " edges.");

            // ── Steps 10-12: update canvas on EDT ─────────────────────────────
            final List<Star>   fStars   = stars;
            final List<Nebula> fNebulae = nebulae;
            final List<Edge>   fEdges   = edges;

            SwingUtilities.invokeLater(() -> {
                GalaxyCanvas canvas = frame.getCanvas();

                List<Vector2D> worldPositions = new ArrayList<>();
                for (Star s : fStars) worldPositions.add(s.getPosition());

                Map<String, Vector2D> posMap = new HashMap<>();
                for (Star s : fStars) posMap.put(s.getId(), s.getPosition());

                List<CelestialBody> bodies = new ArrayList<>(fStars);
                bodies.addAll(fNebulae);
                canvas.setBodies(bodies);
                canvas.setEdges(fEdges);

                canvas.addLayer(new BackgroundLayer(worldPositions));
                canvas.addLayer(new NebulaLayer(fNebulae));
                canvas.addLayer(new StarLayer(fStars));
                canvas.addLayer(new EdgeLayer(fEdges, posMap));
                canvas.addLayer(new LabelLayer(fStars));

                canvas.fitAll();
                canvas.repaint();
                System.out.println("Galaxy is ready!");
            });

            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Cluster label helpers — LLM-based naming with local fallback
    // -------------------------------------------------------------------------

    /**
     * Generates a short label for a cluster using LLM via
     * {@link OpenAIChatProvider#chatWithSystem}.  If the generated name is already
     * in {@code usedLabels}, a second LLM call is made asking for a different name.
     * Falls back to comma-joining the first three filenames when LLM is unavailable
     * or fails.
     *
     * @param memberFileNames filenames of notes in this cluster
     * @param chatProvider    optional ChatProvider (may be {@code null})
     * @param usedLabels      set of labels already assigned (checked for uniqueness)
     * @param index           cluster index — used only in edge-case fallback
     * @return a unique, non-blank label string
     */
    static String labelClusterLLM(List<String> memberFileNames,
                                   OpenAIChatProvider chatProvider,
                                   Set<String> usedLabels,
                                   int index) {
        if (memberFileNames.isEmpty()) return "Cluster " + (index + 1);

        String fileList = String.join(", ", memberFileNames);
        String systemPrompt = "You are naming a topic cluster in a knowledge base.";

        if (chatProvider != null) {
            String userPrompt = "The cluster contains these notes: " + fileList
                    + ". Give this cluster a short descriptive name, 2-4 words in English."
                    + " Output only the name, nothing else.";
            try {
                ChatResponse resp = chatProvider.chatWithSystem(systemPrompt, userPrompt);
                if (resp.isSuccess() && resp.getContent() != null
                        && !resp.getContent().isBlank()) {
                    String label = resp.getContent().trim();
                    if (!usedLabels.contains(label)) return label;

                    // Duplicate — retry with dedup constraint
                    String usedList = String.join(", ", usedLabels);
                    String retryPrompt = userPrompt
                            + " The name must be different from: " + usedList
                            + ". Focus on what makes this cluster unique compared to the others.";
                    ChatResponse resp2 = chatProvider.chatWithSystem(systemPrompt, retryPrompt);
                    if (resp2.isSuccess() && resp2.getContent() != null
                            && !resp2.getContent().isBlank()) {
                        String label2 = resp2.getContent().trim();
                        if (!usedLabels.contains(label2)) return label2;
                    }
                }
            } catch (AIServiceException e) {
                System.err.println("  Sector LLM failed: " + e.getMessage()
                        + " — using fallback.");
            }
        }

        // Fallback: first 3 filenames comma-joined
        return memberFileNames.stream().limit(3).collect(Collectors.joining(", "));
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // -------------------------------------------------------------------------
    // File scanner
    // -------------------------------------------------------------------------

    /**
     * Walks {@code folder} recursively and returns all {@code .md} / {@code .txt}
     * files whose content is at least {@link AppConstants#MIN_CONTENT_LENGTH}
     * characters long.  Files that cannot be read are silently skipped.
     */
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
            double x = (rng.nextDouble() - 0.5) * 800;
            double y = (rng.nextDouble() - 0.5) * 800;
            double radius = 5.0 + rng.nextDouble() * 7.0;   // [5, 12]
            java.awt.Color color = palette[rng.nextInt(palette.length)];
            String sectorId = sectorIds[rng.nextInt(sectorIds.length)];
            Sector sector = new Sector(sectorId, "Sector " + sectorId, color);
            String noteId = "note_" + i;
            Note note = new Note(noteId, "/fake/" + noteId + ".md", noteId + ".md");
            Star star = new Star(note, new Vector2D(x, y), radius, sector);
            result.add(star);
        }
        return result;
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
                            System.err.println("  Skipping unreadable file: " + p.getFileName());
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
}
