package com.docgalaxy.layout;

import com.docgalaxy.model.Vector2D;
import com.docgalaxy.util.AppConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Uniform-grid spatial index for O(1) neighbourhood queries in the
 * force-directed layout engine.
 *
 * <p>The canvas is partitioned into a {@value AppConstants#GRID_SIZE}×{@value AppConstants#GRID_SIZE}
 * grid of equal-width cells.  Each call to {@link #rebuild} resets the index
 * and assigns every node to its cell based on its current position.
 * {@link #getNearbyNodes} then returns the nodes in the same cell plus all
 * eight adjacent cells — at most 9 cells in total — giving a constant-time
 * candidate set for repulsion force calculations.
 *
 * <p>Nodes whose positions fall outside the canvas bounds are clamped to the
 * nearest cell rather than discarded, so no node is ever silently lost.
 *
 * <p>This class is <em>not</em> thread-safe.  The layout engine must call
 * {@link #rebuild} and {@link #getNearbyNodes} from the same thread (or hold
 * an external lock).
 */
public final class SpatialGrid {

    /** Number of columns (and rows) in the grid. */
    private final int gridSize;

    /** Primary index: cell → list of nodes assigned to that cell. */
    private final Map<GridCell, List<NodeData>> cells = new HashMap<>();

    /** Reverse index: nodeId → cell, for O(1) cell lookup in getNearbyNodes. */
    private final Map<String, GridCell> nodeToCell = new HashMap<>();

    /** Width of one cell in canvas units; set during {@link #rebuild}. */
    private double cellWidth;

    /** Height of one cell in canvas units; set during {@link #rebuild}. */
    private double cellHeight;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /** Production constructor — uses the grid size from {@link AppConstants}. */
    public SpatialGrid() {
        this(AppConstants.GRID_SIZE);
    }

    /** Package-private constructor for unit tests with an arbitrary grid size. */
    SpatialGrid(int gridSize) {
        if (gridSize <= 0) throw new IllegalArgumentException("gridSize must be > 0");
        this.gridSize = gridSize;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * (Re)builds the spatial index from the given node list.
     *
     * <p>Any previous index is cleared.  Each node is placed in the cell
     * corresponding to {@link NodeData#getInitialPosition()}.  Positions that
     * lie outside {@code [0, canvasWidth) × [0, canvasHeight)} are clamped to
     * the nearest valid cell.
     *
     * @param nodes        the nodes to index; must not be {@code null}
     * @param canvasWidth  width of the layout canvas in world-space units (must be &gt; 0)
     * @param canvasHeight height of the layout canvas in world-space units (must be &gt; 0)
     * @throws IllegalArgumentException if {@code nodes} is {@code null} or either
     *                                  canvas dimension is &le; 0
     */
    public void rebuild(List<NodeData> nodes, double canvasWidth, double canvasHeight) {
        if (nodes      == null) throw new IllegalArgumentException("nodes must not be null");
        if (canvasWidth  <= 0) throw new IllegalArgumentException("canvasWidth must be > 0");
        if (canvasHeight <= 0) throw new IllegalArgumentException("canvasHeight must be > 0");

        cells.clear();
        nodeToCell.clear();

        cellWidth  = canvasWidth  / gridSize;
        cellHeight = canvasHeight / gridSize;

        for (NodeData node : nodes) {
            Vector2D pos = node.getInitialPosition();
            GridCell cell = cellFor(pos);
            cells.computeIfAbsent(cell, k -> new ArrayList<>()).add(node);
            nodeToCell.put(node.getNoteId(), cell);
        }
    }

    /**
     * Returns all nodes in the same cell as {@code node} and in the eight
     * immediately adjacent cells (Moore neighbourhood, radius 1).
     *
     * <p>The returned list may include {@code node} itself — callers are
     * responsible for skipping self-interactions when computing repulsion forces.
     *
     * <p>If {@code node} was not present in the most recent {@link #rebuild}
     * call, an empty list is returned.
     *
     * @param node the query node; must not be {@code null}
     * @return unmodifiable snapshot of candidate nearby nodes (never {@code null})
     * @throws IllegalArgumentException if {@code node} is {@code null}
     */
    public List<NodeData> getNearbyNodes(NodeData node) {
        if (node == null) throw new IllegalArgumentException("node must not be null");

        GridCell center = nodeToCell.get(node.getNoteId());
        if (center == null) return Collections.emptyList();

        List<NodeData> result = new ArrayList<>();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int r = center.row() + dr;
                int c = center.col() + dc;
                if (r < 0 || r >= gridSize || c < 0 || c >= gridSize) continue;
                List<NodeData> bucket = cells.get(new GridCell(r, c));
                if (bucket != null) result.addAll(bucket);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the number of non-empty cells currently in the index.
     * Primarily useful for tests and diagnostics.
     */
    public int occupiedCellCount() {
        return cells.size();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Maps a world-space position to the grid cell that contains it. */
    private GridCell cellFor(Vector2D pos) {
        int col = (int) (pos.getX() / cellWidth);
        int row = (int) (pos.getY() / cellHeight);
        // Clamp to valid range (handles positions exactly on the right/bottom edge
        // and positions outside the canvas bounds)
        col = Math.max(0, Math.min(gridSize - 1, col));
        row = Math.max(0, Math.min(gridSize - 1, row));
        return new GridCell(row, col);
    }

    // -------------------------------------------------------------------------
    // GridCell — value type used as HashMap key
    // -------------------------------------------------------------------------

    /**
     * Immutable row/column coordinate used as the key in the cell map.
     *
     * <p>Java record semantics give us correct {@code equals} and
     * {@code hashCode} for free, making it safe to use as a {@link HashMap} key.
     */
    record GridCell(int row, int col) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GridCell gc)) return false;
            return row == gc.row && col == gc.col;
        }

        @Override
        public int hashCode() {
            return Objects.hash(row, col);
        }
    }
}
