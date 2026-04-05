package com.docgalaxy.model.celestial;

import com.docgalaxy.model.Vector2D;
import java.awt.Color;
import java.awt.Graphics2D;

public abstract class CelestialBody {
    protected String id;
    protected Vector2D position;
    protected double radius;
    protected Color color;

    protected CelestialBody(String id, Vector2D position, double radius, Color color) {
        this.id = id;
        this.position = position;
        this.radius = radius;
        this.color = color;
    }

    public String getId() { return id; }
    public Vector2D getPosition() { return position; }
    public void setPosition(Vector2D position) { this.position = position; }
    public double getRadius() { return radius; }
    public void setRadius(double radius) { this.radius = radius; }
    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }

    /** Hit test with 1.5x radius for easier clicking. */
    public boolean contains(Vector2D point) {
        return position.distanceTo(point) <= radius * 1.5;
    }

    /** Draw this body on the canvas. Subclasses implement differently. */
    public abstract void draw(Graphics2D g, double zoom);

    /** Text for preview card / tooltip. */
    public abstract String getTooltipText();
}
