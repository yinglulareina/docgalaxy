package com.docgalaxy.layout;

import com.docgalaxy.ai.Neighbor;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.util.AppConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ForceDirectedLayout}.
 *
 * <p>All tests use a 400×400 canvas and small node lists (≤ 6 nodes) so that
 * even 300 FR iterations complete in milliseconds.
 */
class ForceDirectedLayoutTest {

    private static final double W = 400.0;
    private static final double H = 400.0;
    private static final double DELTA = 1e-9;

    /** Single-pass OverlapResolver to make overlap post-processing deterministic. */
    private ForceDirectedLayout layout;

    @BeforeEach
    void setUp() {
        layout = new ForceDirectedLayout(W, H, new OverlapResolver(1));
    }

    // -----------------------------------------------------------------------
    // Constructor validation
    // -----------------------------------------------------------------------

    @Test
    void constructor_zeroCanvasWidth_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ForceDirectedLayout(0, H));
    }

    @Test
    void constructor_zeroCanvasHeight_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ForceDirectedLayout(W, 0));
    }

    @Test
    void constructor_negativeCanvasWidth_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ForceDirectedLayout(-1, H));
    }

    // -----------------------------------------------------------------------
    // calculate() — argument validation
    // -----------------------------------------------------------------------

    @Test
    void calculate_nullNodes_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> layout.calculate(null));
    }

    // -----------------------------------------------------------------------
    // calculate() — trivial cases
    // -----------------------------------------------------------------------

    @Test
    void calculate_emptyList_returnsEmptyMap() {
        Map<String, Vector2D> result = layout.calculate(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    void calculate_singleNode_returnsCanvasCentre() {
        NodeData nd = node("a", 0, 0);
        Map<String, Vector2D> result = layout.calculate(List.of(nd));

        assertEquals(1, result.size());
        assertEquals(W / 2, result.get("a").getX(), DELTA);
        assertEquals(H / 2, result.get("a").getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // calculate() — output completeness
    // -----------------------------------------------------------------------

    @Test
    void calculate_twoNodes_bothIdsInResult() {
        List<NodeData> nodes = List.of(node("a", 0, 0), node("b", 1, 0));
        Map<String, Vector2D> result = layout.calculate(nodes);

        assertTrue(result.containsKey("a"), "result must contain id 'a'");
        assertTrue(result.containsKey("b"), "result must contain id 'b'");
        assertEquals(2, result.size());
    }

    @Test
    void calculate_fiveNodes_allIdsPresent() {
        List<NodeData> nodes = fiveNode();
        Map<String, Vector2D> result = layout.calculate(nodes);

        assertEquals(5, result.size());
        for (NodeData nd : nodes) {
            assertTrue(result.containsKey(nd.getNoteId()),
                    "missing id: " + nd.getNoteId());
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — no NaN or Infinity
    // -----------------------------------------------------------------------

    @Test
    void calculate_twoNodes_noNanOrInfinity() {
        List<NodeData> nodes = List.of(node("a", 0, 0), node("b", 1, 0));
        Map<String, Vector2D> result = layout.calculate(nodes);

        for (Vector2D v : result.values()) {
            assertFalse(Double.isNaN(v.getX()),      "x must not be NaN");
            assertFalse(Double.isNaN(v.getY()),      "y must not be NaN");
            assertFalse(Double.isInfinite(v.getX()), "x must not be Infinite");
            assertFalse(Double.isInfinite(v.getY()), "y must not be Infinite");
        }
    }

    @Test
    void calculate_coincidentPcaPositions_noNanOrInfinity() {
        // All nodes at same PCA position → tests degenerate range handling
        List<NodeData> nodes = List.of(
                node("a", 5, 5),
                node("b", 5, 5),
                node("c", 5, 5));
        Map<String, Vector2D> result = layout.calculate(nodes);

        for (Vector2D v : result.values()) {
            assertFalse(Double.isNaN(v.getX()));
            assertFalse(Double.isNaN(v.getY()));
            assertFalse(Double.isInfinite(v.getX()));
            assertFalse(Double.isInfinite(v.getY()));
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — result is unmodifiable
    // -----------------------------------------------------------------------

    @Test
    void calculate_resultIsUnmodifiable() {
        Map<String, Vector2D> result = layout.calculate(List.of(node("a", 0, 0),
                                                                  node("b", 1, 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> result.put("new", Vector2D.ZERO));
    }

    // -----------------------------------------------------------------------
    // calculate() — repulsion separates coincident/overlapping nodes
    // -----------------------------------------------------------------------

    @Test
    void calculate_twoCoincidentNodes_endsUpSeparated() {
        List<NodeData> nodes = List.of(node("a", 0, 0), node("b", 0, 0));
        Map<String, Vector2D> result = layout.calculate(nodes);

        double dist = result.get("a").distanceTo(result.get("b"));
        assertTrue(dist > 0, "coincident nodes must be separated by repulsion");
    }

    @Test
    void calculate_twoNodes_finalDistanceAboveMinDist() {
        // After OverlapResolver, no pair should be closer than 2*radius + PAD
        List<NodeData> nodes = List.of(node("a", 0, 0), node("b", 0, 0));
        Map<String, Vector2D> result = layout.calculate(nodes);

        double minDist = 2 * ForceDirectedLayout.DEFAULT_NODE_RADIUS
                       + AppConstants.OVERLAP_PADDING;
        double dist    = result.get("a").distanceTo(result.get("b"));
        assertTrue(dist >= minDist - 1e-6,
                "OverlapResolver must push nodes to at least minDist " + minDist
                + "; actual dist=" + dist);
    }

    // -----------------------------------------------------------------------
    // calculate() — gravity keeps nodes near canvas centre
    // -----------------------------------------------------------------------

    @Test
    void calculate_nodesFarFromOrigin_gravityBringsThemOntoCanvas() {
        // PCA positions far outside canvas; gravity must bring them back
        List<NodeData> nodes = List.of(
                node("a", 1e6, 1e6),
                node("b", -1e6, -1e6));
        Map<String, Vector2D> result = layout.calculate(nodes);

        // After layout, positions should be within a reasonable distance of canvas
        double maxAllowedDist = Math.hypot(W, H) * 2;
        Vector2D centre = new Vector2D(W / 2, H / 2);
        for (Vector2D v : result.values()) {
            assertTrue(v.distanceTo(centre) < maxAllowedDist,
                    "nodes must not drift arbitrarily far from canvas centre");
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — semantic attraction pulls similar nodes together
    // -----------------------------------------------------------------------

    @Test
    void calculate_highSimilarityEdge_nodesCloserThanLowSimilarity() {
        // Run two separate layouts: one with high-sim KNN, one with zero-sim KNN.
        // High-sim nodes should end up closer together (semantic spring pulls them).
        NodeData aHigh = nodeWithNeighbor("a", 0,   0, "b", 0.99);
        NodeData bHigh = nodeWithNeighbor("b", 1, 0, "a", 0.99);

        NodeData aLow  = nodeWithNeighbor("a", 0,   0, "b", 0.01);
        NodeData bLow  = nodeWithNeighbor("b", 1, 0, "a", 0.01);

        Map<String, Vector2D> rHigh = new ForceDirectedLayout(W, H, new OverlapResolver(1))
                .calculate(List.of(aHigh, bHigh));
        Map<String, Vector2D> rLow  = new ForceDirectedLayout(W, H, new OverlapResolver(1))
                .calculate(List.of(aLow, bLow));

        double distHigh = rHigh.get("a").distanceTo(rHigh.get("b"));
        double distLow  = rLow .get("a").distanceTo(rLow .get("b"));

        assertTrue(distHigh <= distLow,
                "high-similarity pair must end up <= distance of low-similarity pair;"
                + " distHigh=" + distHigh + " distLow=" + distLow);
    }

    // -----------------------------------------------------------------------
    // calculate() — does not modify input list
    // -----------------------------------------------------------------------

    @Test
    void calculate_doesNotModifyInputList() {
        List<NodeData> input = new ArrayList<>(fiveNode());
        List<NodeData> snapshot = new ArrayList<>(input);
        layout.calculate(input);
        assertEquals(snapshot.size(), input.size());
        for (int i = 0; i < input.size(); i++) {
            assertSame(snapshot.get(i), input.get(i),
                    "input list element must not be replaced");
        }
    }

    // -----------------------------------------------------------------------
    // isIterative() / getName()
    // -----------------------------------------------------------------------

    @Test
    void isIterative_returnsTrue() {
        assertTrue(layout.isIterative());
    }

    @Test
    void getName_returnsGalaxy() {
        assertEquals("Galaxy", layout.getName());
    }

    // -----------------------------------------------------------------------
    // Default constructor — smoke test
    // -----------------------------------------------------------------------

    @Test
    void defaultConstructor_smokeTest_twoNodes() {
        ForceDirectedLayout defaultLayout = new ForceDirectedLayout();
        Map<String, Vector2D> result = defaultLayout.calculate(
                List.of(node("x", 0, 0), node("y", 1, 0)));
        assertEquals(2, result.size());
    }

    // -----------------------------------------------------------------------
    // Neighbours pointing to unknown ids — no exception
    // -----------------------------------------------------------------------

    @Test
    void calculate_neighborIdNotInNodeList_noException() {
        // Neighbor "z" is not in the node list — must be silently skipped
        NodeData nd = nodeWithNeighbor("a", 0, 0, "z", 0.8);
        assertDoesNotThrow(() -> layout.calculate(List.of(nd, node("b", 1, 0))));
    }

    // -----------------------------------------------------------------------
    // Constructor validation — negative canvas height (missing from existing tests)
    // -----------------------------------------------------------------------

    @Test
    void constructor_negativeCanvasHeight_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ForceDirectedLayout(W, -1));
    }

    // -----------------------------------------------------------------------
    // calculate() — instance reuse (called twice)
    // -----------------------------------------------------------------------

    @Test
    void calculate_calledTwice_sameInstance_bothCallsSucceed() {
        List<NodeData> nodes = List.of(node("a", 0, 0), node("b", 1, 0));
        Map<String, Vector2D> r1 = layout.calculate(nodes);
        Map<String, Vector2D> r2 = layout.calculate(nodes);
        assertEquals(2, r1.size());
        assertEquals(2, r2.size());
    }

    @Test
    void calculate_calledTwice_secondCallResultIsIndependentOfFirst() {
        List<NodeData> nodes = List.of(node("a", 0, 0), node("b", 1, 0));
        layout.calculate(nodes); // first call — must not corrupt internal state
        Map<String, Vector2D> r2 = layout.calculate(nodes);
        assertTrue(r2.containsKey("a"));
        assertTrue(r2.containsKey("b"));
    }

    // -----------------------------------------------------------------------
    // calculate() — no KNN edges (pure gravity + repulsion)
    // -----------------------------------------------------------------------

    @Test
    void calculate_noNeighbors_fiveNodes_noException() {
        List<NodeData> nodes = List.of(
                node("a", 0, 0), node("b", 1, 0), node("c", 0, 1),
                node("d", 1, 1), node("e", 0.5, 0.5));
        assertDoesNotThrow(() -> layout.calculate(nodes));
    }

    @Test
    void calculate_noNeighbors_fiveNodes_allIdsPresent() {
        List<NodeData> nodes = List.of(
                node("a", 0, 0), node("b", 1, 0), node("c", 0, 1),
                node("d", 1, 1), node("e", 0.5, 0.5));
        Map<String, Vector2D> result = layout.calculate(nodes);
        assertEquals(5, result.size());
        for (NodeData nd : nodes) assertTrue(result.containsKey(nd.getNoteId()));
    }

    @Test
    void calculate_noNeighbors_fiveNodes_noNanOrInfinity() {
        List<NodeData> nodes = List.of(
                node("a", 0, 0), node("b", 1, 0), node("c", 0, 1),
                node("d", 1, 1), node("e", 0.5, 0.5));
        Map<String, Vector2D> result = layout.calculate(nodes);
        for (Vector2D v : result.values()) {
            assertFalse(Double.isNaN(v.getX()) || Double.isNaN(v.getY()));
            assertFalse(Double.isInfinite(v.getX()) || Double.isInfinite(v.getY()));
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — multiple KNN edges per node
    // -----------------------------------------------------------------------

    @Test
    void calculate_multipleNeighborsPerNode_noException() {
        // Each node has 3 KNN edges
        List<NodeData> nodes = List.of(
                new NodeData("n0", new Vector2D(0, 0), "s",
                        List.of(new Neighbor("n1", 0.9), new Neighbor("n2", 0.7), new Neighbor("n3", 0.5))),
                new NodeData("n1", new Vector2D(1, 0), "s",
                        List.of(new Neighbor("n0", 0.9), new Neighbor("n2", 0.8), new Neighbor("n3", 0.4))),
                new NodeData("n2", new Vector2D(0, 1), "s",
                        List.of(new Neighbor("n0", 0.7), new Neighbor("n1", 0.8), new Neighbor("n3", 0.6))),
                new NodeData("n3", new Vector2D(1, 1), "s",
                        List.of(new Neighbor("n0", 0.5), new Neighbor("n1", 0.4), new Neighbor("n2", 0.6))));
        Map<String, Vector2D> result = layout.calculate(nodes);
        assertEquals(4, result.size());
        for (Vector2D v : result.values()) {
            assertFalse(Double.isNaN(v.getX()) || Double.isNaN(v.getY()),
                    "position must not be NaN with multiple KNN edges");
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — positions within reasonable bounds
    // -----------------------------------------------------------------------

    @Test
    void calculate_finalPositions_withinExpandedCanvasBounds() {
        // Gravity should prevent nodes drifting more than 2× canvas away from centre
        List<NodeData> nodes = fiveNode();
        Map<String, Vector2D> result = layout.calculate(nodes);

        double maxX = W * 2, maxY = H * 2;
        for (Map.Entry<String, Vector2D> e : result.entrySet()) {
            assertTrue(e.getValue().getX() > -maxX && e.getValue().getX() < maxX,
                    e.getKey() + " x=" + e.getValue().getX() + " is out of expanded bounds");
            assertTrue(e.getValue().getY() > -maxY && e.getValue().getY() < maxY,
                    e.getKey() + " y=" + e.getValue().getY() + " is out of expanded bounds");
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — structural symmetry
    // -----------------------------------------------------------------------

    @Test
    void calculate_symmetricInput_xCoordinatesSumToCanvasWidth() {
        // "a" at PCA (-1,0), "b" at PCA (1,0): symmetric about x=0
        // After scaling: both mapped to y=H/2; x-positions symmetric about W/2
        // Gravity + no-edges → both converge toward W/2; OverlapResolver separates symmetrically
        NodeData a = node("a", -1, 0);
        NodeData b = node("b",  1, 0);
        Map<String, Vector2D> result = layout.calculate(List.of(a, b));

        double sumX = result.get("a").getX() + result.get("b").getX();
        assertEquals(W, sumX, 1.0, "symmetric input must produce x-positions summing to canvas width");
    }

    @Test
    void calculate_symmetricInput_yCoordinatesEqual() {
        // Same setup — both nodes start at y = H/2, gravity keeps them there
        NodeData a = node("a", -1, 0);
        NodeData b = node("b",  1, 0);
        Map<String, Vector2D> result = layout.calculate(List.of(a, b));

        assertEquals(result.get("a").getY(), result.get("b").getY(), 1.0,
                "symmetric input must produce equal y-coordinates");
    }

    // -----------------------------------------------------------------------
    // calculate() — all pairs separated after OverlapResolver (default 3-pass)
    // -----------------------------------------------------------------------

    @Test
    void calculate_fiveNodes_allPairsAtLeastMinDist_withDefaultResolver() {
        // Use the default 3-pass OverlapResolver for this test
        ForceDirectedLayout defaultLayout = new ForceDirectedLayout(W, H);
        List<NodeData> nodes = fiveNode();
        Map<String, Vector2D> result = defaultLayout.calculate(nodes);

        double minDist = 2 * ForceDirectedLayout.DEFAULT_NODE_RADIUS
                       + AppConstants.OVERLAP_PADDING;
        List<String> ids = nodes.stream().map(NodeData::getNoteId).toList();
        for (int i = 0; i < ids.size() - 1; i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                double d = result.get(ids.get(i)).distanceTo(result.get(ids.get(j)));
                assertTrue(d >= minDist - 1e-6,
                        ids.get(i) + "–" + ids.get(j) + " dist=" + d
                        + " < minDist=" + minDist);
            }
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — degenerate PCA ranges
    // -----------------------------------------------------------------------

    @Test
    void calculate_allSameXCoordinate_noNanOrInfinity() {
        // All nodes at same x → rangeX degenerate; only rangeY varies
        List<NodeData> nodes = List.of(
                node("a", 5, 0), node("b", 5, 1), node("c", 5, 2));
        Map<String, Vector2D> result = layout.calculate(nodes);
        for (Vector2D v : result.values()) {
            assertFalse(Double.isNaN(v.getX()) || Double.isNaN(v.getY()));
        }
    }

    @Test
    void calculate_allSameYCoordinate_noNanOrInfinity() {
        // All nodes at same y → rangeY degenerate; only rangeX varies
        List<NodeData> nodes = List.of(
                node("a", 0, 7), node("b", 1, 7), node("c", 2, 7));
        Map<String, Vector2D> result = layout.calculate(nodes);
        for (Vector2D v : result.values()) {
            assertFalse(Double.isNaN(v.getX()) || Double.isNaN(v.getY()));
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — stress test (20 nodes)
    // -----------------------------------------------------------------------

    @Test
    void calculate_20Nodes_noExceptionAndAllIdsPresent() {
        List<NodeData> nodes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            double x = (i % 5) * 0.25;
            double y = (i / 5) * 0.25;
            nodes.add(node("n" + i, x, y));
        }
        Map<String, Vector2D> result = layout.calculate(nodes);
        assertEquals(20, result.size());
        for (NodeData nd : nodes) assertTrue(result.containsKey(nd.getNoteId()));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static NodeData node(String id, double x, double y) {
        return new NodeData(id, new Vector2D(x, y), "sector", Collections.emptyList());
    }

    private static NodeData nodeWithNeighbor(String id, double x, double y,
                                              String neighborId, double sim) {
        return new NodeData(id, new Vector2D(x, y), "sector",
                List.of(new Neighbor(neighborId, sim)));
    }

    /** Five-node list spread across a small area, with one KNN edge each. */
    private static List<NodeData> fiveNode() {
        return List.of(
                nodeWithNeighbor("n0", 0.0, 0.0, "n1", 0.9),
                nodeWithNeighbor("n1", 1.0, 0.0, "n0", 0.9),
                nodeWithNeighbor("n2", 0.0, 1.0, "n3", 0.5),
                nodeWithNeighbor("n3", 1.0, 1.0, "n2", 0.5),
                node("n4", 0.5, 0.5));
    }
}
