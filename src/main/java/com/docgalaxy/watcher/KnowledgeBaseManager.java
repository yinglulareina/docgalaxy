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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

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

    // Shared vector map – also handed to PersistenceManager and VectorDatabase
    private final Map<String, double[]> vectorMap = new ConcurrentHashMap<>();

    // Optional: set once EmbeddingProvider is wired in
    private EmbeddingProvider embeddingProvider;

    // Expected embedding dimension from config; -1 = not set (Case 12)
    private int expectedDimension = -1;

    // Worker pool for background embedding tasks (2 threads)
    private final ExecutorService embeddingWorker =
        Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "docgalaxy-embed-worker");
            t.setDaemon(true);
            return t;
        });

    // Rename detection: content hash → Note pending deletion
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
        persistenceManager.setCurrentVectors(vectorMap);
    }

    /** Register the callback to trigger canvas repaint on the EDT. */
    public void setOnGalaxyRefresh(Consumer<Void> callback) {
        this.onGalaxyRefresh = callback;
    }

    /**
     * Set the configured embedding dimension so reconcile() can detect mismatches.
     * Call before reconcile(). (Case 12)
     */
    public void setExpectedDimension(int dim) {
        this.expectedDimension = dim;
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
        // Case 9: skip symlinks
        if (Files.isSymbolicLink(path)) return;
        try {
            String content = Files.readString(path);

            // Check if this is actually a rename (DELETE + CREATE pair)
            Note renamedNote = matchPendingDelete(content);
            if (renamedNote != null) {
                handleRename(renamedNote, path);
                return;
            }

            // New note
            String relPath = relativize(path);
            Note note = new Note(relPath, path.getFileName().toString());
            note.setContentHash(HashUtil.sha256(content));
            note.setFileSize(Files.size(path));
            note.setLastModified(Files.getLastModifiedTime(path).toMillis());

            // Case 7: too short → incubator
            if (content.strip().length() < AppConstants.MIN_CONTENT_LENGTH) {
                note.setStatus(NoteStatus.INCUBATOR);
                LOGGER.info("File too short for indexing (incubator): " + path.getFileName());
                knowledgeBase.addNote(note);
                persistenceManager.markDirty(PersistenceManager.STORE_INDEX);
                return;
            }

            knowledgeBase.addNote(note);
            persistenceManager.markDirty(PersistenceManager.STORE_INDEX);
            enqueueEmbedding(note, content);

        } catch (IOException e) {
            // Case 10: log and skip
            LOGGER.log(Level.WARNING, "Error processing created file: " + path, e);
        }
    }

    @Override
    public void onFileModified(Path path) {
        if (!isWatchedFile(path)) return;
        if (Files.isSymbolicLink(path)) return;   // Case 9
        try {
            String content  = Files.readString(path);
            String relPath  = relativize(path);
            Note   note     = knowledgeBase.getNoteByPath(relPath);

            if (note == null) {
                onFileCreated(path);
                return;
            }

            String newHash = HashUtil.sha256(content);
            if (newHash.equals(note.getContentHash())) return;

            note.setContentHash(newHash);
            note.setFileSize(Files.size(path));
            note.setLastModified(Files.getLastModifiedTime(path).toMillis());
            persistenceManager.markDirty(PersistenceManager.STORE_INDEX);
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
        pendingDeletes.put(note.getContentHash(), note);

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

    private Note matchPendingDelete(String newContent) {
        if (pendingDeletes.isEmpty()) return null;
        String hash = HashUtil.sha256(newContent);
        return pendingDeletes.remove(hash);
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
    // Startup reconciliation — all 13 edge cases
    // ----------------------------------------------------------------

    /**
     * Reconciles the persisted index against the actual files on disk.
     *
     * <pre>
     * Case  1  path+hash same, vector present   → reuse UUID+embedding, skip API
     * Case  2  path+hash different               → reuse UUID, re-embed
     * Case  3  hash matches exactly 1 orphan     → rename, reuse UUID
     * Case  4  hash matches multiple orphans     → treat as new (ambiguous)
     * Case  5  filekey match (rename+modified)   → reuse UUID, re-embed
     * Case  6  in index, not on disk             → remove record + vector
     * Case  7  file < 50 chars                   → incubator, no embed
     * Case  8  only .md/.txt/.markdown files
     * Case  9  skip symlinks
     * Case 10  read failure                       → log warning, skip
     * Case 11  path+hash same but vector missing → re-embed
     * Case 12  dimension mismatch                → clear vector cache, full re-embed
     * Case 13  recursive scan, relative paths
     * </pre>
     */
    public void reconcile() {
        Path rootPath = knowledgeBase.getRootPath();

        // ── Case 12: dimension mismatch check ────────────────────────────────
        if (expectedDimension > 0 && !vectorMap.isEmpty()) {
            double[] sample = vectorMap.values().iterator().next();
            if (sample.length != expectedDimension) {
                LOGGER.warning("Reconcile: embedding dimension mismatch (stored=" + sample.length
                    + " configured=" + expectedDimension + ") — clearing vector cache for full re-embed");
                vectorMap.clear();
            }
        }

        // ── Collect all eligible files on disk (Cases 8, 9, 13) ─────────────
        List<Path> diskFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(rootPath)) {     // Case 13: recursive
            walk.filter(Files::isRegularFile)
                .filter(p -> !Files.isSymbolicLink(p))       // Case 9
                .filter(this::isWatchedFile)                 // Case 8
                .filter(p -> !p.startsWith(rootPath.resolve(AppConstants.DOT_DIR)))
                .forEach(diskFiles::add);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Reconcile: failed to walk directory", e);
            return;
        }

        // ── Round 1: exact path match (Cases 1, 2, 7, 10, 11) ───────────────
        Set<String>  matchedNoteIds = new HashSet<>();
        List<Path>   unmatchedFiles = new ArrayList<>();

        for (Path file : diskFiles) {
            String rel  = relativize(file);          // Case 13: store relative path
            Note   note = knowledgeBase.getNoteByPath(rel);

            if (note == null) {
                unmatchedFiles.add(file);
                continue;
            }

            matchedNoteIds.add(note.getId());

            try {
                String content = Files.readString(file);   // Case 10: IOException → log
                String newHash = HashUtil.sha256(content);
                boolean hashSame      = newHash.equals(note.getContentHash());
                boolean vectorPresent = vectorMap.containsKey(note.getId()); // Case 11

                note.setFileSize(Files.size(file));
                note.setLastModified(Files.getLastModifiedTime(file).toMillis());

                if (content.strip().length() < AppConstants.MIN_CONTENT_LENGTH) {
                    // Case 7: file shrunk below threshold → move to incubator
                    note.setStatus(NoteStatus.INCUBATOR);
                    vectorMap.remove(note.getId());
                    LOGGER.info("Reconcile: incubator (too short) " + note.getFileName());

                } else if (hashSame && vectorPresent) {
                    // Case 1: path+hash match, vector present → reuse everything
                    LOGGER.info("Reconcile: reuse " + note.getFileName() + " (path match)");

                } else {
                    // Case 2: hash changed  –OR–  Case 11: vector missing
                    String reason = !hashSame ? "content changed" : "vector missing";
                    LOGGER.info("Reconcile: re-embed " + note.getFileName() + " (" + reason + ")");
                    note.setContentHash(newHash);
                    note.setStatus(NoteStatus.ACTIVE);
                    enqueueEmbedding(note, content);
                }

            } catch (IOException e) {
                // Case 10: log warning and leave note as-is
                LOGGER.log(Level.WARNING, "Reconcile: failed to read " + file + " — skipping", e);
            }
        }

        // ── Build orphan lookup maps ──────────────────────────────────────────
        Collection<Note> allNotes = new ArrayList<>(knowledgeBase.getAllNotes());
        List<Note>        orphans  = new ArrayList<>();
        for (Note note : allNotes) {
            if (!matchedNoteIds.contains(note.getId())) orphans.add(note);
        }

        Map<String, Note>       orphanByFileKey = new HashMap<>();
        Map<String, List<Note>> orphansByHash   = new HashMap<>();  // Cases 3+4

        for (Note orphan : orphans) {
            if (orphan.getFileKey()     != null)
                orphanByFileKey.put(orphan.getFileKey(), orphan);
            if (orphan.getContentHash() != null)
                orphansByHash.computeIfAbsent(orphan.getContentHash(), k -> new ArrayList<>())
                             .add(orphan);
        }

        // ── Round 2: match unmatched files against orphans (Cases 3–5) ───────
        List<Path> trulyNewFiles = new ArrayList<>();

        for (Path file : unmatchedFiles) {
            Note   matched     = null;
            String matchReason = "";
            String fileContent = null;

            // Check 1: FileKey (OS inode) — strongest signal (Case 5)
            String fileKey = readFileKey(file);
            if (fileKey != null) {
                matched = orphanByFileKey.get(fileKey);
                if (matched != null) matchReason = "filekey";
            }

            // Check 2: content hash (Cases 3, 4)
            if (matched == null) {
                try {
                    fileContent = Files.readString(file);
                    String hash = HashUtil.sha256(fileContent);
                    List<Note> hashMatches = orphansByHash.getOrDefault(hash, List.of());
                    if (hashMatches.size() == 1) {
                        // Case 3: exactly one orphan with this hash → safe rename
                        matched     = hashMatches.get(0);
                        matchReason = "hash (unique)";
                    } else if (hashMatches.size() > 1) {
                        // Case 4: ambiguous — treat as new to avoid wrong reuse
                        LOGGER.info("Reconcile: ambiguous hash match for " + relativize(file)
                            + " (" + hashMatches.size() + " orphans) → new file");
                    }
                } catch (IOException e) {
                    // Case 10: log and skip this file entirely
                    LOGGER.log(Level.WARNING,
                        "Reconcile: failed to read " + file + " — skipping", e);
                    continue;
                }
            }

            if (matched != null) {
                // Remove from orphan tracking
                orphans.remove(matched);
                orphanByFileKey.remove(matched.getFileKey());
                List<Note> hl = orphansByHash.get(matched.getContentHash());
                if (hl != null) hl.remove(matched);

                // Update identity fields
                String newRel = relativize(file);
                matched.setFilePath(newRel);
                matched.setFileName(file.getFileName().toString());
                matched.setStatus(NoteStatus.ACTIVE);
                if (fileKey != null) matched.setFileKey(fileKey);
                try {
                    matched.setFileSize(Files.size(file));
                    matched.setLastModified(Files.getLastModifiedTime(file).toMillis());
                } catch (IOException ignored) {}

                // Case 5: after rename, check whether content also changed
                try {
                    if (fileContent == null) fileContent = Files.readString(file);
                    String newHash = HashUtil.sha256(fileContent);
                    if (!newHash.equals(matched.getContentHash())) {
                        LOGGER.info("Reconcile: re-embed " + matched.getFileName()
                            + " (renamed via " + matchReason + " + content changed)");
                        matched.setContentHash(newHash);
                        enqueueEmbedding(matched, fileContent);
                    } else {
                        LOGGER.info("Reconcile: renamed " + matched.getFileName()
                            + " via " + matchReason + " (content unchanged)");
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING,
                        "Reconcile: failed to verify content of renamed " + file, e);
                }

            } else {
                trulyNewFiles.add(file);
            }
        }

        // ── Case 6: delete index records + vectors for true orphans ──────────
        for (Note orphan : orphans) {
            knowledgeBase.removeNote(orphan.getId());
            vectorMap.remove(orphan.getId());
            LOGGER.info("Reconcile: deleted (file removed) " + orphan.getFilePath());
        }

        // ── Create Notes for truly new files ─────────────────────────────────
        for (Path file : trulyNewFiles) {
            try {
                String content = Files.readString(file);
                String relPath = relativize(file);   // Case 13: relative path

                // Case 7: too short → incubator, no embed
                if (content.strip().length() < AppConstants.MIN_CONTENT_LENGTH) {
                    Note note = new Note(relPath, file.getFileName().toString());
                    note.setStatus(NoteStatus.INCUBATOR);
                    note.setContentHash(HashUtil.sha256(content));
                    knowledgeBase.addNote(note);
                    LOGGER.info("Reconcile: incubator (too short) " + relPath);
                    continue;
                }

                Note note = new Note(relPath, file.getFileName().toString());
                note.setContentHash(HashUtil.sha256(content));
                note.setFileKey(readFileKey(file));
                note.setFileSize(Files.size(file));
                note.setLastModified(Files.getLastModifiedTime(file).toMillis());
                knowledgeBase.addNote(note);
                enqueueEmbedding(note, content);
                LOGGER.info("Reconcile: new file indexed " + relPath);

            } catch (IOException e) {
                // Case 10: log and skip
                LOGGER.log(Level.WARNING,
                    "Reconcile: failed to process " + file + " — skipping", e);
            }
        }

        persistenceManager.markDirty(PersistenceManager.STORE_INDEX);
        LOGGER.info("Reconcile complete — " + knowledgeBase.getNoteCount() + " notes, "
            + orphans.size() + " deleted, " + trulyNewFiles.size() + " new");
    }

    // ----------------------------------------------------------------
    // Utilities
    // ----------------------------------------------------------------

    /**
     * Only process .md, .txt, and .markdown files. (Case 8)
     */
    private boolean isWatchedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".markdown");
    }

    private String relativize(Path absolute) {
        try {
            return knowledgeBase.getRootPath().relativize(absolute).toString();
        } catch (IllegalArgumentException e) {
            return absolute.toString();
        }
    }

    /** Read the OS-level file key (inode on Unix). Returns null if unavailable. */
    private static String readFileKey(Path path) {
        try {
            BasicFileAttributes attrs =
                Files.readAttributes(path, BasicFileAttributes.class);
            Object key = attrs.fileKey();
            return key != null ? key.toString() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Inline cosine similarity (avoids importing VectorMath). */
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

    /** Exposes the live vector map so VectorDatabase can be seeded from it. */
    public Map<String, double[]> getVectorMap() { return vectorMap; }
}
