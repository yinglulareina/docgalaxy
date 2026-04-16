package com.docgalaxy.layout;

import com.docgalaxy.model.Vector2D;
import com.docgalaxy.model.celestial.CelestialBody;
import com.docgalaxy.util.AppConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OverlapResolver}.
 *
 * <p>Uses a minimal concrete subclass of {@link CelestialBody} so that tests
 * do not depend on {@code Star} construction complexity.
 */
class OverlapResolverTest {

    private static final double DELTA  = 1e-9;
    private static final double PAD    = AppConstants.OVERLAP_PADDING; // 2.0
    private static final double RADIUS = 10.0;

    private OverlapResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OverlapResolver();
    }

    // -----------------------------------------------------------------------
    // Minimal CelestialBody stub
    // -----------------------------------------------------------------------

    /** Minimal concrete subclass — just exposes position and radius. */
    static final class TestNode extends CelestialBody {
        TestNode(String id, double x, double y, double radius) {
            super(id, new Vector2D(x, y), radius, Color.WHITE);
        }
        @Override public void draw(Graphics2D g, double zoom) {}
        @Override public String getTooltipText() { return id; }
    }

    private static TestNode node(String id, double x, double y) {
        return new TestNode(id, x, y, RADIUS);
    }

    private static TestNode node(String id, double x, double y, double radius) {
        return new TestNode(id, x, y, radius);
    }

    // -----------------------------------------------------------------------
    // resolve() — argument validation
    // -----------------------------------------------------------------------

    @Test
    void resolve_nullList_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(null));
    }

    @Test
    void resolve_emptyList_doesNotThrow() {
        assertDoesNotThrow(() -> resolver.resolve(Collections.emptyList()));
    }

    @Test
    void resolve_singleNode_doesNotThrow() {
        assertDoesNotThrow(() -> resolver.resolve(List.of(node("a", 0, 0))));
    }

    // -----------------------------------------------------------------------
    // Constructor validation (package-private)
    // -----------------------------------------------------------------------

    @Test
    void constructor_zeroPasses_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new OverlapResolver(0));
    }

    @Test
    void constructor_negativePasses_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new OverlapResolver(-1));
    }

    // -----------------------------------------------------------------------
    // Non-overlapping nodes — positions unchanged
    // -----------------------------------------------------------------------

    @Test
    void resolve_noOverlap_horizontal_positionsUnchanged() {
        // minDist = 10+10+2 = 22; nodes 100 apart → no overlap
        TestNode a = node("a",   0, 0);
        TestNode b = node("b", 100, 0);
        resolver.resolve(List.of(a, b));

        assertEquals(  0.0, a.getPosition().getX(), DELTA);
        assertEquals(100.0, b.getPosition().getX(), DELTA);
        assertEquals(  0.0, a.getPosition().getY(), DELTA);
        assertEquals(  0.0, b.getPosition().getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // Overlapping pair — separation contract
    // -----------------------------------------------------------------------

    @Test
    void resolve_overlappingPair_finalDistanceAtLeastMinDist() {
        // Place two radius-10 nodes 5 units apart (overlapping: 5 < 22)
        TestNode a = node("a",  0, 0);
        TestNode b = node("b",  5, 0);
        resolver.resolve(List.of(a, b));

        double dist    = a.getPosition().distanceTo(b.getPosition());
        double minDist = RADIUS + RADIUS + PAD;
        assertTrue(dist >= minDist - DELTA,
                "after resolution distance " + dist + " must be >= minDist " + minDist);
    }

    @Test
    void resolve_overlappingPair_pushedSymmetrically() {
        // Midpoint must be preserved since each node is pushed half the penetration
        TestNode a = node("a",  0, 0);
        TestNode b = node("b", 10, 0);
        resolver.resolve(List.of(a, b));

        double midX = (a.getPosition().getX() + b.getPosition().getX()) / 2.0;
        double midY = (a.getPosition().getY() + b.getPosition().getY()) / 2.0;
        assertEquals(5.0, midX, DELTA, "midpoint x must be preserved");
        assertEquals(0.0, midY, DELTA, "midpoint y must be preserved");
    }

    @Test
    void resolve_overlappingPair_separationAlongConnectionLine() {
        // Nodes on y-axis: any push must stay on y-axis
        TestNode a = node("a", 0,  0);
        TestNode b = node("b", 0,  5);
        resolver.resolve(List.of(a, b));

        assertEquals(0.0, a.getPosition().getX(), DELTA);
        assertEquals(0.0, b.getPosition().getX(), DELTA);
        assertTrue(b.getPosition().getY() > a.getPosition().getY());
    }

    @Test
    void resolve_exactlyAtMinDist_noMovement() {
        double minDist = RADIUS + RADIUS + PAD; // 22.0
        TestNode a = node("a",      0, 0);
        TestNode b = node("b", minDist, 0);
        resolver.resolve(List.of(a, b));

        assertEquals(     0.0, a.getPosition().getX(), DELTA);
        assertEquals(minDist,  b.getPosition().getX(), DELTA);
    }

    // -----------------------------------------------------------------------
    // Coincident centres — deterministic tiebreak
    // -----------------------------------------------------------------------

    @Test
    void resolve_coincidentCentres_doesNotThrow() {
        TestNode a = node("a", 50, 50);
        TestNode b = node("b", 50, 50);
        assertDoesNotThrow(() -> resolver.resolve(List.of(a, b)));
    }

    @Test
    void resolve_coincidentCentres_separatedToAtLeastMinDist() {
        TestNode a = node("a", 50, 50);
        TestNode b = node("b", 50, 50);
        resolver.resolve(List.of(a, b));

        double dist    = a.getPosition().distanceTo(b.getPosition());
        double minDist = RADIUS + RADIUS + PAD;
        assertTrue(dist >= minDist - DELTA,
                "coincident nodes must be separated to at least minDist");
    }

    @Test
    void resolve_coincidentCentres_separationAlongTiebreakAxis() {
        // Tiebreak direction is +x: a moves right, b moves left; y unchanged
        TestNode a = node("a", 50, 50);
        TestNode b = node("b", 50, 50);
        resolver.resolve(List.of(a, b));

        assertTrue(a.getPosition().getX() > b.getPosition().getX(),
                "a should be to the right of b after tiebreak");
        assertEquals(50.0, a.getPosition().getY(), DELTA);
        assertEquals(50.0, b.getPosition().getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // Different radii
    // -----------------------------------------------------------------------

    @Test
    void resolve_differentRadii_usesCorrectMinDist() {
        // a: radius 5, b: radius 15 → minDist = 5+15+2 = 22; place 10 apart
        TestNode a = node("a",  0, 0,  5);
        TestNode b = node("b", 10, 0, 15);
        resolver.resolve(List.of(a, b));

        double dist    = a.getPosition().distanceTo(b.getPosition());
        double minDist = 5 + 15 + PAD;
        assertTrue(dist >= minDist - DELTA,
                "must use sum of each node's actual radius");
    }

    // -----------------------------------------------------------------------
    // Three nodes — all pairs resolved
    // -----------------------------------------------------------------------

    @Test
    void resolve_threeOverlappingNodes_allPairsSeparated() {
        TestNode a = node("a",  0, 0);
        TestNode b = node("b",  5, 0);
        TestNode c = node("c", 10, 0);
        resolver.resolve(List.of(a, b, c));

        double minDist = RADIUS + RADIUS + PAD;
        assertTrue(a.getPosition().distanceTo(b.getPosition()) >= minDist - DELTA,
                "a-b must be separated");
        assertTrue(b.getPosition().distanceTo(c.getPosition()) >= minDist - DELTA,
                "b-c must be separated");
    }

    // -----------------------------------------------------------------------
    // Multiple passes vs. single pass
    // -----------------------------------------------------------------------

    @Test
    void resolve_morePasses_betterOrEqualSeparation() {
        List<TestNode> nodes1 = List.of(
                new TestNode("a", 0, 0, RADIUS),
                new TestNode("b", 1, 0, RADIUS),
                new TestNode("c", 0, 1, RADIUS),
                new TestNode("d", 1, 1, RADIUS));
        List<TestNode> nodes3 = List.of(
                new TestNode("a", 0, 0, RADIUS),
                new TestNode("b", 1, 0, RADIUS),
                new TestNode("c", 0, 1, RADIUS),
                new TestNode("d", 1, 1, RADIUS));

        new OverlapResolver(1).resolve(nodes1);
        new OverlapResolver(3).resolve(nodes3);

        double minPairDist1 = minPairDist(nodes1);
        double minPairDist3 = minPairDist(nodes3);

        assertTrue(minPairDist3 >= minPairDist1 - DELTA,
                "3-pass resolver should produce >= separation than 1-pass");
    }

    // -----------------------------------------------------------------------
    // No NaN / Infinity
    // -----------------------------------------------------------------------

    @Test
    void resolve_manyOverlappingNodes_noNaNOrInfinity() {
        List<CelestialBody> nodes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            nodes.add(node("n" + i, i * 3.0, 0));  // all densely packed
        }
        resolver.resolve(nodes);

        for (CelestialBody n : nodes) {
            assertFalse(Double.isNaN(n.getPosition().getX()),      "x must not be NaN");
            assertFalse(Double.isNaN(n.getPosition().getY()),      "y must not be NaN");
            assertFalse(Double.isInfinite(n.getPosition().getX()), "x must not be Infinite");
            assertFalse(Double.isInfinite(n.getPosition().getY()), "y must not be Infinite");
        }
    }

    // -----------------------------------------------------------------------
    // Default pass count
    // -----------------------------------------------------------------------

    @Test
    void defaultConstructor_passCountIsDefaultPasses() {
        assertEquals(3, OverlapResolver.DEFAULT_PASSES);
    }

    // -----------------------------------------------------------------------
    // Diagonal overlap
    // -----------------------------------------------------------------------

    @Test
    void resolve_diagonalOverlap_separatesCorrectly() {
        // a at (0,0), b at (3,4) → dist=5 < 22
        TestNode a = node("a", 0, 0);
        TestNode b = node("b", 3, 4);
        resolver.resolve(List.of(a, b));

        double dist = a.getPosition().distanceTo(b.getPosition());
        assertTrue(dist >= RADIUS + RADIUS + PAD - DELTA);
    }

    @Test
    void resolve_diagonalOverlap_midpointPreserved() {
        // Midpoint of (0,0) and (3,4) = (1.5, 2.0); must be preserved
        TestNode a = node("a", 0, 0);
        TestNode b = node("b", 3, 4);
        resolver.resolve(List.of(a, b));

        double midX = (a.getPosition().getX() + b.getPosition().getX()) / 2.0;
        double midY = (a.getPosition().getY() + b.getPosition().getY()) / 2.0;
        assertEquals(1.5, midX, DELTA);
        assertEquals(2.0, midY, DELTA);
    }

    // -----------------------------------------------------------------------
    // Exact push calculation (single pass, one pair)
    // -----------------------------------------------------------------------

    @Test
    void resolve_exactPush_horizontalPair_singlePass() {
        // a at (0,0), b at (10,0): dist=10, minDist=22, penetration=12, halfPush=6
        // After 1 pass: a at (-6,0), b at (16,0)
        OverlapResolver singlePass = new OverlapResolver(1);
        TestNode a = node("a",  0, 0);
        TestNode b = node("b", 10, 0);
        singlePass.resolve(List.of(a, b));

        assertEquals( -6.0, a.getPosition().getX(), DELTA);
        assertEquals(  0.0, a.getPosition().getY(), DELTA);
        assertEquals( 16.0, b.getPosition().getX(), DELTA);
        assertEquals(  0.0, b.getPosition().getY(), DELTA);
    }

    @Test
    void resolve_exactPush_verticalPair_singlePass() {
        // a at (0,0), b at (0,10): dist=10, penetration=12, halfPush=6
        // After 1 pass: a at (0,-6), b at (0,16)
        OverlapResolver singlePass = new OverlapResolver(1);
        TestNode a = node("a", 0,  0);
        TestNode b = node("b", 0, 10);
        singlePass.resolve(List.of(a, b));

        assertEquals(0.0,  a.getPosition().getX(), DELTA);
        assertEquals(-6.0, a.getPosition().getY(), DELTA);
        assertEquals(0.0,  b.getPosition().getX(), DELTA);
        assertEquals(16.0, b.getPosition().getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static double minPairDist(List<? extends CelestialBody> nodes) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < nodes.size() - 1; i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                double d = nodes.get(i).getPosition().distanceTo(nodes.get(j).getPosition());
                if (d < min) min = d;
            }
        }
        return min;
    }
}
