package com.docgalaxy.layout;

import com.docgalaxy.model.Vector2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ForceCalculator}.
 *
 * <p>All assertions on floating-point values use delta=1e-9 unless the
 * expected result is known only to looser precision (delta=1e-6 noted inline).
 */
class ForceCalculatorTest {

    private static final double DELTA = 1e-9;
    private static final double K     = 10.0;

    // -----------------------------------------------------------------------
    // repulsion() — argument validation
    // -----------------------------------------------------------------------

    @Test
    void repulsion_nullPosA_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ForceCalculator.repulsion(null, new Vector2D(1, 0), K));
    }

    @Test
    void repulsion_nullPosB_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ForceCalculator.repulsion(new Vector2D(0, 0), null, K));
    }

    @Test
    void repulsion_nonPositiveK_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ForceCalculator.repulsion(new Vector2D(0, 0), new Vector2D(1, 0), 0));
        assertThrows(IllegalArgumentException.class,
                () -> ForceCalculator.repulsion(new Vector2D(0, 0), new Vector2D(1, 0), -5));
    }

    // -----------------------------------------------------------------------
    // repulsion() — direction
    // -----------------------------------------------------------------------

    @Test
    void repulsion_pointsAlongXAxis_forceIsInPositiveX() {
        // A at (5,0), B at (0,0) → force on A is away from B, i.e. positive x
        Vector2D f = ForceCalculator.repulsion(new Vector2D(5, 0), new Vector2D(0, 0), K);
        assertTrue(f.getX() > 0, "repulsion must push A away from B");
        assertEquals(0.0, f.getY(), DELTA);
    }

    @Test
    void repulsion_pointsAlongNegativeXAxis_forceIsNegativeX() {
        Vector2D f = ForceCalculator.repulsion(new Vector2D(-5, 0), new Vector2D(0, 0), K);
        assertTrue(f.getX() < 0);
        assertEquals(0.0, f.getY(), DELTA);
    }

    @Test
    void repulsion_symmetry_forcesAreEqualAndOpposite() {
        Vector2D posA = new Vector2D(3, 4);
        Vector2D posB = new Vector2D(6, 8);
        Vector2D fOnA = ForceCalculator.repulsion(posA, posB, K);
        Vector2D fOnB = ForceCalculator.repulsion(posB, posA, K);

        assertEquals(fOnA.getX(), -fOnB.getX(), DELTA);
        assertEquals(fOnA.getY(), -fOnB.getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // repulsion() — magnitude (FR formula K²/d²)
    // -----------------------------------------------------------------------

    @Test
    void repulsion_exactMagnitude_horizontalUnit() {
        // A at (1,0), B at (0,0): d=1, expected magnitude = K²/1 = K²
        Vector2D f = ForceCalculator.repulsion(new Vector2D(1, 0), new Vector2D(0, 0), K);
        assertEquals(K * K, f.magnitude(), DELTA);
    }

    @Test
    void repulsion_exactMagnitude_distanceTwo() {
        // FR formula: magnitude = K²/d = K²/2
        Vector2D f = ForceCalculator.repulsion(new Vector2D(2, 0), new Vector2D(0, 0), K);
        assertEquals(K * K / 2.0, f.magnitude(), DELTA);
    }

    @Test
    void repulsion_largerDistanceGivesWeakerForce() {
        Vector2D fClose = ForceCalculator.repulsion(new Vector2D(1, 0), new Vector2D(0, 0), K);
        Vector2D fFar   = ForceCalculator.repulsion(new Vector2D(5, 0), new Vector2D(0, 0), K);
        assertTrue(fClose.magnitude() > fFar.magnitude());
    }

    @Test
    void repulsion_largerKGivesStrongerForce() {
        Vector2D pos = new Vector2D(3, 0);
        Vector2D fSmallK = ForceCalculator.repulsion(pos, Vector2D.ZERO, 1.0);
        Vector2D fLargeK = ForceCalculator.repulsion(pos, Vector2D.ZERO, 10.0);
        assertTrue(fLargeK.magnitude() > fSmallK.magnitude());
    }

    @Test
    void repulsion_coincidentPoints_returnsZero() {
        Vector2D f = ForceCalculator.repulsion(new Vector2D(3, 4), new Vector2D(3, 4), K);
        assertEquals(0.0, f.getX(), DELTA);
        assertEquals(0.0, f.getY(), DELTA);
    }

    @Test
    void repulsion_diagonalDistance_exactValues() {
        // A=(3,4), B=(0,0): d=5, magnitude = K²/d = K²/5, direction=(3/5, 4/5)
        Vector2D f = ForceCalculator.repulsion(new Vector2D(3, 4), Vector2D.ZERO, K);
        double expected = K * K / 5.0;  // 20.0
        assertEquals(expected, f.magnitude(), DELTA);
        assertEquals(expected * 3.0 / 5, f.getX(), DELTA);
        assertEquals(expected * 4.0 / 5, f.getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // semanticAttraction() — argument validation
    // -----------------------------------------------------------------------

    @Test
    void semanticAttraction_nullPosA_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ForceCalculator.semanticAttraction(null, new Vector2D(1, 0),
                        0.8, 0.0, 1.0, K));
    }

    @Test
    void semanticAttraction_nullPosB_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ForceCalculator.semanticAttraction(new Vector2D(0, 0), null,
                        0.8, 0.0, 1.0, K));
    }

    @Test
    void semanticAttraction_nonPositiveK_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ForceCalculator.semanticAttraction(
                        new Vector2D(0, 0), new Vector2D(1, 0), 0.5, 0.0, 1.0, 0));
    }

    // -----------------------------------------------------------------------
    // semanticAttraction() — direction
    // -----------------------------------------------------------------------

    @Test
    void semanticAttraction_maximumSimilarity_attractsTowardB() {
        // sNorm=1 → idealLength=0, force pulls A toward B
        Vector2D posA = new Vector2D(0, 0);
        Vector2D posB = new Vector2D(10, 0);
        Vector2D f = ForceCalculator.semanticAttraction(posA, posB, 1.0, 0.0, 1.0, K);
        assertTrue(f.getX() > 0, "maximum similarity must attract A toward B");
        assertEquals(0.0, f.getY(), DELTA);
    }

    @Test
    void semanticAttraction_symmetry_forcesAreEqualAndOpposite() {
        Vector2D posA = new Vector2D(0, 0);
        Vector2D posB = new Vector2D(8, 0);
        double sim = 0.7, min = 0.0, max = 1.0;
        Vector2D fOnA = ForceCalculator.semanticAttraction(posA, posB, sim, min, max, K);
        Vector2D fOnB = ForceCalculator.semanticAttraction(posB, posA, sim, min, max, K);

        assertEquals(fOnA.getX(), -fOnB.getX(), DELTA);
        assertEquals(fOnA.getY(), -fOnB.getY(), DELTA);
    }

    @Test
    void semanticAttraction_zeroSimilarity_returnsZeroForce() {
        // sNorm=0 → stiffness=0 → no force
        Vector2D f = ForceCalculator.semanticAttraction(
                new Vector2D(0, 0), new Vector2D(5, 0), 0.0, 0.0, 1.0, K);
        assertEquals(0.0, f.getX(), DELTA);
        assertEquals(0.0, f.getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // semanticAttraction() — magnitude and formula
    // -----------------------------------------------------------------------

    @Test
    void semanticAttraction_exactFormula_sNormOne_distanceTen() {
        // sNorm=1: stiffness=1, idealLength=0, displacement=10
        // force magnitude = 1 * 10 = 10, direction = +x
        Vector2D posA = new Vector2D(0, 0);
        Vector2D posB = new Vector2D(10, 0);
        Vector2D f = ForceCalculator.semanticAttraction(posA, posB, 1.0, 0.0, 1.0, K);
        assertEquals(10.0, f.getX(), DELTA);
        assertEquals(0.0,  f.getY(), DELTA);
    }

    @Test
    void semanticAttraction_exactFormula_sNormHalf() {
        // sNorm=0.5: stiffness=0.5, idealLength=K*0.5=5
        // dist=10, displacement=5, force=0.5*5=2.5 in +x
        Vector2D posA = new Vector2D(0, 0);
        Vector2D posB = new Vector2D(10, 0);
        Vector2D f = ForceCalculator.semanticAttraction(posA, posB, 0.5, 0.0, 1.0, K);
        assertEquals(2.5, f.getX(), DELTA);
        assertEquals(0.0, f.getY(), DELTA);
    }

    @Test
    void semanticAttraction_nodeAtIdealLength_forceIsZero() {
        // sNorm=0.5: idealLength=K*0.5=5; if dist=5, displacement=0 → force=0
        Vector2D posA = new Vector2D(0, 0);
        Vector2D posB = new Vector2D(5, 0);
        Vector2D f = ForceCalculator.semanticAttraction(posA, posB, 0.5, 0.0, 1.0, K);
        assertEquals(0.0, f.getX(), DELTA);
        assertEquals(0.0, f.getY(), DELTA);
    }

    @Test
    void semanticAttraction_nodeCloserThanIdealLength_forceIsRepulsive() {
        // sNorm=0.5: idealLength=5; dist=2 < 5 → displacement negative → repels
        Vector2D posA = new Vector2D(0, 0);
        Vector2D posB = new Vector2D(2, 0);
        Vector2D f = ForceCalculator.semanticAttraction(posA, posB, 0.5, 0.0, 1.0, K);
        assertTrue(f.getX() < 0, "below ideal length → spring pushes A away from B");
    }

    @Test
    void semanticAttraction_highSimilarityStrongerThanLow_atSameDistance() {
        Vector2D posA = new Vector2D(0, 0);
        Vector2D posB = new Vector2D(8, 0);
        Vector2D fHigh = ForceCalculator.semanticAttraction(posA, posB, 0.9, 0.0, 1.0, K);
        Vector2D fLow  = ForceCalculator.semanticAttraction(posA, posB, 0.3, 0.0, 1.0, K);
        // Both should be attractive (A toward B); high similarity pulls harder
        assertTrue(fHigh.magnitude() > fLow.magnitude(),
                "higher similarity should produce larger attraction magnitude");
    }

    @Test
    void semanticAttraction_coincidentPoints_returnsZero() {
        Vector2D f = ForceCalculator.semanticAttraction(
                new Vector2D(3, 3), new Vector2D(3, 3), 0.9, 0.0, 1.0, K);
        assertEquals(0.0, f.getX(), DELTA);
        assertEquals(0.0, f.getY(), DELTA);
    }

    @Test
    void semanticAttraction_similarityBelowMin_clampedToZeroStiffness() {
        // similarity < minSim → sNorm=0 → no force
        Vector2D f = ForceCalculator.semanticAttraction(
                new Vector2D(0, 0), new Vector2D(5, 0), -1.0, 0.0, 1.0, K);
        assertEquals(0.0, f.getX(), DELTA);
        assertEquals(0.0, f.getY(), DELTA);
    }

    @Test
    void semanticAttraction_similarityAboveMax_clampedToOne() {
        // similarity > maxSim → sNorm=1 → same as max-similarity case
        Vector2D fClamped = ForceCalculator.semanticAttraction(
                new Vector2D(0, 0), new Vector2D(10, 0), 2.0, 0.0, 1.0, K);
        Vector2D fExact = ForceCalculator.semanticAttraction(
                new Vector2D(0, 0), new Vector2D(10, 0), 1.0, 0.0, 1.0, K);
        assertEquals(fExact.getX(), fClamped.getX(), DELTA);
        assertEquals(fExact.getY(), fClamped.getY(), DELTA);
    }

    @Test
    void semanticAttraction_degenerateRange_doesNotThrow() {
        // minSim == maxSim → should not throw, uses 0.5 fallback
        assertDoesNotThrow(() -> ForceCalculator.semanticAttraction(
                new Vector2D(0, 0), new Vector2D(5, 0), 0.7, 0.7, 0.7, K));
    }

    // -----------------------------------------------------------------------
    // gravity() — argument validation
    // -----------------------------------------------------------------------

    @Test
    void gravity_nullPos_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ForceCalculator.gravity(null, new Vector2D(0, 0), 0.03));
    }

    @Test
    void gravity_nullCentroid_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ForceCalculator.gravity(new Vector2D(1, 0), null, 0.03));
    }

    @Test
    void gravity_negativeG_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ForceCalculator.gravity(new Vector2D(0, 0), new Vector2D(0, 0), -0.1));
    }

    // -----------------------------------------------------------------------
    // gravity() — direction and magnitude
    // -----------------------------------------------------------------------

    @Test
    void gravity_forceDirectedTowardCentroid() {
        // Node at (10,0), centroid at (0,0): force must be in -x direction
        Vector2D f = ForceCalculator.gravity(new Vector2D(10, 0), Vector2D.ZERO, 0.1);
        assertTrue(f.getX() < 0, "force must pull node toward centroid");
        assertEquals(0.0, f.getY(), DELTA);
    }

    @Test
    void gravity_exactValues_horizontalOffset() {
        // pos=(5,0), centroid=(0,0), g=0.1 → force = (0-5)*0.1 = (-0.5, 0)
        Vector2D f = ForceCalculator.gravity(new Vector2D(5, 0), Vector2D.ZERO, 0.1);
        assertEquals(-0.5, f.getX(), DELTA);
        assertEquals( 0.0, f.getY(), DELTA);
    }

    @Test
    void gravity_exactValues_diagonalOffset() {
        // pos=(3,4), centroid=(0,0), g=0.2 → force = (-3*0.2, -4*0.2) = (-0.6, -0.8)
        Vector2D f = ForceCalculator.gravity(new Vector2D(3, 4), Vector2D.ZERO, 0.2);
        assertEquals(-0.6, f.getX(), DELTA);
        assertEquals(-0.8, f.getY(), DELTA);
    }

    @Test
    void gravity_centroidNotAtOrigin_exactValues() {
        // pos=(1,1), centroid=(4,5), g=1.0 → force = (3, 4)
        Vector2D f = ForceCalculator.gravity(new Vector2D(1, 1), new Vector2D(4, 5), 1.0);
        assertEquals(3.0, f.getX(), DELTA);
        assertEquals(4.0, f.getY(), DELTA);
    }

    @Test
    void gravity_gEqualsZero_returnsZeroForce() {
        Vector2D f = ForceCalculator.gravity(new Vector2D(5, 10), Vector2D.ZERO, 0.0);
        assertEquals(0.0, f.getX(), DELTA);
        assertEquals(0.0, f.getY(), DELTA);
    }

    @Test
    void gravity_nodeAtCentroid_returnsZeroForce() {
        Vector2D centroid = new Vector2D(3, 7);
        Vector2D f = ForceCalculator.gravity(centroid, centroid, 0.5);
        assertEquals(0.0, f.getX(), DELTA);
        assertEquals(0.0, f.getY(), DELTA);
    }

    @Test
    void gravity_proportionalToDistance_doublingOffsetDoublesForce() {
        Vector2D f1 = ForceCalculator.gravity(new Vector2D(5, 0), Vector2D.ZERO, 0.1);
        Vector2D f2 = ForceCalculator.gravity(new Vector2D(10, 0), Vector2D.ZERO, 0.1);
        assertEquals(f1.getX() * 2, f2.getX(), DELTA);
    }

    @Test
    void gravity_proportionalToG_doublingGDoublesForce() {
        Vector2D f1 = ForceCalculator.gravity(new Vector2D(5, 0), Vector2D.ZERO, 0.1);
        Vector2D f2 = ForceCalculator.gravity(new Vector2D(5, 0), Vector2D.ZERO, 0.2);
        assertEquals(f1.getX() * 2, f2.getX(), DELTA);
    }

    @Test
    void gravity_symmetry_oppositeSideOfCentroid_oppositeForce() {
        Vector2D centroid = new Vector2D(0, 0);
        Vector2D fRight = ForceCalculator.gravity(new Vector2D( 5, 0), centroid, 0.1);
        Vector2D fLeft  = ForceCalculator.gravity(new Vector2D(-5, 0), centroid, 0.1);
        assertEquals(fRight.getX(), -fLeft.getX(), DELTA);
        assertEquals(fRight.getY(), -fLeft.getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // repulsion() — additional formula verification
    // -----------------------------------------------------------------------

    @Test
    void repulsion_magnitudeScalesWithKSquared_doublingKQuadruplesForce() {
        // FR formula: magnitude = K²/d; doubling K → magnitude × 4
        Vector2D posA = new Vector2D(3, 0);
        double mag1 = ForceCalculator.repulsion(posA, Vector2D.ZERO, 5.0).magnitude();
        double mag2 = ForceCalculator.repulsion(posA, Vector2D.ZERO, 10.0).magnitude();
        assertEquals(mag1 * 4.0, mag2, DELTA);
    }

    @Test
    void repulsion_pureYAxis_exactValues() {
        // A=(0,3), B=(0,0): d=3, magnitude=K²/3; direction=(0,1)
        Vector2D f = ForceCalculator.repulsion(new Vector2D(0, 3), Vector2D.ZERO, K);
        double expectedMag = K * K / 3.0;
        assertEquals(0.0, f.getX(), DELTA);
        assertEquals(expectedMag, f.getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // semanticAttraction() — additional validation and formula
    // -----------------------------------------------------------------------

    @Test
    void semanticAttraction_negativeK_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ForceCalculator.semanticAttraction(
                        new Vector2D(0, 0), new Vector2D(1, 0), 0.5, 0.0, 1.0, -1.0));
    }

    @Test
    void semanticAttraction_diagonal_exactComponents() {
        // A=(0,0), B=(3,4): d=5, sNorm=1 → stiffness=1, idealLength=0
        // force = delta * stiffness * displacement / dist = (3,4) * 1 * 5 / 5 = (3,4)
        Vector2D f = ForceCalculator.semanticAttraction(
                new Vector2D(0, 0), new Vector2D(3, 4), 1.0, 0.0, 1.0, K);
        assertEquals(3.0, f.getX(), DELTA);
        assertEquals(4.0, f.getY(), DELTA);
    }

    @Test
    void semanticAttraction_degenerateRange_fallbackSNormIsHalf_exactForce() {
        // minSim==maxSim → sNorm=0.5, stiffness=0.5, idealLength=K*0.5=5
        // A=(0,0), B=(10,0): dist=10, displacement=5, force=0.5*5=2.5 in +x
        Vector2D f = ForceCalculator.semanticAttraction(
                new Vector2D(0, 0), new Vector2D(10, 0), 0.7, 0.7, 0.7, K);
        assertEquals(2.5, f.getX(), DELTA);
        assertEquals(0.0, f.getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // gravity() — magnitude formula
    // -----------------------------------------------------------------------

    @Test
    void gravity_forceMagnitude_equalsGTimesDistance() {
        // pos=(3,4), centroid=(0,0): distance=5, g=0.2 → magnitude=1.0
        Vector2D f = ForceCalculator.gravity(new Vector2D(3, 4), Vector2D.ZERO, 0.2);
        assertEquals(0.2 * 5.0, f.magnitude(), DELTA);
    }

    // -----------------------------------------------------------------------
    // Cross-method: combined force scenario
    // -----------------------------------------------------------------------

    @Test
    void combined_repulsionAndGravityOppose_noNaN() {
        // Ensure no NaN/Infinity when forces are added together
        Vector2D posA = new Vector2D(1, 0);
        Vector2D rep  = ForceCalculator.repulsion(posA, Vector2D.ZERO, K);
        Vector2D grav = ForceCalculator.gravity(posA, Vector2D.ZERO, 0.03);
        Vector2D total = rep.add(grav);

        assertFalse(Double.isNaN(total.getX()),      "combined force x must not be NaN");
        assertFalse(Double.isNaN(total.getY()),      "combined force y must not be NaN");
        assertFalse(Double.isInfinite(total.getX()), "combined force x must not be Infinite");
        assertFalse(Double.isInfinite(total.getY()), "combined force y must not be Infinite");
    }
}
