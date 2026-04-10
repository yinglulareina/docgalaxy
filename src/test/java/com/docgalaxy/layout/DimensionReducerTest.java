package com.docgalaxy.layout;

import com.docgalaxy.model.Vector2D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DimensionReducer.
 *
 * <p>All tests use small-dimensional vectors (3-D or 4-D) so they run in
 * milliseconds while still exercising the full PCA path.  Geometric
 * assertions use a loose delta (1e-6) because floating-point power iteration
 * converges to machine precision but sign is arbitrary (±eigenvector is
 * equally valid).
 */
class DimensionReducerTest {

    private static final double DELTA = 1e-6;

    private DimensionReducer reducer;

    @BeforeEach
    void setUp() {
        reducer = new DimensionReducer();
    }

    // -----------------------------------------------------------------------
    // isTrained / state guards
    // -----------------------------------------------------------------------

    @Test
    void isTrained_beforeFit_returnsFalse() {
        assertFalse(reducer.isTrained());
    }

    @Test
    void isTrained_afterFit_returnsTrue() {
        reducer.fit(axisAlignedData());
        assertTrue(reducer.isTrained());
    }

    @Test
    void transform_beforeFit_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> reducer.transform(new double[]{1, 0, 0}));
    }

    @Test
    void transformAll_beforeFit_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> reducer.transformAll(List.of(new double[]{1, 0, 0})));
    }

    // -----------------------------------------------------------------------
    // fit() — argument validation
    // -----------------------------------------------------------------------

    @Test
    void fit_nullList_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> reducer.fit(null));
    }

    @Test
    void fit_emptyList_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> reducer.fit(List.of()));
    }

    @Test
    void fit_mismatchedDimensions_throwsIllegalArgumentException() {
        List<double[]> bad = List.of(new double[]{1, 0}, new double[]{1, 0, 0});
        assertThrows(IllegalArgumentException.class, () -> reducer.fit(bad));
    }

    // -----------------------------------------------------------------------
    // fit() + transform() — basic correctness
    // -----------------------------------------------------------------------

    /**
     * Data where all variance is along the x-axis:
     * points at (±1, 0, 0), (±2, 0, 0), (±3, 0, 0).
     * PC1 must be aligned with the x-axis; transform of mean → (0, 0).
     */
    @Test
    void transform_ofMean_returnsOrigin() {
        reducer.fit(axisAlignedData());

        // Mean of the data is [0, 0, 0] (symmetric around origin)
        Vector2D projected = reducer.transform(new double[]{0, 0, 0});

        assertEquals(0.0, projected.getX(), DELTA);
        assertEquals(0.0, projected.getY(), DELTA);
    }

    @Test
    void transform_pc1AlignedWithPrincipalAxis() {
        reducer.fit(axisAlignedData());
        double[][] pm = reducer.getProjectionMatrix();

        // PC1 must be ±[1, 0, 0]; |pm[0][0]| ≈ 1, others ≈ 0
        assertEquals(1.0, Math.abs(pm[0][0]), DELTA);
        assertEquals(0.0, Math.abs(pm[0][1]), DELTA);
        assertEquals(0.0, Math.abs(pm[0][2]), DELTA);
    }

    @Test
    void transform_projectedCoordinateReflectsOriginalVariance() {
        reducer.fit(axisAlignedData());

        // Point (3, 0, 0) should project further from origin than (1, 0, 0)
        Vector2D p3 = reducer.transform(new double[]{3, 0, 0});
        Vector2D p1 = reducer.transform(new double[]{1, 0, 0});

        assertTrue(Math.abs(p3.getX()) > Math.abs(p1.getX()),
                "larger x should project to larger PC1 coordinate");
    }

    @Test
    void transform_symmetricPoints_haveOppositeSign() {
        reducer.fit(axisAlignedData());

        Vector2D pos = reducer.transform(new double[]{2, 0, 0});
        Vector2D neg = reducer.transform(new double[]{-2, 0, 0});

        assertEquals(pos.getX(), -neg.getX(), DELTA);
        assertEquals(pos.getY(), -neg.getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // Eigenvector properties
    // -----------------------------------------------------------------------

    @Test
    void projectionMatrix_pc1IsUnitVector() {
        reducer.fit(axisAlignedData());
        double[][] pm = reducer.getProjectionMatrix();
        assertEquals(1.0, norm(pm[0]), DELTA);
    }

    @Test
    void projectionMatrix_pc2IsUnitVector() {
        reducer.fit(axisAlignedData());
        double[][] pm = reducer.getProjectionMatrix();
        assertEquals(1.0, norm(pm[1]), DELTA);
    }

    @Test
    void projectionMatrix_pc1AndPc2AreOrthogonal() {
        reducer.fit(randomData(50, 4, 7));
        double[][] pm = reducer.getProjectionMatrix();
        assertEquals(0.0, Math.abs(dot(pm[0], pm[1])), DELTA);
    }

    @Test
    void projectionMatrix_isDefensiveCopy_mutationDoesNotAffectModel() {
        reducer.fit(axisAlignedData());
        double[][] copy = reducer.getProjectionMatrix();
        copy[0][0] = 999.0;

        // Second call should return the original values
        assertNotEquals(999.0, reducer.getProjectionMatrix()[0][0]);
    }

    // -----------------------------------------------------------------------
    // transform() — argument validation
    // -----------------------------------------------------------------------

    @Test
    void transform_nullVector_throwsIllegalArgumentException() {
        reducer.fit(axisAlignedData());
        assertThrows(IllegalArgumentException.class, () -> reducer.transform(null));
    }

    @Test
    void transform_wrongDimension_throwsIllegalArgumentException() {
        reducer.fit(axisAlignedData()); // 3-D data
        assertThrows(IllegalArgumentException.class,
                () -> reducer.transform(new double[]{1, 0})); // 2-D input
    }

    // -----------------------------------------------------------------------
    // transformAll()
    // -----------------------------------------------------------------------

    @Test
    void transformAll_nullList_throwsIllegalArgumentException() {
        reducer.fit(axisAlignedData());
        assertThrows(IllegalArgumentException.class, () -> reducer.transformAll(null));
    }

    @Test
    void transformAll_emptyList_returnsEmptyList() {
        reducer.fit(axisAlignedData());
        List<Vector2D> result = reducer.transformAll(List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void transformAll_singleElement_matchesSingleTransform() {
        reducer.fit(axisAlignedData());
        double[] v = {1, 0, 0};

        List<Vector2D> batch = reducer.transformAll(List.of(v));
        Vector2D single = reducer.transform(v);

        assertEquals(single.getX(), batch.get(0).getX(), DELTA);
        assertEquals(single.getY(), batch.get(0).getY(), DELTA);
    }

    @Test
    void transformAll_preservesOrder() {
        reducer.fit(randomData(30, 4, 1));
        List<double[]> input = randomData(5, 4, 99);

        List<Vector2D> batch = reducer.transformAll(input);
        assertEquals(input.size(), batch.size());
        for (int i = 0; i < input.size(); i++) {
            Vector2D expected = reducer.transform(input.get(i));
            assertEquals(expected.getX(), batch.get(i).getX(), DELTA);
            assertEquals(expected.getY(), batch.get(i).getY(), DELTA);
        }
    }

    // -----------------------------------------------------------------------
    // needsRefit()
    // -----------------------------------------------------------------------

    @Test
    void needsRefit_beforeFit_alwaysTrue() {
        assertTrue(reducer.needsRefit(0));
        assertTrue(reducer.needsRefit(100));
    }

    @Test
    void needsRefit_sameCountAsFit_returnsFalse() {
        List<double[]> data = randomData(10, 3, 0);
        reducer.fit(data);
        assertFalse(reducer.needsRefit(10));
    }

    @Test
    void needsRefit_exactlyAtThreshold_returnsFalse() {
        // 20% change == threshold, NOT more-than, so false
        reducer.fit(randomData(10, 3, 0));
        assertFalse(reducer.needsRefit(12)); // +20%
        assertFalse(reducer.needsRefit(8));  // -20%
    }

    @Test
    void needsRefit_justOverThreshold_returnsTrue() {
        reducer.fit(randomData(10, 3, 0));
        assertTrue(reducer.needsRefit(13));  // +30%
        assertTrue(reducer.needsRefit(7));   // -30%
    }

    @Test
    void needsRefit_countDropsToZero_returnsTrue() {
        reducer.fit(randomData(10, 3, 0));
        assertTrue(reducer.needsRefit(0)); // 100% decrease
    }

    @Test
    void needsRefit_countIncreasesLargeAmount_returnsTrue() {
        reducer.fit(randomData(10, 3, 0));
        assertTrue(reducer.needsRefit(1000));
    }

    @Test
    void needsRefit_smallFluctuation_returnsFalse() {
        reducer.fit(randomData(100, 3, 0));
        assertFalse(reducer.needsRefit(105)); // +5%
        assertFalse(reducer.needsRefit(95));  // -5%
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    void fit_singleVector_doesNotThrow_andIsTrained() {
        assertDoesNotThrow(() -> reducer.fit(List.of(new double[]{1, 2, 3})));
        assertTrue(reducer.isTrained());
    }

    @Test
    void fit_singleVector_transformDoesNotThrow() {
        reducer.fit(List.of(new double[]{1, 2, 3}));
        assertDoesNotThrow(() -> reducer.transform(new double[]{1, 2, 3}));
    }

    @Test
    void fit_twoIdenticalVectors_doesNotThrow() {
        List<double[]> data = List.of(new double[]{1, 2, 3}, new double[]{1, 2, 3});
        assertDoesNotThrow(() -> reducer.fit(data));
        assertTrue(reducer.isTrained());
    }

    @Test
    void fit_canBeCalledMultipleTimes_lastFitWins() {
        reducer.fit(axisAlignedData());         // 6 vectors, dim=3
        reducer.fit(randomData(20, 4, 5)); // 20 vectors, dim=4

        assertTrue(reducer.isTrained());
        // needsRefit should now use 20 as the reference count
        assertFalse(reducer.needsRefit(20));
        assertTrue(reducer.needsRefit(30));
    }

    @Test
    void fit_highDimensionalData_completesWithoutError() {
        // Smoke test with 50 vectors × 128 dims
        assertDoesNotThrow(() -> reducer.fit(randomData(50, 128, 0)));
        assertTrue(reducer.isTrained());

        double[] query = new double[128];
        assertDoesNotThrow(() -> reducer.transform(query));
    }

    @Test
    void transformAll_largeHighDimBatch_resultsMatchIndividualTransforms() {
        reducer.fit(randomData(40, 16, 1));
        List<double[]> batch = randomData(20, 16, 2);

        List<Vector2D> results = reducer.transformAll(batch);
        assertEquals(20, results.size());
        for (int i = 0; i < 20; i++) {
            Vector2D expected = reducer.transform(batch.get(i));
            assertEquals(expected.getX(), results.get(i).getX(), DELTA);
            assertEquals(expected.getY(), results.get(i).getY(), DELTA);
        }
    }

    @Test
    void getProjectionMatrix_beforeFit_returnsNull() {
        assertNull(reducer.getProjectionMatrix());
    }

    @Test
    void getProjectionMatrix_hasCorrectShape_2xDim() {
        int dim = 5;
        reducer.fit(randomData(20, dim, 3));
        double[][] pm = reducer.getProjectionMatrix();

        assertNotNull(pm);
        assertEquals(2,   pm.length,    "outer dimension must be 2");
        assertEquals(dim, pm[0].length, "PC1 length must equal input dim");
        assertEquals(dim, pm[1].length, "PC2 length must equal input dim");
    }

    // -----------------------------------------------------------------------
    // Side-effect safety
    // -----------------------------------------------------------------------

    @Test
    void fit_doesNotModifyInputVectors() {
        double[] v1 = {1.0, 0.0, 0.0};
        double[] v2 = {2.0, 0.0, 0.0};
        double[] v1Copy = v1.clone();
        double[] v2Copy = v2.clone();

        reducer.fit(new ArrayList<>(List.of(v1, v2)));

        assertArrayEquals(v1Copy, v1, DELTA, "fit() must not mutate input vector 0");
        assertArrayEquals(v2Copy, v2, DELTA, "fit() must not mutate input vector 1");
    }

    @Test
    void transform_doesNotModifyInputVector() {
        reducer.fit(axisAlignedData());
        double[] input = {1.0, 0.0, 0.0};
        double[] inputCopy = input.clone();

        reducer.transform(input);

        assertArrayEquals(inputCopy, input, DELTA, "transform() must not mutate its input");
    }

    @Test
    void transformAll_doesNotModifyInputVectors() {
        reducer.fit(axisAlignedData());
        double[] v = {2.0, 0.0, 0.0};
        double[] vCopy = v.clone();
        List<double[]> list = new ArrayList<>(List.of(v));

        reducer.transformAll(list);

        assertArrayEquals(vCopy, v, DELTA, "transformAll() must not mutate input vectors");
    }

    // -----------------------------------------------------------------------
    // Exact-value assertions
    // -----------------------------------------------------------------------

    /**
     * Dataset: six points at x=1 or x=3 (y=0, z=0), mean=[2,0,0].
     * Centered: x∈{-1,+1}.  PC1=±[1,0,0], so transform([1,0,0]).x = ∓1
     * and transform([3,0,0]).x = ±1 — magnitude is exactly 1.
     */
    @Test
    void transform_exactMagnitude_nonZeroMeanDataset() {
        List<double[]> data = List.of(
                new double[]{1, 0, 0}, new double[]{3, 0, 0},
                new double[]{1, 0, 0}, new double[]{3, 0, 0},
                new double[]{1, 0, 0}, new double[]{3, 0, 0}
        );
        reducer.fit(data);

        Vector2D p1 = reducer.transform(new double[]{1, 0, 0});
        Vector2D p3 = reducer.transform(new double[]{3, 0, 0});

        assertEquals(1.0, Math.abs(p1.getX()), DELTA, "|PC1 coord of x=1| must be 1");
        assertEquals(1.0, Math.abs(p3.getX()), DELTA, "|PC1 coord of x=3| must be 1");
        // They must lie on opposite sides of the mean
        assertEquals(p1.getX(), -p3.getX(), DELTA, "x=1 and x=3 must project to opposite signs");
    }

    @Test
    void transform_meanOfDataset_isExactlyOrigin_nonZeroMean() {
        // Dataset with non-zero mean to make the centering step non-trivial
        List<double[]> data = List.of(
                new double[]{10, 5, 0}, new double[]{12, 5, 0},
                new double[]{ 8, 5, 0}, new double[]{10, 5, 0}
        );
        reducer.fit(data);

        // The mean is [10, 5, 0]; transforming it should yield (0, 0)
        Vector2D proj = reducer.transform(new double[]{10, 5, 0});
        assertEquals(0.0, proj.getX(), DELTA);
        assertEquals(0.0, proj.getY(), DELTA);
    }

    /**
     * For axis-aligned data all variance is on the x-axis, so the PC2
     * direction captures no variance.  Every point on the x-axis has a
     * y-projection of exactly 0.
     */
    @Test
    void transform_pc2Coordinate_isZeroForPointsOnPrincipalAxis() {
        reducer.fit(axisAlignedData());

        for (double x : new double[]{-3, -2, -1, 0, 1, 2, 3}) {
            Vector2D proj = reducer.transform(new double[]{x, 0, 0});
            assertEquals(0.0, proj.getY(), DELTA,
                    "PC2 coordinate must be 0 for point on x-axis, x=" + x);
        }
    }

    /**
     * PCA invariant: the projections of the centered training data onto each
     * principal component sum to zero (mean of projections == 0).
     */
    @Test
    void transform_sumOfAllProjectedTrainingPoints_isNearZero() {
        List<double[]> data = randomData(30, 4, 11);
        reducer.fit(data);

        double sumX = 0, sumY = 0;
        for (double[] v : data) {
            Vector2D p = reducer.transform(v);
            sumX += p.getX();
            sumY += p.getY();
        }
        assertEquals(0.0, sumX, 1e-8, "sum of PC1 projections must be 0");
        assertEquals(0.0, sumY, 1e-8, "sum of PC2 projections must be 0");
    }

    // -----------------------------------------------------------------------
    // transformAll return-type contract
    // -----------------------------------------------------------------------

    @Test
    void transformAll_returnsModifiableList() {
        reducer.fit(axisAlignedData());
        List<Vector2D> result = reducer.transformAll(List.of(new double[]{1, 0, 0}));

        // Must not throw — result should be a regular mutable list
        assertDoesNotThrow(() -> result.add(new Vector2D(0, 0)));
    }

    // -----------------------------------------------------------------------
    // needsRefit — re-fit updates the reference count
    // -----------------------------------------------------------------------

    @Test
    void needsRefit_afterSecondFitWithDifferentCount_usesNewFittedCount() {
        reducer.fit(randomData(10, 3, 0));   // fittedCount = 10
        reducer.fit(randomData(50, 3, 1));   // fittedCount = 50

        // 50 ± 20% = [40, 60] → not retrigger
        assertFalse(reducer.needsRefit(50), "same as new fitted count → false");
        assertFalse(reducer.needsRefit(60), "exactly +20% of 50 → false");
        assertTrue(reducer.needsRefit(65),  "+30% of 50 → true");
        // Old boundary (10 ± 20%) must no longer apply
        assertFalse(reducer.needsRefit(55), "55 is within 20% of 50, not of 10");
    }

    @Test
    void needsRefit_withNegativeCurrentCount_treatedAsLargeDecrease() {
        // Negative counts are not expected in normal usage, but the code must not throw
        reducer.fit(randomData(10, 3, 0));
        assertDoesNotThrow(() -> reducer.needsRefit(-1));
        // |(-1 - 10)| / 10 = 1.1 > 0.2 → true
        assertTrue(reducer.needsRefit(-1));
    }

    // -----------------------------------------------------------------------
    // fit with degenerate / edge-case data
    // -----------------------------------------------------------------------

    @Test
    void fit_allZeroVectors_doesNotThrow_andIsTrained() {
        List<double[]> zeros = List.of(
                new double[]{0, 0, 0},
                new double[]{0, 0, 0},
                new double[]{0, 0, 0}
        );
        assertDoesNotThrow(() -> reducer.fit(zeros));
        assertTrue(reducer.isTrained());
    }

    @Test
    void fit_allZeroVectors_transformDoesNotThrow() {
        reducer.fit(List.of(
                new double[]{0, 0, 0},
                new double[]{0, 0, 0}
        ));
        assertDoesNotThrow(() -> reducer.transform(new double[]{1, 2, 3}));
    }

    @Test
    void fit_dataWithVarianceInTwoDimensions_pc1CapturesMoreVarianceThanPc2() {
        // Points spread over two axes but more spread on x than y
        List<double[]> data = new ArrayList<>();
        Random rng = new Random(42);
        for (int i = 0; i < 40; i++) {
            // x has σ=3, y has σ=1
            data.add(new double[]{rng.nextGaussian() * 3, rng.nextGaussian()});
        }
        reducer.fit(data);

        // Variance captured by PC1 > variance captured by PC2
        // We verify this by checking that the sum of squared PC1 projections
        // exceeds the sum of squared PC2 projections.
        double varPC1 = 0, varPC2 = 0;
        for (double[] v : data) {
            Vector2D p = reducer.transform(v);
            varPC1 += p.getX() * p.getX();
            varPC2 += p.getY() * p.getY();
        }
        assertTrue(varPC1 > varPC2,
                "PC1 should capture more variance than PC2 when x-spread > y-spread");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Six 3-D points with variance only along the x-axis:
     * {±1, ±2, ±3} × {0} × {0}. Mean is the origin.
     */
    private static List<double[]> axisAlignedData() {
        return List.of(
                new double[]{ 1, 0, 0},
                new double[]{ 2, 0, 0},
                new double[]{ 3, 0, 0},
                new double[]{-1, 0, 0},
                new double[]{-2, 0, 0},
                new double[]{-3, 0, 0}
        );
    }

    /** Random Gaussian data with given seed for reproducibility. */
    private static List<double[]> randomData(int n, int dim, long seed) {
        Random rng = new Random(seed);
        List<double[]> data = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double[] v = new double[dim];
            for (int j = 0; j < dim; j++) v[j] = rng.nextGaussian();
            data.add(v);
        }
        return data;
    }

    private static double norm(double[] v) {
        double s = 0;
        for (double x : v) s += x * x;
        return Math.sqrt(s);
    }

    private static double dot(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}
