package com.docgalaxy.persistence;

import com.docgalaxy.model.KnowledgeBase;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central coordinator for dirty-checking and periodic flushing.
 *
 * Usage:
 *   PersistenceManager pm = new PersistenceManager();
 *   pm.init(kb, indexStore, embeddingStore);
 *   pm.markDirty("index");          // call whenever notes change
 *   pm.markDirty("embeddings");     // call whenever vectors change
 *   // automatic flush every 30 s, and on JVM shutdown
 */
public class PersistenceManager {

    private static final Logger LOGGER = Logger.getLogger(PersistenceManager.class.getName());

    // Store name constants – use these with markDirty()
    public static final String STORE_INDEX      = "index";
    public static final String STORE_EMBEDDINGS = "embeddings";

    private final Map<String, Boolean> dirtyFlags = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?>        flushTask;

    // Managed stores and data references
    private KnowledgeBase          currentKb;
    private IndexStore             indexStore;
    private EmbeddingStore         embeddingStore;
    private Map<String, double[]>  currentVectors;

    // ----------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------

    /**
     * Initialise with the active knowledge base and its stores.
     * Schedules periodic flush every 30 s and registers a JVM shutdown hook.
     */
    public void init(KnowledgeBase kb,
                     IndexStore indexStore,
                     EmbeddingStore embeddingStore) {
        this.currentKb      = kb;
        this.indexStore     = indexStore;
        this.embeddingStore = embeddingStore;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "docgalaxy-persistence-flush");
            t.setDaemon(true);
            return t;
        });

        flushTask = scheduler.scheduleAtFixedRate(
            this::flushIfDirty, 30, 30, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(
            new Thread(this::forceFlush, "docgalaxy-persistence-shutdown"));

        LOGGER.info("PersistenceManager initialised, flush interval = 30 s");
    }

    /**
     * Update the vector map reference when VectorDatabase is populated.
     * Called by KnowledgeBaseManager after embeddings are available.
     */
    public void setCurrentVectors(Map<String, double[]> vectors) {
        this.currentVectors = vectors;
    }

    // ----------------------------------------------------------------
    // Dirty tracking
    // ----------------------------------------------------------------

    public void markDirty(String storeName) {
        dirtyFlags.put(storeName, Boolean.TRUE);
    }

    // ----------------------------------------------------------------
    // Flush
    // ----------------------------------------------------------------

    /** Flush only the stores that have been marked dirty. */
    public synchronized void flushIfDirty() {
        if (Boolean.TRUE.equals(dirtyFlags.get(STORE_INDEX))) {
            flushIndex();
        }
        if (Boolean.TRUE.equals(dirtyFlags.get(STORE_EMBEDDINGS))) {
            flushEmbeddings();
        }
    }

    /** Flush every store regardless of dirty flags (used by shutdown hook). */
    public synchronized void forceFlush() {
        LOGGER.info("Force-flushing all persistence stores…");
        flushIndex();
        flushEmbeddings();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    // ----------------------------------------------------------------
    // Private flush helpers
    // ----------------------------------------------------------------

    private void flushIndex() {
        if (indexStore == null || currentKb == null) return;
        try {
            indexStore.saveKnowledgeBase(currentKb);
            dirtyFlags.put(STORE_INDEX, Boolean.FALSE);
            LOGGER.fine("index.json flushed (" + currentKb.getNoteCount() + " notes)");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to flush index.json", e);
        }
    }

    private void flushEmbeddings() {
        if (embeddingStore == null || currentVectors == null) return;
        try {
            embeddingStore.save(currentVectors);
            dirtyFlags.put(STORE_EMBEDDINGS, Boolean.FALSE);
            LOGGER.fine("embeddings.bin flushed (" + currentVectors.size() + " vectors)");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to flush embeddings.bin", e);
        }
    }
}
