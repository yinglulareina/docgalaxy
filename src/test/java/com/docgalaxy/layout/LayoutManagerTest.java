package com.docgalaxy.layout;

import com.docgalaxy.model.Vector2D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LayoutManager}.
 *
 * <p>Sync-path tests flush the EDT with {@link SwingUtilities#invokeAndWait}
 * to drain the {@code invokeLater} callback before asserting.
 * Async-path tests additionally use a {@link CountDownLatch} (5 s timeout)
 * because {@link javax.swing.SwingWorker#done()} is dispatched to the EDT after
 * the background thread finishes.
 */
class LayoutManagerTest {

    private LayoutManager manager;

    @BeforeEach
    void setUp() {
        manager = new LayoutManager();
    }

    // -----------------------------------------------------------------------
    // Stubs
    // -----------------------------------------------------------------------

    /** Non-iterative stub — returns fixed single-node result immediately. */
    static final class SyncStrategy implements LayoutStrategy {
        private final String name;
        final AtomicInteger callCount = new AtomicInteger();

        SyncStrategy(String name) { this.name = name; }

        @Override
        public Map<String, Vector2D> calculate(List<NodeData> nodes) {
            callCount.incrementAndGet();
            if (nodes.isEmpty()) return Collections.emptyMap();
            return Map.of(nodes.get(0).getNoteId(), new Vector2D(1, 2));
        }

        @Override public boolean isIterative() { return false; }
        @Override public String getName()      { return name; }
    }

    /** Iterative stub — sleeps briefly then returns a fixed result. */
    static final class AsyncStrategy implements LayoutStrategy {
        private final String name;
        final AtomicInteger callCount = new AtomicInteger();

        AsyncStrategy(String name) { this.name = name; }

        @Override
        public Map<String, Vector2D> calculate(List<NodeData> nodes) {
            callCount.incrementAndGet();
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (nodes.isEmpty()) return Collections.emptyMap();
            return Map.of(nodes.get(0).getNoteId(), new Vector2D(3, 4));
        }

        @Override public boolean isIterative() { return true; }
        @Override public String getName()      { return name; }
    }

    /** Strategy that always throws from calculate(). */
    static final class ThrowingStrategy implements LayoutStrategy {
        @Override
        public Map<String, Vector2D> calculate(List<NodeData> nodes) {
            throw new RuntimeException("intentional failure");
        }
        @Override public boolean isIterative() { return false; }
        @Override public String getName()      { return "Throwing"; }
    }

    /** Iterative strategy that always throws from calculate(). */
    static final class ThrowingAsyncStrategy implements LayoutStrategy {
        @Override
        public Map<String, Vector2D> calculate(List<NodeData> nodes) {
            throw new RuntimeException("intentional async failure");
        }
        @Override public boolean isIterative() { return true; }
        @Override public String getName()      { return "ThrowingAsync"; }
    }

    private static NodeData node(String id) {
        return new NodeData(id, new Vector2D(0, 0), "s", Collections.emptyList());
    }

    /** Drains the EDT queue so that invokeLater callbacks have fired. */
    private static void flushEdt() throws InvocationTargetException, InterruptedException {
        SwingUtilities.invokeAndWait(() -> { /* no-op */ });
    }

    // -----------------------------------------------------------------------
    // registerStrategy() — validation
    // -----------------------------------------------------------------------

    @Test
    void registerStrategy_null_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> manager.registerStrategy(null));
    }

    // -----------------------------------------------------------------------
    // switchLayout() — argument validation
    // -----------------------------------------------------------------------

    @Test
    void switchLayout_nullName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> manager.switchLayout(null, List.of(), r -> {}));
    }

    @Test
    void switchLayout_nullNodes_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> manager.switchLayout("X", null, r -> {}));
    }

    @Test
    void switchLayout_nullCallback_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> manager.switchLayout("X", List.of(), null));
    }

    // -----------------------------------------------------------------------
    // getAvailableLayouts()
    // -----------------------------------------------------------------------

    @Test
    void getAvailableLayouts_empty_initiallyEmpty() {
        assertTrue(manager.getAvailableLayouts().isEmpty());
    }

    @Test
    void getAvailableLayouts_afterRegister_containsName() {
        manager.registerStrategy(new SyncStrategy("Tree"));
        assertTrue(manager.getAvailableLayouts().contains("Tree"));
    }

    @Test
    void getAvailableLayouts_multipleStrategies_allPresent() {
        manager.registerStrategy(new SyncStrategy("Tree"));
        manager.registerStrategy(new SyncStrategy("Radial"));
        manager.registerStrategy(new AsyncStrategy("Galaxy"));
        List<String> names = manager.getAvailableLayouts();
        assertTrue(names.contains("Tree") && names.contains("Radial") && names.contains("Galaxy"));
        assertEquals(3, names.size());
    }

    @Test
    void getAvailableLayouts_preservesRegistrationOrder() {
        manager.registerStrategy(new SyncStrategy("A"));
        manager.registerStrategy(new SyncStrategy("B"));
        manager.registerStrategy(new SyncStrategy("C"));
        assertEquals(List.of("A", "B", "C"), manager.getAvailableLayouts());
    }

    @Test
    void getAvailableLayouts_resultIsUnmodifiable() {
        manager.registerStrategy(new SyncStrategy("X"));
        List<String> names = manager.getAvailableLayouts();
        assertThrows(UnsupportedOperationException.class, () -> names.add("Y"));
    }

    @Test
    void registerStrategy_sameNameTwice_replacesOld() {
        manager.registerStrategy(new SyncStrategy("Same"));
        manager.registerStrategy(new SyncStrategy("Same"));
        assertEquals(1, manager.getAvailableLayouts().size());
    }

    // -----------------------------------------------------------------------
    // getCurrentStrategy()
    // -----------------------------------------------------------------------

    @Test
    void getCurrentStrategy_initiallyNull() {
        assertNull(manager.getCurrentStrategy());
    }

    @Test
    void getCurrentStrategy_afterSwitch_returnsCorrectStrategy()
            throws InvocationTargetException, InterruptedException {
        SyncStrategy s = new SyncStrategy("Tree");
        manager.registerStrategy(s);
        manager.switchLayout("Tree", List.of(node("n1")), r -> {});
        flushEdt();
        assertSame(s, manager.getCurrentStrategy());
    }

    @Test
    void getCurrentStrategy_switchTwice_returnsLastStrategy()
            throws InvocationTargetException, InterruptedException {
        SyncStrategy s1 = new SyncStrategy("A");
        SyncStrategy s2 = new SyncStrategy("B");
        manager.registerStrategy(s1);
        manager.registerStrategy(s2);
        manager.switchLayout("A", List.of(node("n")), r -> {});
        manager.switchLayout("B", List.of(node("n")), r -> {});
        flushEdt();
        assertSame(s2, manager.getCurrentStrategy());
    }

    // -----------------------------------------------------------------------
    // switchLayout() — unknown strategy fallback
    // -----------------------------------------------------------------------

    @Test
    void switchLayout_unknownName_callbackWithEmptyMap()
            throws InvocationTargetException, InterruptedException {
        AtomicReference<Map<String, Vector2D>> ref = new AtomicReference<>();
        manager.switchLayout("NoSuchLayout", List.of(), ref::set);
        flushEdt();
        assertNotNull(ref.get());
        assertTrue(ref.get().isEmpty(), "unknown strategy must produce empty map");
    }

    @Test
    void switchLayout_unknownName_currentStrategyUnchanged()
            throws InvocationTargetException, InterruptedException {
        SyncStrategy s = new SyncStrategy("X");
        manager.registerStrategy(s);
        manager.switchLayout("X", List.of(), r -> {});
        flushEdt();
        manager.switchLayout("NoSuch", List.of(), r -> {});
        flushEdt();
        // currentStrategy must still be s (set by the first switch)
        assertSame(s, manager.getCurrentStrategy());
    }

    // -----------------------------------------------------------------------
    // switchLayout() — non-iterative (sync) path
    // -----------------------------------------------------------------------

    @Test
    void switchLayout_syncStrategy_callbackInvoked()
            throws InvocationTargetException, InterruptedException {
        manager.registerStrategy(new SyncStrategy("Tree"));
        AtomicReference<Map<String, Vector2D>> ref = new AtomicReference<>();
        manager.switchLayout("Tree", List.of(node("a")), ref::set);
        flushEdt();
        assertNotNull(ref.get(), "onComplete must have been called");
    }

    @Test
    void switchLayout_syncStrategy_callbackReceivesCalculatedPosition()
            throws InvocationTargetException, InterruptedException {
        manager.registerStrategy(new SyncStrategy("Tree"));
        AtomicReference<Map<String, Vector2D>> ref = new AtomicReference<>();
        manager.switchLayout("Tree", List.of(node("a")), ref::set);
        flushEdt();
        assertEquals(1.0, ref.get().get("a").getX(), 1e-9);
        assertEquals(2.0, ref.get().get("a").getY(), 1e-9);
    }

    @Test
    void switchLayout_syncStrategy_callbackOnEdtThread()
            throws InvocationTargetException, InterruptedException {
        manager.registerStrategy(new SyncStrategy("Tree"));
        AtomicReference<Boolean> onEdt = new AtomicReference<>();
        manager.switchLayout("Tree", List.of(node("a")),
                r -> onEdt.set(SwingUtilities.isEventDispatchThread()));
        flushEdt();
        assertTrue(onEdt.get(), "onComplete must be called on the EDT");
    }

    @Test
    void switchLayout_syncStrategy_emptyNodes_callbackWithEmptyMap()
            throws InvocationTargetException, InterruptedException {
        manager.registerStrategy(new SyncStrategy("Tree"));
        AtomicReference<Map<String, Vector2D>> ref = new AtomicReference<>();
        manager.switchLayout("Tree", List.of(), ref::set);
        flushEdt();
        assertNotNull(ref.get());
        assertTrue(ref.get().isEmpty());
    }

    @Test
    void switchLayout_syncThrows_callbackWithEmptyMap()
            throws InvocationTargetException, InterruptedException {
        manager.registerStrategy(new ThrowingStrategy());
        AtomicReference<Map<String, Vector2D>> ref = new AtomicReference<>();
        manager.switchLayout("Throwing", List.of(node("a")), ref::set);
        flushEdt();
        assertNotNull(ref.get());
        assertTrue(ref.get().isEmpty(), "exception in calculate() must yield empty map");
    }

    @Test
    void switchLayout_syncStrategy_calculateCalledOnce()
            throws InvocationTargetException, InterruptedException {
        SyncStrategy s = new SyncStrategy("Tree");
        manager.registerStrategy(s);
        manager.switchLayout("Tree", List.of(node("n")), r -> {});
        flushEdt();
        assertEquals(1, s.callCount.get());
    }

    // -----------------------------------------------------------------------
    // switchLayout() — iterative (async) path
    // -----------------------------------------------------------------------

    @Test
    void switchLayout_asyncStrategy_callbackEventuallyInvoked()
            throws InterruptedException, InvocationTargetException {
        manager.registerStrategy(new AsyncStrategy("Galaxy"));
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, Vector2D>> ref = new AtomicReference<>();
        manager.switchLayout("Galaxy", List.of(node("a")), result -> {
            ref.set(result);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "async callback must fire within 5 s");
        assertNotNull(ref.get());
    }

    @Test
    void switchLayout_asyncStrategy_callbackReceivesResult()
            throws InterruptedException, InvocationTargetException {
        manager.registerStrategy(new AsyncStrategy("Galaxy"));
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, Vector2D>> ref = new AtomicReference<>();
        manager.switchLayout("Galaxy", List.of(node("n1")), result -> {
            ref.set(result);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(3.0, ref.get().get("n1").getX(), 1e-9);
        assertEquals(4.0, ref.get().get("n1").getY(), 1e-9);
    }

    @Test
    void switchLayout_asyncStrategy_callbackOnEdtThread()
            throws InterruptedException, InvocationTargetException {
        manager.registerStrategy(new AsyncStrategy("Galaxy"));
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> onEdt = new AtomicReference<>();
        manager.switchLayout("Galaxy", List.of(node("a")), result -> {
            onEdt.set(SwingUtilities.isEventDispatchThread());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(onEdt.get(), "async callback must be delivered on the EDT");
    }

    @Test
    void switchLayout_asyncThrows_callbackWithEmptyMap()
            throws InterruptedException, InvocationTargetException {
        manager.registerStrategy(new ThrowingAsyncStrategy());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, Vector2D>> ref = new AtomicReference<>();
        manager.switchLayout("ThrowingAsync", List.of(node("a")), result -> {
            ref.set(result);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(ref.get().isEmpty(), "exception in async calculate() must yield empty map");
    }

    // -----------------------------------------------------------------------
    // switchLayout() — previous worker cancellation
    // -----------------------------------------------------------------------

    @Test
    void switchLayout_secondIterativeCall_prevWorkerCancelled_onlyLastCallbackFires()
            throws InterruptedException, InvocationTargetException {
        // Use a slow strategy so the first run is still in flight when the second starts
        LayoutStrategy slow = new LayoutStrategy() {
            @Override public Map<String, Vector2D> calculate(List<NodeData> n) {
                try { Thread.sleep(500); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Map.of("slow", new Vector2D(9, 9));
            }
            @Override public boolean isIterative() { return true; }
            @Override public String getName()      { return "Slow"; }
        };
        manager.registerStrategy(slow);
        manager.registerStrategy(new AsyncStrategy("Fast"));

        AtomicInteger callbackCount = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);

        // First call (slow) — will be cancelled
        manager.switchLayout("Slow", List.of(node("x")), r -> callbackCount.incrementAndGet());

        // Immediately start a second call (fast) — must cancel the first
        manager.switchLayout("Fast", List.of(node("y")), r -> {
            callbackCount.incrementAndGet();
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        // Only the second (fast) callback should have fired
        assertEquals(1, callbackCount.get(),
                "cancellation of first worker must prevent its callback from firing");
    }

    // -----------------------------------------------------------------------
    // Repeated switchLayout calls — no state leakage between calls
    // -----------------------------------------------------------------------

    @Test
    void switchLayout_calledThreeTimes_allCallbacksFire()
            throws InvocationTargetException, InterruptedException {
        manager.registerStrategy(new SyncStrategy("T"));
        AtomicInteger count = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            manager.switchLayout("T", List.of(node("n")), r -> count.incrementAndGet());
        }
        flushEdt();
        assertEquals(3, count.get(), "each switchLayout must fire its own callback");
    }
}
