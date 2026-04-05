package com.docgalaxy.model.celestial;

import com.docgalaxy.model.Sector;
import com.docgalaxy.model.Vector2D;
import java.awt.Graphics2D;

public class Nebula extends CelestialBody {
    private final Sector sector;
    private float alpha;

    public Nebula(Sector sector, Vector2D position, double radius) {
        super("nebula-" + sector.getId(), position, radius, sector.getColor());
        this.sector = sector;
        this.alpha = 1.0f;
    }

    public Sector getSector() { return sector; }
    public float getAlpha() { return alpha; }
    public void setAlpha(float alpha) { this.alpha = alpha; }

    @Override
    public void draw(Graphics2D g, double zoom) {
        // TODO: Roommate implements RadialGradientPaint cloud rendering
    }

    @Override
    public String getTooltipText() {
        return sector.getLabel() + " (" + sector.getNoteCount() + " notes)";
    }
}
