package com.docgalaxy.layout;

import com.docgalaxy.model.DendrogramNode;
import com.docgalaxy.model.Vector2D;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TreeLayout}.
 *
 * <p>Canvas: 400 × 400. MARGIN = 10 % → margin = 40, usableW = usableH = 320.
 *
 * <h3>Helper trees used in tests</h3>
 * <pre>
 * twoLeaf (balanced, depth 1):
 *       root
 *      /    \
 *     L0    L1
 *
 * threeLeaf (right-skewed, depth 2):
 *       root
 *      /    \
 *     L0    int
 *           /  \
 *          L1  L2
 *
 * balancedFour (depth 2):
 *         root
 *        /    \
 *      int0  int1
 *      /  \  /  \
 *     L0  L1 L2  L3
 * </pre>
 */
class TreeLayoutTest {

    private static final double W      = 400.0;
    private static final double H      = 400.0;
    private static final double DELTA  = 1e-9;
    private static final double MARGIN = 40.0;   // 10 % of 400
    private static final double USABLE = 320.0;  // 400 - 2*40

    // -----------------------------------------------------------------------
    // Constructor validation
    // -----------------------------------------------------------------------

    @Test
    void constructor_zeroCanvasWidth_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TreeLayout(null, 0, H));
    }

    @Test
    void constructor_negativeCanvasWidth_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TreeLayout(null, -1, H));
    }

    @Test
    void constructor_zeroCanvasHeight_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TreeLayout(null, W, 0));
    }

    @Test
    void constructor_negativeCanvasHeight_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TreeLayout(null, W, -1));
    }

    @Test
    void constructor_nullRoot_doesNotThrow() {
        assertDoesNotThrow(() -> new TreeLayout(null, W, H));
    }

    // -----------------------------------------------------------------------
    // calculate() — argument validation
    // -----------------------------------------------------------------------

    @Test
    void calculate_nullNodes_throwsIllegalArgumentException() {
        TreeLayout layout = new TreeLayout(twoLeafTree(), W, H);
        assertThrows(IllegalArgumentException.class, () -> layout.calculate(null));
    }

    // -----------------------------------------------------------------------
    // calculate() — trivial inputs
    // -----------------------------------------------------------------------

    @Test
    void calculate_emptyList_returnsEmptyMap() {
        TreeLayout layout = new TreeLayout(twoLeafTree(), W, H);
        assertTrue(layout.calculate(Collections.emptyList()).isEmpty());
    }

    @Test
    void calculate_singleNode_returnsCanvasCentre() {
        TreeLayout layout = new TreeLayout(twoLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(List.of(node("x", 0, 0)));
        assertEquals(1, result.size());
        assertEquals(W / 2, result.get("x").getX(), DELTA);
        assertEquals(H / 2, result.get("x").getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // calculate() — null root fallback
    // -----------------------------------------------------------------------

    @Test
    void calculate_nullRoot_allNodesAtCanvasCentre() {
        TreeLayout layout = new TreeLayout(null, W, H);
        List<NodeData> nodes = List.of(node("a", 0, 0), node("b", 0, 0), node("c", 0, 0));
        Map<String, Vector2D> result = layout.calculate(nodes);

        assertEquals(3, result.size());
        for (Vector2D v : result.values()) {
            assertEquals(W / 2, v.getX(), DELTA);
            assertEquals(H / 2, v.getY(), DELTA);
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — output completeness
    // -----------------------------------------------------------------------

    @Test
    void calculate_twoLeafTree_bothIdsPresent() {
        TreeLayout layout = new TreeLayout(twoLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0", 0, 0), node("L1", 0, 0)));
        assertTrue(result.containsKey("L0"));
        assertTrue(result.containsKey("L1"));
        assertEquals(2, result.size());
    }

    @Test
    void calculate_threeLeafTree_allIdsPresent() {
        TreeLayout layout = new TreeLayout(threeLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0", 0, 0), node("L1", 0, 0), node("L2", 0, 0)));
        assertEquals(3, result.size());
    }

    // -----------------------------------------------------------------------
    // calculate() — x-axis: in-order left-to-right positioning
    // -----------------------------------------------------------------------

    @Test
    void calculate_twoLeafTree_leftLeafAtLeftMargin() {
        // leafCount=2: x_L0 = margin + 0/(2-1)*usable = 40
        TreeLayout layout = new TreeLayout(twoLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0", 0, 0), node("L1", 0, 0)));
        assertEquals(MARGIN, result.get("L0").getX(), DELTA);
    }

    @Test
    void calculate_twoLeafTree_rightLeafAtRightMargin() {
        // x_L1 = margin + 1/(2-1)*usable = 40 + 320 = 360
        TreeLayout layout = new TreeLayout(twoLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0", 0, 0), node("L1", 0, 0)));
        assertEquals(W - MARGIN, result.get("L1").getX(), DELTA);
    }

    @Test
    void calculate_threeLeafTree_middleLeafAtCanvasCentreX() {
        // leafCount=3: x_L1 = 40 + 1/2*320 = 40+160 = 200
        TreeLayout layout = new TreeLayout(threeLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0", 0, 0), node("L1", 0, 0), node("L2", 0, 0)));
        assertEquals(W / 2, result.get("L1").getX(), DELTA);
    }

    @Test
    void calculate_threeLeafTree_leftLeafAtLeftMarginX() {
        TreeLayout layout = new TreeLayout(threeLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0", 0, 0), node("L1", 0, 0), node("L2", 0, 0)));
        assertEquals(MARGIN, result.get("L0").getX(), DELTA);
    }

    @Test
    void calculate_threeLeafTree_rightLeafAtRightMarginX() {
        TreeLayout layout = new TreeLayout(threeLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0", 0, 0), node("L1", 0, 0), node("L2", 0, 0)));
        assertEquals(W - MARGIN, result.get("L2").getX(), DELTA);
    }

    @Test
    void calculate_xCoordinatesStrictlyIncreaseLeftToRight() {
        // In-order traversal: L0 < L1 < L2
        TreeLayout layout = new TreeLayout(threeLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0", 0, 0), node("L1", 0, 0), node("L2", 0, 0)));
        assertTrue(result.get("L0").getX() < result.get("L1").getX());
        assertTrue(result.get("L1").getX() < result.get("L2").getX());
    }

    // -----------------------------------------------------------------------
    // calculate() — y-axis: depth positioning
    // -----------------------------------------------------------------------

    @Test
    void calculate_twoLeafTree_bothLeavesAtSameY() {
        // L0 and L1 both at depth=1; maxDepth=1 → y = margin + 1/1*usable = 360
        TreeLayout layout = new TreeLayout(twoLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0", 0, 0), node("L1", 0, 0)));
        assertEquals(result.get("L0").getY(), result.get("L1").getY(), DELTA);
    }

    @Test
    void calculate_twoLeafTree_leavesAtBottomMarginY() {
        // y = margin + 1/1*usable = 40 + 320 = 360
        TreeLayout layout = new TreeLayout(twoLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0", 0, 0), node("L1", 0, 0)));
        assertEquals(H - MARGIN, result.get("L0").getY(), DELTA);
    }

    @Test
    void calculate_threeLeafTree_L0ShallowerThanL1andL2() {
        // L0 at depth=1, L1 and L2 at depth=2 → L0 has smaller y
        TreeLayout layout = new TreeLayout(threeLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0", 0, 0), node("L1", 0, 0), node("L2", 0, 0)));
        assertTrue(result.get("L0").getY() < result.get("L1").getY(),
                "L0 at depth 1 must have smaller y than L1 at depth 2");
    }

    @Test
    void calculate_threeLeafTree_exactYForL0() {
        // L0 depth=1, maxDepth=2: y = 40 + 1/2*320 = 40+160 = 200
        TreeLayout layout = new TreeLayout(threeLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0", 0, 0), node("L1", 0, 0), node("L2", 0, 0)));
        assertEquals(H / 2, result.get("L0").getY(), DELTA);
    }

    @Test
    void calculate_threeLeafTree_L1andL2SameY() {
        // L1 and L2 both at depth=2
        TreeLayout layout = new TreeLayout(threeLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0", 0, 0), node("L1", 0, 0), node("L2", 0, 0)));
        assertEquals(result.get("L1").getY(), result.get("L2").getY(), DELTA);
    }

    @Test
    void calculate_threeLeafTree_deepLeavesAtBottomMarginY() {
        // y_L1 = y_L2 = 40 + 2/2*320 = 360
        TreeLayout layout = new TreeLayout(threeLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0", 0, 0), node("L1", 0, 0), node("L2", 0, 0)));
        assertEquals(H - MARGIN, result.get("L1").getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // calculate() — balanced four-leaf tree exact values
    // -----------------------------------------------------------------------

    @Test
    void calculate_balancedFour_allLeavesAtSameDepth_sameY() {
        // All leaves at depth=2 → same y
        TreeLayout layout = new TreeLayout(balancedFourTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0",0,0),node("L1",0,0),node("L2",0,0),node("L3",0,0)));
        double y0 = result.get("L0").getY();
        assertEquals(y0, result.get("L1").getY(), DELTA);
        assertEquals(y0, result.get("L2").getY(), DELTA);
        assertEquals(y0, result.get("L3").getY(), DELTA);
    }

    @Test
    void calculate_balancedFour_xCoordinatesEquallySpaced() {
        // leafCount=4, positions 0..3: spacing = usable/3 = 320/3 ≈ 106.67
        TreeLayout layout = new TreeLayout(balancedFourTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0",0,0),node("L1",0,0),node("L2",0,0),node("L3",0,0)));
        double expectedSpacing = USABLE / 3.0;
        assertEquals(expectedSpacing,
                result.get("L1").getX() - result.get("L0").getX(), DELTA);
        assertEquals(expectedSpacing,
                result.get("L2").getX() - result.get("L1").getX(), DELTA);
        assertEquals(expectedSpacing,
                result.get("L3").getX() - result.get("L2").getX(), DELTA);
    }

    // -----------------------------------------------------------------------
    // calculate() — node absent from dendrogram falls back to canvas centre
    // -----------------------------------------------------------------------

    @Test
    void calculate_nodeAbsentFromDendrogram_placedAtCanvasCentre() {
        TreeLayout layout = new TreeLayout(twoLeafTree(), W, H);
        // "ghost" is not a leaf in twoLeafTree (which has "L0" and "L1")
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0",0,0), node("L1",0,0), node("ghost",0,0)));
        assertEquals(W / 2, result.get("ghost").getX(), DELTA);
        assertEquals(H / 2, result.get("ghost").getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // calculate() — result contract
    // -----------------------------------------------------------------------

    @Test
    void calculate_resultIsUnmodifiable() {
        TreeLayout layout = new TreeLayout(twoLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0",0,0), node("L1",0,0)));
        assertThrows(UnsupportedOperationException.class,
                () -> result.put("x", Vector2D.ZERO));
    }

    @Test
    void calculate_doesNotModifyInputList() {
        TreeLayout layout = new TreeLayout(twoLeafTree(), W, H);
        List<NodeData> input = new ArrayList<>();
        input.add(node("L0", 0, 0));
        input.add(node("L1", 0, 0));
        List<NodeData> snapshot = new ArrayList<>(input);
        layout.calculate(input);
        assertEquals(snapshot.size(), input.size());
        for (int i = 0; i < input.size(); i++) assertSame(snapshot.get(i), input.get(i));
    }

    @Test
    void calculate_noNanOrInfinity() {
        TreeLayout layout = new TreeLayout(threeLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0",0,0), node("L1",0,0), node("L2",0,0)));
        for (Vector2D v : result.values()) {
            assertFalse(Double.isNaN(v.getX()) || Double.isNaN(v.getY()));
            assertFalse(Double.isInfinite(v.getX()) || Double.isInfinite(v.getY()));
        }
    }

    @Test
    void calculate_calledTwice_sameResult() {
        TreeLayout layout = new TreeLayout(twoLeafTree(), W, H);
        List<NodeData> nodes = List.of(node("L0",0,0), node("L1",0,0));
        Map<String, Vector2D> r1 = layout.calculate(nodes);
        Map<String, Vector2D> r2 = layout.calculate(nodes);
        for (String id : List.of("L0","L1")) {
            assertEquals(r1.get(id).getX(), r2.get(id).getX(), DELTA);
            assertEquals(r1.get(id).getY(), r2.get(id).getY(), DELTA);
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — input order independent of dendrogram
    // -----------------------------------------------------------------------

    @Test
    void calculate_inputOrderIndependent_xPositionMatchesDendrogram() {
        // Dendrogram: root → L0 (left), L1 (right).  L0 must always be at leftMargin
        // regardless of whether L0 or L1 appears first in the NodeData list.
        TreeLayout layout = new TreeLayout(twoLeafTree(), W, H);

        Map<String, Vector2D> forwardOrder = layout.calculate(
                List.of(node("L0", 0, 0), node("L1", 0, 0)));
        Map<String, Vector2D> reverseOrder = layout.calculate(
                List.of(node("L1", 0, 0), node("L0", 0, 0)));

        assertEquals(forwardOrder.get("L0").getX(), reverseOrder.get("L0").getX(), DELTA,
                "L0 x must be identical regardless of NodeData list order");
        assertEquals(forwardOrder.get("L1").getX(), reverseOrder.get("L1").getX(), DELTA,
                "L1 x must be identical regardless of NodeData list order");
    }

    // -----------------------------------------------------------------------
    // calculate() — left-skewed tree
    // -----------------------------------------------------------------------

    @Test
    void calculate_leftSkewedTree_deepLeavesOnLeft_shallowOnRight() {
        // Left-skewed tree:
        //     root
        //    /    \
        //  int    L2
        //  /  \
        // L0  L1
        // L0 depth=2, L1 depth=2, L2 depth=1
        DendrogramNode internal = DendrogramNode.merge(
                DendrogramNode.leaf("L0"), DendrogramNode.leaf("L1"), 0.5);
        DendrogramNode root = DendrogramNode.merge(internal, DendrogramNode.leaf("L2"), 1.0);
        TreeLayout layout = new TreeLayout(root, W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0",0,0), node("L1",0,0), node("L2",0,0)));

        // In-order: L0(depth2), L1(depth2), L2(depth1)
        assertTrue(result.get("L0").getX() < result.get("L1").getX(),
                "left subtree L0 must precede L1");
        assertTrue(result.get("L1").getX() < result.get("L2").getX(),
                "left subtree leaves must precede right leaf");

        // L2 is shallower → smaller y
        assertTrue(result.get("L2").getY() < result.get("L0").getY(),
                "shallower right leaf must have smaller y than deeper left leaves");
    }

    @Test
    void calculate_leftSkewedTree_L2_exactYAtHalfDepth() {
        // maxDepth=2, L2 at depth=1: y = 40 + 1/2*320 = 200
        DendrogramNode internal = DendrogramNode.merge(
                DendrogramNode.leaf("L0"), DendrogramNode.leaf("L1"), 0.5);
        DendrogramNode root = DendrogramNode.merge(internal, DendrogramNode.leaf("L2"), 1.0);
        TreeLayout layout = new TreeLayout(root, W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0",0,0), node("L1",0,0), node("L2",0,0)));

        assertEquals(H / 2, result.get("L2").getY(), DELTA,
                "L2 at depth 1 of 2 must be at canvas midpoint y");
    }

    // -----------------------------------------------------------------------
    // calculate() — all nodes absent from dendrogram
    // -----------------------------------------------------------------------

    @Test
    void calculate_allNodesAbsentFromDendrogram_allAtCanvasCentre() {
        // twoLeafTree has L0 and L1; pass completely different IDs
        TreeLayout layout = new TreeLayout(twoLeafTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("X",0,0), node("Y",0,0), node("Z",0,0)));

        for (String id : List.of("X","Y","Z")) {
            assertEquals(W / 2, result.get(id).getX(), DELTA,
                    id + " absent from dendrogram must fall back to canvas centre x");
            assertEquals(H / 2, result.get(id).getY(), DELTA,
                    id + " absent from dendrogram must fall back to canvas centre y");
        }
    }

    // -----------------------------------------------------------------------
    // calculate() — single-leaf dendrogram
    // -----------------------------------------------------------------------

    @Test
    void calculate_singleLeafDendrogram_matchingNode_atCanvasCentre() {
        // root is a leaf, leafCount=1: x = W/2, depth=0 → y = H/2
        TreeLayout layout = new TreeLayout(DendrogramNode.leaf("solo"), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("solo",0,0), node("other",0,0)));

        assertEquals(W / 2, result.get("solo").getX(), DELTA,
                "single-leaf dendrogram: leaf x must be canvas centre");
        assertEquals(H / 2, result.get("solo").getY(), DELTA,
                "single-leaf dendrogram: leaf y must be canvas centre (depth=0)");
    }

    @Test
    void calculate_singleLeafDendrogram_nonMatchingNode_atCanvasCentre() {
        TreeLayout layout = new TreeLayout(DendrogramNode.leaf("solo"), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("solo",0,0), node("ghost",0,0)));

        // "ghost" is not in dendrogram at all — also falls back to centre
        assertEquals(W / 2, result.get("ghost").getX(), DELTA);
        assertEquals(H / 2, result.get("ghost").getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // calculate() — balanced-four exact y value
    // -----------------------------------------------------------------------

    @Test
    void calculate_balancedFour_exactYValue() {
        // All leaves at depth=2, maxDepth=2: y = 40 + 2/2*320 = 360
        TreeLayout layout = new TreeLayout(balancedFourTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0",0,0),node("L1",0,0),node("L2",0,0),node("L3",0,0)));
        assertEquals(H - MARGIN, result.get("L0").getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // calculate() — deep chain (5 levels)
    // -----------------------------------------------------------------------

    @Test
    void calculate_deepChain_leafDepthsMonotonicallyIncrease() {
        // Chain: root → (A, chain2) → (B, chain3) → (C, chain4) → (D, E)
        // A at depth=1, B at depth=2, C at depth=3, D/E at depth=4
        DendrogramNode chain4 = DendrogramNode.merge(
                DendrogramNode.leaf("D"), DendrogramNode.leaf("E"), 0.1);
        DendrogramNode chain3 = DendrogramNode.merge(
                DendrogramNode.leaf("C"), chain4, 0.2);
        DendrogramNode chain2 = DendrogramNode.merge(
                DendrogramNode.leaf("B"), chain3, 0.3);
        DendrogramNode root   = DendrogramNode.merge(
                DendrogramNode.leaf("A"), chain2, 0.5);

        TreeLayout layout = new TreeLayout(root, W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("A",0,0), node("B",0,0), node("C",0,0),
                        node("D",0,0), node("E",0,0)));

        // y must increase: A < B < C < D = E
        assertTrue(result.get("A").getY() < result.get("B").getY(), "A shallower than B");
        assertTrue(result.get("B").getY() < result.get("C").getY(), "B shallower than C");
        assertTrue(result.get("C").getY() < result.get("D").getY(), "C shallower than D");
        assertEquals(result.get("D").getY(), result.get("E").getY(), DELTA, "D and E same depth");
    }

    @Test
    void calculate_deepChain_exactYForShallowLeaf() {
        // A at depth=1, maxDepth=4: y = 40 + 1/4*320 = 40+80 = 120
        DendrogramNode chain4 = DendrogramNode.merge(
                DendrogramNode.leaf("D"), DendrogramNode.leaf("E"), 0.1);
        DendrogramNode chain3 = DendrogramNode.merge(
                DendrogramNode.leaf("C"), chain4, 0.2);
        DendrogramNode chain2 = DendrogramNode.merge(
                DendrogramNode.leaf("B"), chain3, 0.3);
        DendrogramNode root   = DendrogramNode.merge(
                DendrogramNode.leaf("A"), chain2, 0.5);

        TreeLayout layout = new TreeLayout(root, W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("A",0,0), node("B",0,0), node("C",0,0),
                        node("D",0,0), node("E",0,0)));

        double expected = MARGIN + (1.0 / 4.0) * USABLE; // 40 + 80 = 120
        assertEquals(expected, result.get("A").getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // calculate() — all x positions are distinct for leaves in different slots
    // -----------------------------------------------------------------------

    @Test
    void calculate_balancedFour_allXPositionsDistinct() {
        TreeLayout layout = new TreeLayout(balancedFourTree(), W, H);
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0",0,0),node("L1",0,0),node("L2",0,0),node("L3",0,0)));

        double x0 = result.get("L0").getX(), x1 = result.get("L1").getX();
        double x2 = result.get("L2").getX(), x3 = result.get("L3").getX();
        // All four must be distinct
        assertTrue(x0 < x1 && x1 < x2 && x2 < x3,
                "all four x positions must be strictly increasing");
    }

    // -----------------------------------------------------------------------
    // isIterative() / getName()
    // -----------------------------------------------------------------------

    @Test
    void isIterative_returnsFalse() {
        assertFalse(new TreeLayout(null, W, H).isIterative());
    }

    @Test
    void getName_returnsTree() {
        assertEquals("Tree", new TreeLayout(null, W, H).getName());
    }

    // -----------------------------------------------------------------------
    // Default constructor smoke test
    // -----------------------------------------------------------------------

    @Test
    void defaultConstructor_calculate_twoLeaves_noException() {
        TreeLayout layout = new TreeLayout(twoLeafTree());
        Map<String, Vector2D> result = layout.calculate(
                List.of(node("L0",0,0), node("L1",0,0)));
        assertEquals(2, result.size());
    }

    // -----------------------------------------------------------------------
    // Tree helpers
    // -----------------------------------------------------------------------

    /**
     * root → L0, L1  (depth 1 leaves)
     */
    private static DendrogramNode twoLeafTree() {
        return DendrogramNode.merge(
                DendrogramNode.leaf("L0"),
                DendrogramNode.leaf("L1"),
                1.0);
    }

    /**
     * <pre>
     *   root
     *   /   \
     *  L0  (internal)
     *       /  \
     *      L1  L2
     * </pre>
     * L0 at depth 1; L1, L2 at depth 2.
     */
    private static DendrogramNode threeLeafTree() {
        DendrogramNode internal = DendrogramNode.merge(
                DendrogramNode.leaf("L1"),
                DendrogramNode.leaf("L2"),
                0.5);
        return DendrogramNode.merge(DendrogramNode.leaf("L0"), internal, 1.0);
    }

    /**
     * <pre>
     *       root
     *      /    \
     *    int0  int1
     *    /  \   /  \
     *   L0  L1 L2  L3
     * </pre>
     * All leaves at depth 2.
     */
    private static DendrogramNode balancedFourTree() {
        DendrogramNode int0 = DendrogramNode.merge(
                DendrogramNode.leaf("L0"), DendrogramNode.leaf("L1"), 0.3);
        DendrogramNode int1 = DendrogramNode.merge(
                DendrogramNode.leaf("L2"), DendrogramNode.leaf("L3"), 0.3);
        return DendrogramNode.merge(int0, int1, 1.0);
    }

    private static NodeData node(String id, double x, double y) {
        return new NodeData(id, new Vector2D(x, y), "sector", java.util.List.of());
    }
}
