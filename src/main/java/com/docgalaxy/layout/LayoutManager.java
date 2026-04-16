package com.docgalaxy.layout;

import com.docgalaxy.model.Vector2D;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Central coordinator for all {@link LayoutStrategy} implementations.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Maintains a registry of named strategies ({@link #registerStrategy}).</li>
 *   <li>Switches between strategies on demand ({@link #switchLayout}):
 *       <ul>
 *         <li><b>Iterative</b> strategies (e.g. force-directed): computation
 *             runs in a {@link SwingWorker} background thread so the EDT stays
 *             responsive.  The result is delivered to the caller's
 *             {@code onComplete} callback <em>on the EDT</em> via
 *             {@link SwingWorker#done()}.</li>
 *         <li><b>Non-iterative</b> strategies (e.g. tree, radial): computation
 *             runs synchronously on the calling thread, then {@code onComplete}
 *             is posted to the EDT via
 *             {@link SwingUtilities#invokeLater invokeLater}.</li>
 *       </ul>
 *   </li>
 *   <li>At most one layout computation is active at a time — starting a new
 *       one cancels any still-running {@link SwingWorker}.</li>
 * </ul>
 *
 * <p>This class is <em>not</em> thread-safe: all public methods should be
 * called from the EDT (the same thread that owns the Swing UI), which matches
 * normal UI-event flow.
 */
public final class LayoutManager {

    /** Registry: strategy name → strategy (insertion order preserved). */
    private final Map<String, LayoutStrategy> strategies = new LinkedHashMap<>();

    /** The strategy that was most recently activated via {@link #switchLayout}. */
    private volatile LayoutStrategy currentStrategy;

    /**
     * Worker running the most recent iterative layout; {@code null} when no
     * iterative layout is in progress.
     */
    private SwingWorker<Map<String, Vector2D>, Void> activeWorker;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Registers a layout strategy under its {@link LayoutStrategy#getName() name}.
     * If a strategy with the same name was previously registered it is replaced.
     *
     * @param strategy the strategy to register; must not be {@code null}
     * @throws IllegalArgumentException if {@code strategy} is {@code null}
     */
    public void registerStrategy(LayoutStrategy strategy) {
        if (strategy == null) throw new IllegalArgumentException("strategy must not be null");
        strategies.put(strategy.getName(), strategy);
    }

    /**
     * Switches to the named layout strategy and triggers a position calculation.
     *
     * <p>If the strategy's {@link LayoutStrategy#isIterative()} flag is
     * {@code true}, the calculation is off-loaded to a {@link SwingWorker};
     * any previously running worker is cancelled first.  The {@code onComplete}
     * callback is invoked on the <em>Event Dispatch Thread</em> once the result
     * is ready.
     *
     * <p>If the strategy is non-iterative, the calculation runs synchronously on
     * the calling thread and {@code onComplete} is posted to the EDT via
     * {@link SwingUtilities#invokeLater invokeLater}.
     *
     * <p>If the requested strategy name is not registered, {@code onComplete} is
     * called with an empty map as a safe fallback.  Any exception thrown by
     * {@link LayoutStrategy#calculate} is caught and similarly results in an
     * empty-map callback.
     *
     * @param name       name of the registered strategy to activate
     * @param nodes      note nodes to pass to the strategy
     * @param onComplete callback receiving the {@code noteId → position} result
     *                   map; always invoked on the EDT
     * @throws IllegalArgumentException if any argument is {@code null}
     */
    public void switchLayout(String name,
                              List<NodeData> nodes,
                              Consumer<Map<String, Vector2D>> onComplete) {
        if (name       == null) throw new IllegalArgumentException("name must not be null");
        if (nodes      == null) throw new IllegalArgumentException("nodes must not be null");
        if (onComplete == null) throw new IllegalArgumentException("onComplete must not be null");

        LayoutStrategy strategy = strategies.get(name);
        if (strategy == null) {
            // Unknown strategy — safe fallback so the UI is never left hanging
            SwingUtilities.invokeLater(() -> onComplete.accept(Collections.emptyMap()));
            return;
        }

        currentStrategy = strategy;

        // Cancel any still-running iterative worker before starting a new layout
        if (activeWorker != null && !activeWorker.isDone()) {
            activeWorker.cancel(true);
            activeWorker = null;
        }

        if (strategy.isIterative()) {
            runAsync(strategy, nodes, onComplete);
        } else {
            runSync(strategy, nodes, onComplete);
        }
    }

    /**
     * Returns the strategy most recently activated by {@link #switchLayout}, or
     * {@code null} if no layout has been switched to yet.
     *
     * @return current active strategy, or {@code null}
     */
    public LayoutStrategy getCurrentStrategy() {
        return currentStrategy;
    }

    /**
     * Returns an unmodifiable snapshot of the registered strategy names in
     * registration order.
     *
     * @return immutable list of available layout names
     */
    public List<String> getAvailableLayouts() {
        return Collections.unmodifiableList(new ArrayList<>(strategies.keySet()));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Runs an iterative layout in a SwingWorker; delivers result on EDT via done(). */
    private void runAsync(LayoutStrategy strategy,
                          List<NodeData> nodes,
                          Consumer<Map<String, Vector2D>> onComplete) {
        activeWorker = new SwingWorker<>() {
            @Override
            protected Map<String, Vector2D> doInBackground() {
                try {
                    return strategy.calculate(nodes);
                } catch (Exception e) {
                    return Collections.emptyMap();
                }
            }

            @Override
            protected void done() {
                // done() is called on the EDT by the SwingWorker machinery
                Map<String, Vector2D> result;
                try {
                    result = get();
                } catch (CancellationException | InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return; // cancelled — caller already started a newer layout
                } catch (ExecutionException e) {
                    result = Collections.emptyMap();
                }
                onComplete.accept(result);
            }
        };
        activeWorker.execute();
    }

    /**
     * Runs a non-iterative layout synchronously on the calling thread, then
     * posts {@code onComplete} to the EDT via {@link SwingUtilities#invokeLater}.
     */
    private static void runSync(LayoutStrategy strategy,
                                 List<NodeData> nodes,
                                 Consumer<Map<String, Vector2D>> onComplete) {
        Map<String, Vector2D> result;
        try {
            result = strategy.calculate(nodes);
        } catch (Exception e) {
            result = Collections.emptyMap();
        }
        final Map<String, Vector2D> finalResult = result;
        SwingUtilities.invokeLater(() -> onComplete.accept(finalResult));
    }
}
