package com.docgalaxy.layout;

import com.docgalaxy.ai.Neighbor;
import com.docgalaxy.model.Vector2D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RadialLayout}.
 *
 * <p>Canvas 400 × 400, MARGIN = 10 % → usableRadius = min(200,200) × 0.8 = 160.
 */
class RadialLayoutTest {

    private static final double W              = 400.0;
    private static final double H              = 400.0;
    private static final double DELTA          = 1e-9;
    private static final double USABLE_RADIUS  = 160.0;  // min(200,200)*(1-2*0.1)
    private static final double CX             = 200.0;  // canvas centre x
    private static final double CY             = 200.0;  // canvas centre y

    private RadialLayout layout;

    @BeforeEach
    void setUp() {
        layout = new RadialLayout(W, H);
    }

    // -----------------------------------------------------------------------
    // Constructor validation
    // -----------------------------------------------------------------------

    @Test
    void constructor_zeroCanvasWidth_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new RadialLayout(0, H));
    }

    @Test
    void constructor_negativeCanvasWidth_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new RadialLayout(-1, H));
    }

    @Test
    void constructor_zeroCanvasHeight_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new RadialLayout(W, 0));
    }

    @Test
    void constructor_negativeCanvasHeight_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new RadialLayout(W, -1));
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
        assertTrue(layout.calculate(Collections.emptyList()).isEmpty());
    }

    @Test
    void calculate_singleNode_returnsCanvasCentre() {
        Map<String, Vector2D> result = layout.calculate(List.of(node("a")));
        assertEquals(1, result.size());
        assertEquals(CX, result.get("a").getX(), DELTA);
        assertEquals(CY, result.get("a").getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // calculate() — output completeness
    // -----------------------------------------------------------------------

    @Test
    void calculate_allIdsPresent_twoNodes() {
        List<NodeData> nodes = List.of(nodeWithNeighbors("a", "b"), node("b"));
        Map<String, Vector2D> result = layout.calculate(nodes);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("a") && result.containsKey("b"));
    }

    @Test
    void calculate_allIdsPresent_fiveNodes() {
        List<NodeData> nodes = starGraph("hub", List.of("s1","s2","s3","s4"));
        Map<String, Vector2D> result = layout.calculate(nodes);
        assertEquals(5, result.size());
        for (NodeData nd : nodes) assertTrue(result.containsKey(nd.getNoteId()));
    }

    // -----------------------------------------------------------------------
    // calculate() — centre selection: most outgoing neighbours
    // -----------------------------------------------------------------------

    @Test
    void calculate_hubNodePlacedAtCanvasCentre() {
        // "hub" has 3 outgoing edges, others have 0
        List<NodeData> nodes = starGraph("hub", List.of("s1","s2","s3"));
        Map<String, Vector2D> result = layout.calculate(nodes);

        assertEquals(CX, result.get("hub").getX(), DELTA);
        assertEquals(CY, result.get("hub").getY(), DELTA);
    }

    @Test
    void calculate_higherDegreeNodeBecomesCenter_notFirstInList() {
        // "last" has 2 neighbors; "first" has 0 — even though "first" appears first
        NodeData first = node("first");
        NodeData last  = nodeWithNeighbors("last", "first", "mid");
        NodeData mid   = node("mid");
        Map<String, Vector2D> result = layout.calculate(List.of(first, mid, last));

        assertEquals(CX, result.get("last").getX(), DELTA,
                "node with most neighbours must be placed at canvas centre");
        assertEquals(CY, result.get("last").getY(), DELTA);
    }

    @Test
    void calculate_tieInOutDegree_higherTotalDegreePicked() {
        // a→x (out=1, in=0, total=1)
        // b→x (out=1, in=1 from p, total=2)  ← b should win
        // p→b (out=1, in=0, total=1)
        // x    (out=0, in=2, total=2)  but out=0 loses to out=1
        NodeData a = nodeWithNeighbors("a", "x");
        NodeData b = nodeWithNeighbors("b", "x");
        NodeData p = nodeWithNeighbors("p", "b"); // gives b an extra in-edge
        NodeData x = node("x");
        Map<String, Vector2D> result = layout.calculate(List.of(a, b, p, x));

        // primary: out-degree tied at 1 for a, b, p; secondary: total b=2 wins
        assertEquals(CX, result.get("b").getX(), DELTA,
                "tie in out-degree resolved by total degree");
        assertEquals(CY, result.get("b").getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // calculate() — BFS ring distances
    // -----------------------------------------------------------------------

    @Test
    void calculate_directNeighbour_placedOnRing1() {
        // hub→s1; s1 should be on ring 1 at distance = usableRadius from centre
        // maxRing=1, so ring1 radius = 1/1 * 160 = 160
        List<NodeData> nodes = starGraph("hub", List.of("s1"));
        Map<String, Vector2D> result = layout.calculate(nodes);

        double dist = result.get("s1").distanceTo(result.get("hub"));
        assertEquals(USABLE_RADIUS, dist, DELTA);
    }

    @Test
    void calculate_neighbourOfNeighbour_fartherThanDirectNeighbour() {
        // hub has out=2 (wins centre), ring1=s1+s2, ring2=t1 (s1's neighbour)
        NodeData hub = nodeWithNeighbors("hub", "s1", "s2");
        NodeData s1  = nodeWithNeighbors("s1",  "t1");
        NodeData s2  = node("s2");
        NodeData t1  = node("t1");
        Map<String, Vector2D> result = layout.calculate(List.of(hub, s1, s2, t1));

        double dS1 = result.get("s1").distanceTo(result.get("hub"));
        double dT1 = result.get("t1").distanceTo(result.get("hub"));
        assertTrue(dT1 > dS1,
                "ring-2 node must be farther from centre than ring-1 node");
    }

    @Test
    void calculate_threeRingChain_ringRadiiIncrease() {
        // hub(out=3) wins; hub→a(ring1)→b(ring2)→c(ring3); hub also has s1,s2 to ensure it wins
        NodeData hub = nodeWithNeighbors("hub", "a", "s1", "s2");
        NodeData a   = nodeWithNeighbors("a",   "b");
        NodeData b   = nodeWithNeighbors("b",   "c");
        NodeData c   = node("c");
        NodeData s1  = node("s1");
        NodeData s2  = node("s2");
        Map<String, Vector2D> result = layout.calculate(List.of(hub, a, b, c, s1, s2));

        double dA = result.get("a").distanceTo(result.get("hub"));
        double dB = result.get("b").distanceTo(result.get("hub"));
        double dC = result.get("c").distanceTo(result.get("hub"));
        assertTrue(dA < dB, "ring 1 closer than ring 2");
        assertTrue(dB < dC, "ring 2 closer than ring 3");
    }

    @Test
    void calculate_ring1_exactRadius_starGraph4Spokes() {
        // hub → s1,s2,s3,s4; maxRing=1 → ring1 radius = USABLE_RADIUS
        List<NodeData> nodes = starGraph("hub", List.of("s1","s2","s3","s4"));
        Map<String, Vector2D> result = layout.calculate(nodes);

        Vector2D hub = result.get("hub");
        for (String spoke : List.of("s1","s2","s3","s4")) {
            assertEquals(USABLE_RADIUS, result.get(spoke).distanceTo(hub), DELTA,
                    spoke + " must be exactly USABLE_RADIUS from hub");
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — angular distribution within a ring
    // -----------------------------------------------------------------------

    @Test
    void calculate_twoNodesOnSameRing_oppositeSidesOfCentre() {
        // hub → s1, s2 (both on ring 1); angles 0 and π → symmetric about hub
        NodeData hub = nodeWithNeighbors("hub", "s1", "s2");
        NodeData s1  = node("s1");
        NodeData s2  = node("s2");
        Map<String, Vector2D> result = layout.calculate(List.of(hub, s1, s2));

        Vector2D c = result.get("hub");
        double dx1 = result.get("s1").getX() - c.getX();
        double dy1 = result.get("s1").getY() - c.getY();
        double dx2 = result.get("s2").getX() - c.getX();
        double dy2 = result.get("s2").getY() - c.getY();

        // Vectors must be anti-parallel: dx1 ≈ -dx2, dy1 ≈ -dy2
        assertEquals(-dx1, dx2, DELTA, "two-node ring: x offsets anti-parallel");
        assertEquals(-dy1, dy2, DELTA, "two-node ring: y offsets anti-parallel");
    }

    @Test
    void calculate_fourNodesOnSameRing_equallySpaced90Degrees() {
        // hub → s1,s2,s3,s4; 4 nodes at angles 0, π/2, π, 3π/2
        List<NodeData> nodes = starGraph("hub", List.of("s1","s2","s3","s4"));
        Map<String, Vector2D> result = layout.calculate(nodes);

        Vector2D c = result.get("hub");
        // All four are at the same radius (verified above); check pairwise angles
        // Dot product of adjacent vectors = cos(90°) = 0
        List<String> spokes = List.of("s1","s2","s3","s4");
        double[] dx = new double[4], dy = new double[4];
        for (int i = 0; i < 4; i++) {
            Vector2D v = result.get(spokes.get(i));
            dx[i] = v.getX() - c.getX();
            dy[i] = v.getY() - c.getY();
        }
        // Adjacent pairs
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            double dot = dx[i]*dx[j] + dy[i]*dy[j];
            assertEquals(0.0, dot, 1e-9, "adjacent spoke vectors must be orthogonal");
        }
    }

    @Test
    void calculate_allNodesOnSameRingEquidistant() {
        // All at ring 1; each must be exactly USABLE_RADIUS from centre
        NodeData hub = nodeWithNeighbors("hub", "s1","s2","s3");
        Map<String, Vector2D> result = layout.calculate(
                List.of(hub, node("s1"), node("s2"), node("s3")));

        Vector2D c = result.get("hub");
        for (String s : List.of("s1","s2","s3")) {
            assertEquals(USABLE_RADIUS, result.get(s).distanceTo(c), DELTA);
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — disconnected nodes
    // -----------------------------------------------------------------------

    @Test
    void calculate_disconnectedNode_presentInResult() {
        // "isolated" has no path from hub
        NodeData hub      = nodeWithNeighbors("hub", "s1");
        NodeData s1       = node("s1");
        NodeData isolated = node("isolated");
        Map<String, Vector2D> result = layout.calculate(List.of(hub, s1, isolated));

        assertTrue(result.containsKey("isolated"),
                "disconnected node must still appear in result");
    }

    @Test
    void calculate_disconnectedNode_fartherThanReachableNodes() {
        // hub → s1 (ring 1); isolated at overflow ring (ring 2)
        NodeData hub      = nodeWithNeighbors("hub", "s1");
        NodeData s1       = node("s1");
        NodeData isolated = node("isolated");
        Map<String, Vector2D> result = layout.calculate(List.of(hub, s1, isolated));

        double d_s1      = result.get("s1")     .distanceTo(result.get("hub"));
        double d_iso     = result.get("isolated").distanceTo(result.get("hub"));
        assertTrue(d_iso > d_s1,
                "disconnected node must be on an outer ring beyond reachable nodes");
    }

    @Test
    void calculate_multipleDisconnectedNodes_allSameRingRadius() {
        // iso1 and iso2 are both unreachable from hub
        NodeData hub  = nodeWithNeighbors("hub", "s1");
        Map<String, Vector2D> result = layout.calculate(
                List.of(hub, node("s1"), node("iso1"), node("iso2")));

        double d1 = result.get("iso1").distanceTo(result.get("hub"));
        double d2 = result.get("iso2").distanceTo(result.get("hub"));
        assertEquals(d1, d2, DELTA,
                "all disconnected nodes must be on the same overflow ring");
    }

    // -----------------------------------------------------------------------
    // calculate() — no edges (all nodes disconnected)
    // -----------------------------------------------------------------------

    @Test
    void calculate_noEdges_allNodesAtSameRadiusExceptCenter() {
        // No neighbours → BFS only places hub at ring 0; all others overflow to ring 1
        List<NodeData> nodes = List.of(node("a"), node("b"), node("c"), node("d"));
        Map<String, Vector2D> result = layout.calculate(nodes);

        // One node must be at canvas centre
        long atCentre = result.values().stream()
                .filter(v -> Math.abs(v.getX() - CX) < DELTA && Math.abs(v.getY() - CY) < DELTA)
                .count();
        assertEquals(1, atCentre, "exactly one node must be at canvas centre");

        // All others must be at USABLE_RADIUS
        String centreId = result.entrySet().stream()
                .filter(e -> Math.abs(e.getValue().getX()-CX)<DELTA && Math.abs(e.getValue().getY()-CY)<DELTA)
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        for (Map.Entry<String, Vector2D> e : result.entrySet()) {
            if (!e.getKey().equals(centreId)) {
                assertEquals(USABLE_RADIUS, e.getValue().distanceTo(result.get(centreId)), DELTA,
                        e.getKey() + " must be at overflow ring radius");
            }
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — result contract
    // -----------------------------------------------------------------------

    @Test
    void calculate_resultIsUnmodifiable() {
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("a"), node("b")));
        assertThrows(UnsupportedOperationException.class,
                () -> result.put("x", Vector2D.ZERO));
    }

    @Test
    void calculate_doesNotModifyInputList() {
        List<NodeData> input = new ArrayList<>(
                List.of(nodeWithNeighbors("hub","s1","s2"), node("s1"), node("s2")));
        List<NodeData> snapshot = new ArrayList<>(input);
        layout.calculate(input);
        assertEquals(snapshot.size(), input.size());
        for (int i = 0; i < input.size(); i++) assertSame(snapshot.get(i), input.get(i));
    }

    @Test
    void calculate_noNanOrInfinity() {
        List<NodeData> nodes = starGraph("hub", List.of("s1","s2","s3","s4"));
        Map<String, Vector2D> result = layout.calculate(nodes);
        for (Vector2D v : result.values()) {
            assertFalse(Double.isNaN(v.getX()) || Double.isNaN(v.getY()));
            assertFalse(Double.isInfinite(v.getX()) || Double.isInfinite(v.getY()));
        }
    }

    @Test
    void calculate_calledTwice_sameInstance_bothSucceed() {
        List<NodeData> nodes = starGraph("hub", List.of("s1","s2"));
        Map<String, Vector2D> r1 = layout.calculate(nodes);
        Map<String, Vector2D> r2 = layout.calculate(nodes);
        assertEquals(r1.get("hub").getX(), r2.get("hub").getX(), DELTA);
    }

    @Test
    void calculate_deterministic_sameSeed_sameResult() {
        List<NodeData> nodes = starGraph("hub", List.of("s1","s2","s3"));
        Map<String, Vector2D> r1 = new RadialLayout(W, H).calculate(nodes);
        Map<String, Vector2D> r2 = new RadialLayout(W, H).calculate(nodes);
        for (NodeData nd : nodes) {
            assertEquals(r1.get(nd.getNoteId()).getX(),
                         r2.get(nd.getNoteId()).getX(), DELTA);
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — exact radius values for two-ring layout
    // -----------------------------------------------------------------------

    @Test
    void calculate_singleSpoke_exactPosition_atAngleZero() {
        // hub→s1 only; ring 1 has 1 node; angle = 2π×0/1 = 0
        // expected: s1 at (CX + USABLE_RADIUS, CY)
        NodeData hub = nodeWithNeighbors("hub", "s1");
        Map<String, Vector2D> result = layout.calculate(List.of(hub, node("s1")));

        assertEquals(CX + USABLE_RADIUS, result.get("s1").getX(), DELTA,
                "single spoke at angle 0 must be at (CX + USABLE_RADIUS, CY)");
        assertEquals(CY, result.get("s1").getY(), DELTA);
    }

    @Test
    void calculate_twoRing_ring1ExactRadius() {
        // hub(out=2)→s1,s2; s1→t1; maxRing=2 → ring1 radius = 1/2 * USABLE_RADIUS = 80
        NodeData hub = nodeWithNeighbors("hub", "s1", "s2");
        NodeData s1  = nodeWithNeighbors("s1",  "t1");
        NodeData s2  = node("s2");
        NodeData t1  = node("t1");
        Map<String, Vector2D> result = layout.calculate(List.of(hub, s1, s2, t1));

        double ring1Radius = USABLE_RADIUS / 2.0;   // 80
        Vector2D c = result.get("hub");
        assertEquals(ring1Radius, result.get("s1").distanceTo(c), DELTA, "ring-1 radius must be USABLE_RADIUS/2");
        assertEquals(ring1Radius, result.get("s2").distanceTo(c), DELTA, "s2 also on ring 1");
    }

    @Test
    void calculate_twoRing_ring2ExactRadius() {
        // same graph as above; ring2 radius = 2/2 * USABLE_RADIUS = 160
        NodeData hub = nodeWithNeighbors("hub", "s1", "s2");
        NodeData s1  = nodeWithNeighbors("s1",  "t1");
        NodeData s2  = node("s2");
        NodeData t1  = node("t1");
        Map<String, Vector2D> result = layout.calculate(List.of(hub, s1, s2, t1));

        assertEquals(USABLE_RADIUS, result.get("t1").distanceTo(result.get("hub")), DELTA,
                "ring-2 radius must equal USABLE_RADIUS");
    }

    // -----------------------------------------------------------------------
    // calculate() — non-square canvas uses min dimension
    // -----------------------------------------------------------------------

    @Test
    void calculate_nonSquareCanvas_usesMinDimension() {
        // 600×400: CX=300, CY=200; min(300,200)=200; usableRadius=200*0.8=160
        RadialLayout wide = new RadialLayout(600.0, 400.0);
        NodeData hub = nodeWithNeighbors("hub", "s1");
        Map<String, Vector2D> result = wide.calculate(List.of(hub, node("s1")));

        double expectedRadius = Math.min(600.0, 400.0) / 2.0 * (1.0 - 2.0 * 0.10);  // 160
        double actualDist = result.get("s1").distanceTo(result.get("hub"));
        assertEquals(expectedRadius, actualDist, DELTA,
                "non-square canvas: usable radius uses the shorter dimension");
    }

    // -----------------------------------------------------------------------
    // calculate() — mutual edges do not double-count ring depth
    // -----------------------------------------------------------------------

    @Test
    void calculate_mutualEdges_noDuplication_bOnRing1Only() {
        // A→B and B→A; A (first in list, wins tiebreak) is centre (ring 0)
        // BFS: B assigned ring 1 via A→B; B's back-edge B→A does not reassign A or push B further
        NodeData a = nodeWithNeighbors("a", "b");
        NodeData b = nodeWithNeighbors("b", "a");
        Map<String, Vector2D> result = layout.calculate(List.of(a, b));

        assertEquals(CX, result.get("a").getX(), DELTA, "a must be at canvas centre");
        assertEquals(CY, result.get("a").getY(), DELTA);
        // b must be at ring-1 radius = USABLE_RADIUS (maxRing=1, r=1)
        assertEquals(USABLE_RADIUS, result.get("b").distanceTo(result.get("a")), DELTA,
                "b must be on ring 1 — mutual edge must not create ring 2");
    }

    // -----------------------------------------------------------------------
    // calculate() — neighbour id not in node list is silently ignored
    // -----------------------------------------------------------------------

    @Test
    void calculate_neighbourIdNotInNodeList_skipped_noException() {
        // hub has a KNN edge to "ghost" which is not in the input list
        NodeData hub = nodeWithNeighbors("hub", "ghost", "s1");
        NodeData s1  = node("s1");
        Map<String, Vector2D> result = layout.calculate(List.of(hub, s1));

        assertEquals(2, result.size(), "result must contain only the 2 nodes in the input list");
        assertFalse(result.containsKey("ghost"), "'ghost' node must not appear in result");
    }

    // -----------------------------------------------------------------------
    // calculate() — first-in-list wins when out-degree and total degree are equal
    // -----------------------------------------------------------------------

    @Test
    void calculate_firstInListWins_equalOutAndTotalDegree() {
        // All three isolated nodes: out=0, in=0, total=0 — first in list becomes centre
        NodeData first  = node("first");
        NodeData second = node("second");
        NodeData third  = node("third");
        Map<String, Vector2D> result = layout.calculate(List.of(first, second, third));

        assertEquals(CX, result.get("first").getX(), DELTA,
                "when all nodes have equal out+total degree, first in list must become centre");
        assertEquals(CY, result.get("first").getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // isIterative() / getName()
    // -----------------------------------------------------------------------

    @Test
    void isIterative_returnsFalse() {
        assertFalse(layout.isIterative());
    }

    @Test
    void getName_returnsRadial() {
        assertEquals("Radial", layout.getName());
    }

    // -----------------------------------------------------------------------
    // Default constructor smoke test
    // -----------------------------------------------------------------------

    @Test
    void defaultConstructor_smokeTest_noException() {
        RadialLayout def = new RadialLayout();
        Map<String, Vector2D> result = def.calculate(
                starGraph("hub", List.of("s1","s2")));
        assertEquals(3, result.size());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static NodeData node(String id) {
        return new NodeData(id, new Vector2D(0, 0), "sector", Collections.emptyList());
    }

    /** Node with outgoing edges to the given neighbour ids (similarity 0.9). */
    private static NodeData nodeWithNeighbors(String id, String... neighborIds) {
        List<Neighbor> neighbors = new ArrayList<>();
        for (String nid : neighborIds) neighbors.add(new Neighbor(nid, 0.9));
        return new NodeData(id, new Vector2D(0, 0), "sector", neighbors);
    }

    /**
     * Star graph: hub has outgoing edges to all spokes; spokes have no edges.
     * Returned list starts with the hub node.
     */
    private static List<NodeData> starGraph(String hubId, List<String> spokeIds) {
        List<NodeData> nodes = new ArrayList<>();
        List<Neighbor> hubNeighbors = new ArrayList<>();
        for (String sid : spokeIds) hubNeighbors.add(new Neighbor(sid, 0.9));
        nodes.add(new NodeData(hubId, new Vector2D(0, 0), "sector", hubNeighbors));
        for (String sid : spokeIds) nodes.add(node(sid));
        return nodes;
    }
}
