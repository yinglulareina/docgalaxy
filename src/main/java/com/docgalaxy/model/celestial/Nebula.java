package com.docgalaxy.model.celestial;

import com.docgalaxy.model.Sector;
import com.docgalaxy.model.Vector2D;
import com.docgalaxy.ui.ThemeManager;
import com.docgalaxy.util.AppConstants;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;

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

    /**
     * Renders a soft nebula cloud using multiple overlapping RadialGradientPaint ellipses.
     * Visibility: alpha = max(0, (0.6 - zoom) / 0.3)
     * Fully visible below zoom 0.3, fully gone above zoom 0.6.
     *
     * Assumes the camera AffineTransform has already been applied to g.
     */
    @Override
    public void draw(Graphics2D g, double zoom) {
        // Compute fade based on zoom level
        float nebulaAlpha = (float) Math.max(0.0,
            (AppConstants.NEBULA_SHOW_THRESHOLD - zoom) / 0.3);
        if (nebulaAlpha <= 0f) return;

        double cx   = position.getX();
        double cy   = position.getY();
        double r    = radius;
        Color  base = color;

        Composite original = g.getComposite();
        float effectiveAlpha = nebulaAlpha * alpha;

        // ----- Outer halo: 1.5x radius, very soft -----
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, effectiveAlpha));
        float outerR = (float)(r * 1.5);
        RadialGradientPaint outerPaint = new RadialGradientPaint(
            (float) cx, (float) cy, outerR,
            new float[]{0f, 0.6f, 1f},
            new Color[]{
                new Color(base.getRed(), base.getGreen(), base.getBlue(), 30),
                new Color(base.getRed(), base.getGreen(), base.getBlue(), 12),
                new Color(0, 0, 0, 0)
            }
        );
        g.setPaint(outerPaint);
        g.fillOval((int)(cx - outerR), (int)(cy - outerR),
                   (int)(outerR * 2), (int)(outerR * 2));

        // ----- Inner core: 1x radius, brighter -----
        RadialGradientPaint innerPaint = new RadialGradientPaint(
            (float) cx, (float) cy, (float) r,
            new float[]{0f, 0.65f, 1f},
            new Color[]{
                new Color(base.getRed(), base.getGreen(), base.getBlue(), 90),
                new Color(base.getRed(), base.getGreen(), base.getBlue(), 45),
                new Color(0, 0, 0, 0)
            }
        );
        g.setPaint(innerPaint);
        g.fillOval((int)(cx - r), (int)(cy - r), (int)(r * 2), (int)(r * 2));

        // ----- Sector label (centred on nebula) -----
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
            Math.min(1f, effectiveAlpha * 1.5f)));
        g.setColor(ThemeManager.TEXT_PRIMARY);
        g.setFont(ThemeManager.FONT_SECTOR_LABEL);
        FontMetrics fm = g.getFontMetrics();
        String label = sector.getLabel() != null ? sector.getLabel() : "";
        g.drawString(label,
            (int)(cx - fm.stringWidth(label) / 2.0),
            (int)(cy + fm.getAscent() / 2.0));

        g.setComposite(original);
    }

    @Override
    public String getTooltipText() {
        return sector.getLabel() + " (" + sector.getNoteCount() + " notes)";
    }
}
