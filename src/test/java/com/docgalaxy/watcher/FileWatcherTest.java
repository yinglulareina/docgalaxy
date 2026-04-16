package com.docgalaxy.watcher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FileWatcher.
 *
 * Note: Java WatchService on macOS uses polling (~2 s interval).
 * Tests that need the OS to "know" a file exists before modifying/deleting it
 * must sleep ≥ 3 s after creation. Event-detection timeouts are set to 10 s.
 */
class FileWatcherTest {

    @TempDir
    Path watchDir;

    private FileWatcher watcher;
    private Thread      watcherThread;

    /** Simple recording listener used across tests. */
    private static class RecordingListener implements FileChangeListener {
        final List<Path> created  = new ArrayList<>();
        final List<Path> modified = new ArrayList<>();
        final List<Path> deleted  = new ArrayList<>();

        CountDownLatch createdLatch  = new CountDownLatch(1);
        CountDownLatch modifiedLatch = new CountDownLatch(1);
        CountDownLatch deletedLatch  = new CountDownLatch(1);

        @Override public void onFileCreated(Path path)  { created.add(path);  createdLatch.countDown(); }
        @Override public void onFileModified(Path path) { modified.add(path); modifiedLatch.countDown(); }
        @Override public void onFileDeleted(Path path)  { deleted.add(path);  deletedLatch.countDown(); }
    }

    @BeforeEach
    void startWatcher() throws InterruptedException {
        // debounceSeconds = 1 for fast tests
        watcher = new FileWatcher(watchDir, 1);
        watcherThread = new Thread(watcher, "test-filewatcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
        // Give the WatchService time to register the directory
        Thread.sleep(500);
    }

    @AfterEach
    void stopWatcher() throws InterruptedException {
        watcher.stop();
        watcherThread.join(3_000);
    }

    // ----------------------------------------------------------------
    // CREATE event forwarded immediately
    // ----------------------------------------------------------------
    @Test
    void onCreate_notifiesListener() throws IOException, InterruptedException {
        RecordingListener listener = new RecordingListener();
        watcher.addListener(listener);

        Path file = watchDir.resolve("note.md");
        Files.writeString(file, "# Hello");

        boolean fired = listener.createdLatch.await(10, TimeUnit.SECONDS);
        assertTrue(fired, "onFileCreated should have been called");
        assertEquals(1, listener.created.size());
    }

    // ----------------------------------------------------------------
    // DELETE event forwarded immediately
    // ----------------------------------------------------------------
    @Test
    void onDelete_notifiesListener() throws IOException, InterruptedException {
        // Create the file and let the WatchService poll at least once (≥ 2 s)
        // so it "knows" the file exists before we delete it.
        Path file = watchDir.resolve("delete-me.md");
        Files.writeString(file, "to be deleted");
        Thread.sleep(3_000);   // wait for WatchService to register the file

        RecordingListener listener = new RecordingListener();
        watcher.addListener(listener);

        Files.delete(file);

        boolean fired = listener.deletedLatch.await(10, TimeUnit.SECONDS);
        assertTrue(fired, "onFileDeleted should have been called");
        assertEquals(1, listener.deleted.size());
    }

    // ----------------------------------------------------------------
    // MODIFY event debounced – rapid writes produce exactly one notification
    // ----------------------------------------------------------------
    @Test
    void onModify_debounced_singleNotification() throws IOException, InterruptedException {
        // Create file and let the WatchService register it
        Path file = watchDir.resolve("debounce.md");
        Files.writeString(file, "version 1");
        Thread.sleep(3_000);   // wait for WatchService to register the file

        RecordingListener listener = new RecordingListener();
        watcher.addListener(listener);

        // Write 3 times quickly – debounce should collapse to 1 notification
        for (int i = 2; i <= 4; i++) {
            Files.writeString(file, "version " + i);
            Thread.sleep(50);
        }

        // debounce fires 1 s after last write; WatchService polling adds up to 2 s
        boolean fired = listener.modifiedLatch.await(10, TimeUnit.SECONDS);
        assertTrue(fired, "onFileModified should have been called at least once");

        // Allow a little extra time to see if more events arrive
        Thread.sleep(1_500);
        assertEquals(1, listener.modified.size(),
            "Rapid writes should be collapsed into a single onFileModified call");
    }

    // ----------------------------------------------------------------
    // DELETE cancels pending MODIFY debounce (no stale modify after delete)
    // ----------------------------------------------------------------
    @Test
    void onDelete_cancelsPendingModify() throws IOException, InterruptedException {
        Path file = watchDir.resolve("cancel-test.md");
        Files.writeString(file, "initial");
        Thread.sleep(3_000);   // wait for WatchService to register the file

        RecordingListener listener = new RecordingListener();
        watcher.addListener(listener);

        // Modify, then immediately delete before debounce fires
        Files.writeString(file, "modified");
        Thread.sleep(100);
        Files.delete(file);

        // DELETE should arrive; wait generously
        listener.deletedLatch.await(10, TimeUnit.SECONDS);

        // Wait past debounce window to confirm no stale MODIFY fires
        Thread.sleep(2_000);

        assertEquals(0, listener.modified.size(),
            "onFileModified should NOT fire after the file was deleted");
        assertEquals(1, listener.deleted.size());
    }

    // ----------------------------------------------------------------
    // removeListener stops notifications
    // ----------------------------------------------------------------
    @Test
    void removeListener_stopsNotifications() throws IOException, InterruptedException {
        RecordingListener listener = new RecordingListener();
        watcher.addListener(listener);
        watcher.removeListener(listener);

        Path file = watchDir.resolve("untracked.md");
        Files.writeString(file, "hello");

        // Should NOT fire – listener was removed; wait 5 s to be sure
        boolean fired = listener.createdLatch.await(5, TimeUnit.SECONDS);
        assertFalse(fired, "Removed listener should not receive events");
    }
}
