package com.docgalaxy.watcher;

import com.docgalaxy.util.AppConstants;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches a directory for file-system changes and notifies listeners.
 *
 * Debounce behaviour (MODIFY events only):
 *   Each file gets a private timer. Every new MODIFY event resets the timer.
 *   Only when the timer fires (no further events for debounceSeconds) does
 *   the listener get notified. This means rapid saves (e.g., auto-save on
 *   every keystroke) produce exactly ONE onFileModified notification.
 *
 * CREATE and DELETE events are forwarded immediately (no debounce).
 *
 * Usage:
 *   FileWatcher watcher = new FileWatcher(rootPath, AppConstants.DEBOUNCE_SECONDS);
 *   watcher.addListener(knowledgeBaseManager);
 *   Thread t = new Thread(watcher, "docgalaxy-filewatcher");
 *   t.setDaemon(true);
 *   t.start();
 *   // later:
 *   watcher.stop();
 */
public class FileWatcher implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(FileWatcher.class.getName());

    private final Path                           watchDir;
    private final int                            debounceSeconds;
    private final List<FileChangeListener>       listeners       = new CopyOnWriteArrayList<>();
    private final Map<Path, ScheduledFuture<?>>  debounceTimers  = new ConcurrentHashMap<>();

    private final ScheduledExecutorService debounceScheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "docgalaxy-debounce");
            t.setDaemon(true);
            return t;
        });

    private volatile boolean running = true;

    // ----------------------------------------------------------------
    // Construction
    // ----------------------------------------------------------------

    public FileWatcher(Path watchDir) {
        this(watchDir, AppConstants.DEBOUNCE_SECONDS);
    }

    public FileWatcher(Path watchDir, int debounceSeconds) {
        this.watchDir        = watchDir;
        this.debounceSeconds = debounceSeconds;
    }

    // ----------------------------------------------------------------
    // Listener management
    // ----------------------------------------------------------------

    public void addListener(FileChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(FileChangeListener listener) {
        listeners.remove(listener);
    }

    // ----------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------

    /** Signal the watch loop to exit cleanly. */
    public void stop() {
        running = false;
        debounceScheduler.shutdownNow();
    }

    // ----------------------------------------------------------------
    // Main watch loop
    // ----------------------------------------------------------------

    @Override
    public void run() {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {

            watchDir.register(ws,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);

            LOGGER.info("FileWatcher started on: " + watchDir);

            while (running) {
                // Poll with timeout so we can check the running flag regularly
                WatchKey key = ws.poll(1, TimeUnit.SECONDS);
                if (key == null) continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    // OVERFLOW means events were dropped; log and continue
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        LOGGER.warning("FileWatcher: OVERFLOW – some events may have been lost");
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    Path filename = ((WatchEvent<Path>) event).context();
                    Path fullPath = watchDir.resolve(filename);

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        fireCreated(fullPath);
                    } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        scheduleModified(fullPath);
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        cancelDebounce(fullPath);   // delete cancels any pending modify
                        fireDeleted(fullPath);
                    }
                }

                // Must reset or no further events are received for this key
                boolean valid = key.reset();
                if (!valid) {
                    LOGGER.warning("FileWatcher: watch key invalidated – directory may have been deleted");
                    break;
                }
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "FileWatcher failed to start", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            LOGGER.info("FileWatcher stopped");
        }
    }

    // ----------------------------------------------------------------
    // Debounce logic
    // ----------------------------------------------------------------

    private void scheduleModified(Path path) {
        // Cancel any existing timer for this path
        cancelDebounce(path);

        ScheduledFuture<?> future = debounceScheduler.schedule(
            () -> {
                debounceTimers.remove(path);
                fireModified(path);
            },
            debounceSeconds, TimeUnit.SECONDS
        );
        debounceTimers.put(path, future);
    }

    private void cancelDebounce(Path path) {
        ScheduledFuture<?> existing = debounceTimers.remove(path);
        if (existing != null) existing.cancel(false);
    }

    // ----------------------------------------------------------------
    // Listener notification (never throws – log and continue on error)
    // ----------------------------------------------------------------

    private void fireCreated(Path path) {
        LOGGER.fine("CREATED: " + path);
        for (FileChangeListener l : listeners) {
            try { l.onFileCreated(path); }
            catch (Exception e) { LOGGER.log(Level.WARNING, "Listener error on CREATE", e); }
        }
    }

    private void fireModified(Path path) {
        LOGGER.fine("MODIFIED: " + path);
        for (FileChangeListener l : listeners) {
            try { l.onFileModified(path); }
            catch (Exception e) { LOGGER.log(Level.WARNING, "Listener error on MODIFY", e); }
        }
    }

    private void fireDeleted(Path path) {
        LOGGER.fine("DELETED: " + path);
        for (FileChangeListener l : listeners) {
            try { l.onFileDeleted(path); }
            catch (Exception e) { LOGGER.log(Level.WARNING, "Listener error on DELETE", e); }
        }
    }
}
