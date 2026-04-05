package com.docgalaxy.ai;

import com.docgalaxy.util.VectorMath;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VectorMathTest {
    @Test
    void testCosineSimilarityIdentical() {
        double[] a = {1, 2, 3};
        assertEquals(1.0, VectorMath.cosineSimilarity(a, a), 1e-10);
    }

    @Test
    void testCosineSimilarityOrthogonal() {
        double[] a = {1, 0};
        double[] b = {0, 1};
        assertEquals(0.0, VectorMath.cosineSimilarity(a, b), 1e-10);
    }

    @Test
    void testCosineSimilarityOpposite() {
        double[] a = {1, 2, 3};
        double[] b = {-1, -2, -3};
        assertEquals(-1.0, VectorMath.cosineSimilarity(a, b), 1e-10);
    }

    @Test
    void testDimensionMismatchThrows() {
        double[] a = {1, 2};
        double[] b = {1, 2, 3};
        assertThrows(IllegalArgumentException.class,
            () -> VectorMath.cosineSimilarity(a, b));
    }
}
