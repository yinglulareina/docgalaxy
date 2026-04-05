package com.docgalaxy.model.celestial;

import com.docgalaxy.model.Edge;
import com.docgalaxy.model.Vector2D;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

public class Constellation extends CelestialBody {
    private final List<Star> stars;
    private final List<Edge> edges;

    public Constellation(String id, List<Star> stars, List<Edge> edges, Vector2D center, Color color) {
        super(id, center, 0, color);
        this.stars = stars;
        this.edges = edges;
    }

    public List<Star> getStars() { return stars; }
    public List<Edge> getEdges() { return edges; }

    @Override
    public void draw(Graphics2D g, double zoom) {
        // TODO: draw edges between member stars
    }

    @Override
    public String getTooltipText() {
        return "Group of " + stars.size() + " related notes";
    }
}
