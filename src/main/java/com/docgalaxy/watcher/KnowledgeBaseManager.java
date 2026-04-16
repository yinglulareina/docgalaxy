package com.docgalaxy.watcher;

import com.docgalaxy.ai.AIServiceException;
import com.docgalaxy.ai.EmbeddingProvider;
import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.model.Note;
import com.docgalaxy.model.NoteStatus;
import com.docgalaxy.persistence.EmbeddingStore;
import com.docgalaxy.persistence.IndexStore;
import com.docgalaxy.persistence.PersistenceManager;
import com.docgalaxy.util.AppConstants;
import com.docgalaxy.util.HashUtil;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central orchestrator that connects file system events to the AI pipeline.
 *
 * Implements FileChangeListener so FileWatcher can notify it directly.
 *
 * Pipeline for a new / modified file:
 *   onFileCreated/Modified
 *     → content length check (< 50 chars → incubator, skip)
 *     → SHA-256 hash check (unchanged → skip)
 *     → enqueue embedding task on worker pool
 *       → EmbeddingProvider.embed()
 *       → store vector in vectorMap (shared with PersistenceManager)
 *       → markDirty("embeddings")
 *       → invoke onGalaxyRefresh callback on EDT
 *
 * Rename detection (runtime):
 *   onFileDeleted → PendingMap + 2-second timeout → ORPHANED if no match
 *   onFileCreated → hash-match against PendingMap → update path if matched
 */
public class KnowledgeBaseManager implements FileChangeListener {

    private static final Logger LOGGER = Logger.getLogger(KnowledgeBaseManager.class.getName());

    private final KnowledgeBase       knowledgeBase;
    private final IndexStore          indexStore;
    private final PersistenceManager  persistenceManager;

    // Shared vector map – also handed to PersistenceManager and VectorDatabase (Ying)
    private final Map<String, double[]> vectorMap = new ConcurrentHashMap<>();

    // Optional: set once Ying's EmbeddingProvider implementation is wired in
    private EmbeddingProvider embeddingProvider;

    // Worker pool for background embedding tasks (2 threads)
    private final ExecutorService embeddingWorker =
        Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "docgalaxy-embed-worker");
            t.setDaemon(true);
            return t;
        });

    // Rename detection: relative path → Note pending deletion
    private final Map<String, Note>     pendingDeletes   = new ConcurrentHashMap<>();
    private final ScheduledExecutorService renameScheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "docgalaxy-rename-watcher");
            t.setDaemon(true);
            return t;
        });

    // EDT callback: () → repaint canvas / recalculate layout
    private Consumer<Void> onGalaxyRefresh;

    // ----------------------------------------------------------------
    // Construction
    // ----------------------------------------------------------------

    public KnowledgeBaseManager(KnowledgeBase kb,
                                IndexStore indexStore,
                                PersistenceManager pm) {
        this.knowledgeBase      = kb;
        this.indexStore         = indexStore;
        this.persistenceManager = pm;
    }

    // ----------------------------------------------------------------
    // Configuration (called after construction)
    // ----------------------------------------------------------------

    /** Wire in the AI embedding provider once it is available. */
    public void setEmbeddingProvider(EmbeddingProvider provider) {
        this.embeddingProvider = provider;
        // Share the vector map so PersistenceManager can flush it
        persistenceManager.setCurrentVectors(vectorMap);
    }

    /** Register the callback to trigger canvas repaint on the EDT. */
    public void setOnGalaxyRefresh(Consumer<Void> callback) {
        this.onGalaxyRefresh = callback;
    }

    // ----------------------------------------------------------------
    // Startup: load persisted index
    // ----------------------------------------------------------------

    /**
     * Called once after the knowledge base folder is chosen.
     * Loads index.json into the KnowledgeBase object.
     */
    public void loadPersistedIndex() {
        indexStore.populateKnowledgeBase(knowledgeBase);
        LOGGER.info("Loaded " + knowledgeBase.getNoteCount() + " notes from index.json");
    }

    // ----------------------------------------------------------------
    // FileChangeListener implementation
    // ----------------------------------------------------------------

    @Override
    public void onFileCreated(Path path) {
        if (!isWatchedFile(path)) return;
        try {
            String content = Files.readString(path);

            // Check if this is actually a rename (DELETE + CREATE pair)
            Note renamedNote = matchPendingDelete(content);
            if (renamedNote != null) {
                handleRename(renamedNote, path);
                return;
            }

            // Content length guard – too short → incubator
            if (content.strip().length() < AppConstants.MIN_CONTENT_LENGTH) {
                LOGGER.info("File too short for indexing (incubator): " + path.getFileName());
                return;
            }

            // New note
            String relPath = relativize(path);
            Note note = new Note(relPath, path.getFileName().toString());
            note.setContentHash(HashUtil.sha256(content));
            note.setFileSize(Files.size(path));
            note.setLastModified(Files.getLastModifiedTime(path).toMillis());

            knowledgeBase.addNote(note);
            persistenceManager.markDirty(PersistenceManager.STORE_INDEX);

            enqueueEmbedding(note, content);

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error processing created file: " + path, e);
        }
    }

    @Override
    public void onFileModified(Path path) {
        if (!isWatchedFile(path)) return;
        // Note: debouncing is handled by FileWatcher before calling here
        try {
            String content  = Files.readString(path);
            String relPath  = relativize(path);
            Note   note     = knowledgeBase.getNoteByPath(relPath);

            if (note == null) {
                // Unknown file – treat as new
                onFileCreated(path);
                return;
            }

            // Hash guard – skip if content is identical
            String newHash = HashUtil.sha256(content);
            if (newHash.equals(note.getContentHash())) return;

            note.setContentHash(newHash);
            note.setFileSize(Files.size(path));
            note.setLastModified(Files.getLastModifiedTime(path).toMillis());
            persistenceManager.markDirty(PersistenceManager.STORE_INDEX);

            // Re-embed; drift threshold check (DRIFT_THRESHOLD = 0.95) happens
            // inside the worker after the new vector is computed.
            enqueueEmbedding(note, content);

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error processing modified file: " + path, e);
        }
    }

    @Override
    public void onFileDeleted(Path path) {
        if (!isWatchedFile(path)) return;
        String relPath = relativize(path);
        Note note = knowledgeBase.getNoteByPath(relPath);
        if (note == null) return;

        note.setStatus(NoteStatus.PENDING_RENAME);
        pendingDeletes.put(note.getContentHash(), note);   // key = hash for matching

        // If no matching CREATE arrives within RENAME_TIMEOUT_MS → mark ORPHANED
        renameScheduler.schedule(() -> {
            Note pending = pendingDeletes.remove(note.getContentHash());
            if (pending != null && pending.getStatus() == NoteStatus.PENDING_RENAME) {
                pending.setStatus(NoteStatus.ORPHANED);
                persistenceManager.markDirty(PersistenceManager.STORE_INDEX);
                LOGGER.info("Note marked ORPHANED: " + relPath);
            }
        }, AppConstants.RENAME_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    // ----------------------------------------------------------------
    // Rename detection helpers
    // ----------------------------------------------------------------

    /** Returns the pending-delete Note whose hash matches the new file, or null. */
    private Note matchPendingDelete(String newContent) {
        if (pendingDeletes.isEmpty()) return null;
        String hash = HashUtil.sha256(newContent);
        return pendingDeletes.remove(hash);   // removes from pending on match
    }

    private void handleRename(Note note, Path newPath) {
        String newRelPath = relativize(newPath);
        note.setFilePath(newRelPath);
        note.setFileName(newPath.getFileName().toString());
        note.setStatus(NoteStatus.ACTIVE);
        persistenceManager.markDirty(PersistenceManager.STORE_INDEX);
        LOGGER.info("Rename detected: " + note.getId() + " → " + newRelPath);
    }

    // ----------------------------------------------------------------
    // Embedding worker
    // ----------------------------------------------------------------

    private void enqueueEmbedding(Note note, String content) {
        embeddingWorker.submit(() -> {
            if (embeddingProvider == null) {
                LOGGER.fine("No embedding provider configured yet: " + note.getFileName());
                return;
            }
            try {
                double[] newVector = embeddingProvider.embed(content);

                // Drift threshold check: if old vector exists and similarity ≥ 0.95,
                // update silently without triggering a layout recalculation.
                double[] oldVector = vectorMap.get(note.getId());
                boolean driftedFar = true;
                if (oldVector != null) {
                    double similarity = cosineSimilarity(oldVector, newVector);
                    driftedFar = similarity < AppConstants.DRIFT_THRESHOLD;
                }

                vectorMap.put(note.getId(), newVector);
                persistenceManager.markDirty(PersistenceManager.STORE_EMBEDDINGS);

                if (driftedFar && onGalaxyRefresh != null) {
                    SwingUtilities.invokeLater(() -> onGalaxyRefresh.accept(null));
                }

            } catch (AIServiceException e) {
                LOGGER.log(Level.WARNING,
                    "Embedding failed for '" + note.getFileName() + "': " + e.getMessage());
            }
        });
    }

    // ----------------------------------------------------------------
    // Utilities
    // ----------------------------------------------------------------

    /** Only process .md and .txt files. */
    private boolean isWatchedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".txt");
    }

    private String relativize(Path absolute) {
        try {
            return knowledgeBase.getRootPath().relativize(absolute).toString();
        } catch (IllegalArgumentException e) {
            return absolute.toString();
        }
    }

    /** Inline cosine similarity (avoids importing VectorMath to keep the
     *  dependency graph simple – this class doesn't need to import the whole
     *  util package just for one operation). */
    private static double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na  += a[i] * a[i];
            nb  += b[i] * b[i];
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        return denom == 0 ? 0 : dot / denom;
    }

    // ----------------------------------------------------------------
    // Accessors
    // ----------------------------------------------------------------

    public KnowledgeBase getKnowledgeBase() { return knowledgeBase; }

    /** Exposes the live vector map so Ying's VectorDatabase can be seeded from it. */
    public Map<String, double[]> getVectorMap() { return vectorMap; }
}
