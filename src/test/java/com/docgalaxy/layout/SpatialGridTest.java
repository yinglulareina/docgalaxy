package com.docgalaxy.layout;

import com.docgalaxy.model.Vector2D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SpatialGrid}.
 *
 * <p>All tests use a 4×4 grid over a 400×400 canvas, giving 100×100 cells,
 * which makes position-to-cell arithmetic trivial to reason about by hand.
 */
class SpatialGridTest {

    /** Canvas dimensions shared by most tests. */
    private static final double W = 400.0;
    private static final double H = 400.0;

    /**
     * 4×4 grid → cellWidth = cellHeight = 100.
     * row = (int)(y/100), col = (int)(x/100).
     */
    private SpatialGrid grid;

    @BeforeEach
    void setUp() {
        grid = new SpatialGrid(4);
    }

    // -----------------------------------------------------------------------
    // Constructor validation
    // -----------------------------------------------------------------------

    @Test
    void constructor_zeroGridSize_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new SpatialGrid(0));
    }

    @Test
    void constructor_negativeGridSize_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new SpatialGrid(-1));
    }

    // -----------------------------------------------------------------------
    // rebuild() — argument validation
    // -----------------------------------------------------------------------

    @Test
    void rebuild_nullNodes_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> grid.rebuild(null, W, H));
    }

    @Test
    void rebuild_zeroCanvasWidth_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> grid.rebuild(List.of(), 0, H));
    }

    @Test
    void rebuild_negativeCanvasWidth_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> grid.rebuild(List.of(), -1, H));
    }

    @Test
    void rebuild_zeroCanvasHeight_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> grid.rebuild(List.of(), W, 0));
    }

    @Test
    void rebuild_negativeCanvasHeight_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> grid.rebuild(List.of(), W, -1));
    }

    // -----------------------------------------------------------------------
    // rebuild() — empty input
    // -----------------------------------------------------------------------

    @Test
    void rebuild_emptyList_occupiedCellCountIsZero() {
        grid.rebuild(Collections.emptyList(), W, H);
        assertEquals(0, grid.occupiedCellCount());
    }

    // -----------------------------------------------------------------------
    // getNearbyNodes() — argument validation
    // -----------------------------------------------------------------------

    @Test
    void getNearbyNodes_nullNode_throwsIllegalArgumentException() {
        grid.rebuild(List.of(), W, H);
        assertThrows(IllegalArgumentException.class,
                () -> grid.getNearbyNodes(null));
    }

    @Test
    void getNearbyNodes_nodeNotInGrid_returnsEmptyList() {
        grid.rebuild(List.of(), W, H);
        NodeData stranger = node("stranger", 50, 50);
        assertEquals(0, grid.getNearbyNodes(stranger).size());
    }

    // -----------------------------------------------------------------------
    // Single node — cell assignment and self-return
    // -----------------------------------------------------------------------

    @Test
    void rebuild_singleNode_oneOccupiedCell() {
        grid.rebuild(List.of(node("a", 50, 50)), W, H);
        assertEquals(1, grid.occupiedCellCount());
    }

    @Test
    void getNearbyNodes_singleNode_returnsSelf() {
        NodeData a = node("a", 50, 50);
        grid.rebuild(List.of(a), W, H);
        List<NodeData> nearby = grid.getNearbyNodes(a);
        assertTrue(nearby.contains(a), "node must appear in its own neighbourhood");
    }

    // -----------------------------------------------------------------------
    // Nodes in same cell — all appear in neighbourhood
    // -----------------------------------------------------------------------

    @Test
    void getNearbyNodes_twoNodesInSameCell_bothVisible() {
        // (50,50) and (80,80) both land in cell (row=0, col=0) for 100-unit cells
        NodeData a = node("a", 50, 50);
        NodeData b = node("b", 80, 80);
        grid.rebuild(List.of(a, b), W, H);

        List<NodeData> nearA = grid.getNearbyNodes(a);
        assertTrue(nearA.contains(a));
        assertTrue(nearA.contains(b));
    }

    // -----------------------------------------------------------------------
    // Adjacent cells — Moore neighbourhood
    // -----------------------------------------------------------------------

    @Test
    void getNearbyNodes_adjacentCell_nodeIsVisible() {
        // a at (50,50) → cell(0,0); b at (150,50) → cell(0,1) — horizontally adjacent
        NodeData a = node("a", 50, 50);
        NodeData b = node("b", 150, 50);
        grid.rebuild(List.of(a, b), W, H);

        assertTrue(grid.getNearbyNodes(a).contains(b), "horizontally adjacent cell must be included");
        assertTrue(grid.getNearbyNodes(b).contains(a), "adjacency must be symmetric");
    }

    @Test
    void getNearbyNodes_diagonallyAdjacentCell_nodeIsVisible() {
        // a at (50,50) → cell(0,0); b at (150,150) → cell(1,1) — diagonally adjacent
        NodeData a = node("a", 50, 50);
        NodeData b = node("b", 150, 150);
        grid.rebuild(List.of(a, b), W, H);

        assertTrue(grid.getNearbyNodes(a).contains(b),
                "diagonally adjacent cell must be included in Moore neighbourhood");
    }

    @Test
    void getNearbyNodes_twoStepsAway_nodeIsNotVisible() {
        // a at (50,50) → cell(0,0); b at (250,50) → cell(0,2) — two columns away
        NodeData a = node("a", 50, 50);
        NodeData b = node("b", 250, 50);
        grid.rebuild(List.of(a, b), W, H);

        assertFalse(grid.getNearbyNodes(a).contains(b),
                "node two cells away must NOT appear in neighbourhood");
    }

    // -----------------------------------------------------------------------
    // Corner cells — neighbourhood does not wrap and does not throw
    // -----------------------------------------------------------------------

    @Test
    void getNearbyNodes_topLeftCornerNode_doesNotThrow() {
        NodeData a = node("a", 0, 0);
        grid.rebuild(List.of(a), W, H);
        assertDoesNotThrow(() -> grid.getNearbyNodes(a));
    }

    @Test
    void getNearbyNodes_topLeftCorner_returnsOnlyCellNeighbours() {
        // Only cells (0,0), (0,1), (1,0), (1,1) are valid; no wrapping
        NodeData a = node("a", 0, 0);      // cell(0,0)
        NodeData b = node("b", 150, 150);  // cell(1,1) — diagonally adjacent, visible
        NodeData c = node("c", 350, 0);    // cell(0,3) — same row, far right, not visible
        grid.rebuild(List.of(a, b, c), W, H);

        List<NodeData> near = grid.getNearbyNodes(a);
        assertTrue(near.contains(b));
        assertFalse(near.contains(c));
    }

    @Test
    void getNearbyNodes_bottomRightCornerNode_doesNotThrow() {
        NodeData a = node("a", 399, 399);
        grid.rebuild(List.of(a), W, H);
        assertDoesNotThrow(() -> grid.getNearbyNodes(a));
    }

    // -----------------------------------------------------------------------
    // Positions at canvas boundary — clamped, not lost
    // -----------------------------------------------------------------------

    @Test
    void rebuild_positionExactlyOnRightEdge_nodeClamped_notLost() {
        // x == canvasWidth would give col=gridSize without clamping → out of range
        NodeData a = node("a", W, H / 2);
        grid.rebuild(List.of(a), W, H);
        // Node must survive rebuild and be retrievable
        assertFalse(grid.getNearbyNodes(a).isEmpty(),
                "node at canvas right edge must be clamped and remain in the grid");
    }

    @Test
    void rebuild_positionBeyondCanvas_nodeClamped_notLost() {
        NodeData a = node("a", W + 500, H + 500);
        grid.rebuild(List.of(a), W, H);
        assertFalse(grid.getNearbyNodes(a).isEmpty(),
                "node beyond canvas must be clamped to nearest cell and retained");
    }

    @Test
    void rebuild_negativePosition_nodeClamped_notLost() {
        NodeData a = node("a", -100, -100);
        grid.rebuild(List.of(a), W, H);
        assertFalse(grid.getNearbyNodes(a).isEmpty(),
                "node with negative position must be clamped to cell(0,0)");
    }

    // -----------------------------------------------------------------------
    // rebuild() clears previous state
    // -----------------------------------------------------------------------

    @Test
    void rebuild_calledTwice_secondCallReplacesFirstIndex() {
        NodeData a = node("a", 50, 50);
        NodeData b = node("b", 350, 350);
        grid.rebuild(List.of(a), W, H);
        // Second rebuild with only b — a must no longer be findable
        grid.rebuild(List.of(b), W, H);

        assertEquals(Collections.emptyList(), grid.getNearbyNodes(a),
                "node from previous rebuild must not appear after second rebuild");
        assertTrue(grid.getNearbyNodes(b).contains(b));
    }

    @Test
    void rebuild_emptySecondCall_clearsAllNodes() {
        NodeData a = node("a", 50, 50);
        grid.rebuild(List.of(a), W, H);
        grid.rebuild(Collections.emptyList(), W, H);
        assertEquals(0, grid.occupiedCellCount());
    }

    // -----------------------------------------------------------------------
    // Neighbourhood size contract
    // -----------------------------------------------------------------------

    @Test
    void getNearbyNodes_resultIsUnmodifiable() {
        NodeData a = node("a", 50, 50);
        grid.rebuild(List.of(a), W, H);
        List<NodeData> nearby = grid.getNearbyNodes(a);
        assertThrows(UnsupportedOperationException.class, () -> nearby.add(a),
                "getNearbyNodes must return an unmodifiable list");
    }

    @Test
    void getNearbyNodes_allNearbyNodesBelongToNeighbourhoodCells() {
        // Scatter 16 nodes, one per cell in the 4×4 grid
        List<NodeData> nodes = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                double x = c * 100 + 50;  // centre of cell (r,c)
                double y = r * 100 + 50;
                nodes.add(node("n" + r + "_" + c, x, y));
            }
        }
        grid.rebuild(nodes, W, H);

        // Centre node at cell(1,1) must see nodes in cells (0..2) × (0..2) = 9 cells
        NodeData centre = nodeById(nodes, "n1_1");
        List<NodeData> nearby = grid.getNearbyNodes(centre);
        Set<String> nearbyIds = nearby.stream().map(NodeData::getNoteId).collect(Collectors.toSet());

        // Expected 9 nodes: rows 0-2, cols 0-2
        for (int r = 0; r <= 2; r++) {
            for (int c = 0; c <= 2; c++) {
                assertTrue(nearbyIds.contains("n" + r + "_" + c),
                        "n" + r + "_" + c + " must be in Moore neighbourhood of n1_1");
            }
        }
        // Row 3 nodes must NOT appear
        for (int c = 0; c < 4; c++) {
            assertFalse(nearbyIds.contains("n3_" + c),
                    "n3_" + c + " is outside Moore neighbourhood of n1_1");
        }
        // Col 3 nodes (rows 0-2) must NOT appear
        for (int r = 0; r <= 2; r++) {
            assertFalse(nearbyIds.contains("n" + r + "_3"),
                    "n" + r + "_3 is outside Moore neighbourhood of n1_1");
        }
    }

    @Test
    void getNearbyNodes_cornerNode_atMostFourCells() {
        // Corner cell (0,0) can touch at most 4 cells: (0,0),(0,1),(1,0),(1,1)
        List<NodeData> nodes = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                nodes.add(node("n" + r + "_" + c, c * 100 + 50, r * 100 + 50));
            }
        }
        grid.rebuild(nodes, W, H);

        NodeData corner = nodeById(nodes, "n0_0");
        List<NodeData> nearby = grid.getNearbyNodes(corner);
        // Must contain exactly 4 nodes (one per accessible cell)
        assertEquals(4, nearby.size(),
                "corner cell Moore neighbourhood spans only 4 cells → 4 nodes");
    }

    // -----------------------------------------------------------------------
    // Input list must not be modified
    // -----------------------------------------------------------------------

    @Test
    void rebuild_doesNotModifyInputList() {
        List<NodeData> input = new ArrayList<>();
        input.add(node("a", 50, 50));
        input.add(node("b", 150, 50));
        List<NodeData> snapshot = new ArrayList<>(input);
        grid.rebuild(input, W, H);
        assertEquals(snapshot, input, "rebuild must not modify the caller's list");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static NodeData node(String id, double x, double y) {
        return new NodeData(id, new Vector2D(x, y), "sector", List.of());
    }

    private static NodeData nodeById(List<NodeData> nodes, String id) {
        return nodes.stream()
                .filter(n -> n.getNoteId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("node not found: " + id));
    }
}
