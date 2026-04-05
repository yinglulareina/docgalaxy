package com.docgalaxy.model;

import java.util.Objects;

public final class Vector2D {
    private final double x;
    private final double y;
    public static final Vector2D ZERO = new Vector2D(0, 0);

    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() { return x; }
    public double getY() { return y; }

    public Vector2D add(Vector2D other) {
        return new Vector2D(x + other.x, y + other.y);
    }

    public Vector2D subtract(Vector2D other) {
        return new Vector2D(x - other.x, y - other.y);
    }

    public Vector2D scale(double factor) {
        return new Vector2D(x * factor, y * factor);
    }

    public Vector2D normalize() {
        double mag = magnitude();
        if (mag == 0) return ZERO;
        return new Vector2D(x / mag, y / mag);
    }

    public double magnitude() {
        return Math.sqrt(x * x + y * y);
    }

    public double distanceTo(Vector2D other) {
        return subtract(other).magnitude();
    }

    public double dotProduct(Vector2D other) {
        return x * other.x + y * other.y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vector2D v)) return false;
        return Double.compare(v.x, x) == 0 && Double.compare(v.y, y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return String.format("Vector2D(%.2f, %.2f)", x, y);
    }
}
