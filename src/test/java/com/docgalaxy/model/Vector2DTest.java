package com.docgalaxy.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Vector2DTest {
    @Test
    void testAdd() {
        Vector2D a = new Vector2D(1, 2);
        Vector2D b = new Vector2D(3, 4);
        Vector2D result = a.add(b);
        assertEquals(4.0, result.getX());
        assertEquals(6.0, result.getY());
    }

    @Test
    void testSubtract() {
        Vector2D a = new Vector2D(5, 10);
        Vector2D b = new Vector2D(3, 4);
        Vector2D result = a.subtract(b);
        assertEquals(2.0, result.getX());
        assertEquals(6.0, result.getY());
    }

    @Test
    void testMagnitude() {
        Vector2D v = new Vector2D(3, 4);
        assertEquals(5.0, v.magnitude(), 1e-10);
    }

    @Test
    void testNormalize() {
        Vector2D v = new Vector2D(3, 4);
        Vector2D n = v.normalize();
        assertEquals(1.0, n.magnitude(), 1e-10);
        assertEquals(0.6, n.getX(), 1e-10);
        assertEquals(0.8, n.getY(), 1e-10);
    }

    @Test
    void testNormalizeZero() {
        assertEquals(Vector2D.ZERO, Vector2D.ZERO.normalize());
    }

    @Test
    void testDistanceTo() {
        Vector2D a = new Vector2D(0, 0);
        Vector2D b = new Vector2D(3, 4);
        assertEquals(5.0, a.distanceTo(b), 1e-10);
    }

    @Test
    void testDotProduct() {
        Vector2D a = new Vector2D(1, 2);
        Vector2D b = new Vector2D(3, 4);
        assertEquals(11.0, a.dotProduct(b), 1e-10);
    }

    @Test
    void testScale() {
        Vector2D v = new Vector2D(2, 3);
        Vector2D result = v.scale(2);
        assertEquals(4.0, result.getX());
        assertEquals(6.0, result.getY());
    }

    @Test
    void testEquals() {
        Vector2D a = new Vector2D(1.0, 2.0);
        Vector2D b = new Vector2D(1.0, 2.0);
        assertEquals(a, b);
    }

    @Test
    void testImmutability() {
        Vector2D original = new Vector2D(1, 2);
        Vector2D result = original.add(new Vector2D(3, 4));
        assertEquals(1.0, original.getX()); // original unchanged
        assertEquals(4.0, result.getX());
    }
}
