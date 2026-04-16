package com.docgalaxy.model.celestial;

import com.docgalaxy.model.Note;
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

public class Star extends CelestialBody {
    private final Note note;
    private Sector sector;
    private int neighborCount;

    public Star(Note note, Vector2D position, double radius, Sector sector) {
        super(note.getId(), position, radius, sector.getColor());
        this.note = note;
        this.sector = sector;
    }

    public Note getNote() { return note; }
    public Sector getSector() { return sector; }
    public void setSector(Sector sector) { this.sector = sector; this.color = sector.getColor(); }
    public int getNeighborCount() { return neighborCount; }
    public void setNeighborCount(int count) { this.neighborCount = count; }

    /**
     * 3-layer rendering with Level of Detail (LOD):
     *   pixelRadius < 2  → single pixel
     *   pixelRadius 2-5  → plain filled circle
     *   pixelRadius > 5  → full glow + body gradient + label
     *
     * Assumes the camera AffineTransform has already been applied to g,
     * so all coordinates here are in world space.
     */
    @Override
    public void draw(Graphics2D g, double zoom) {
        double cx = position.getX();
        double cy = position.getY();
        double r  = radius;
        double pixelR = r * zoom;   // approximate screen-pixel radius

        // LOD: single pixel
        if (pixelR < 2.0) {
            g.setColor(color);
            g.fillRect((int) cx, (int) cy, 1, 1);
            return;
        }

        // LOD: simple circle, no glow
        if (pixelR < 5.0) {
            g.setColor(color);
            int d = Math.max(1, (int)(r * 2));
            g.fillOval((int)(cx - r), (int)(cy - r), d, d);
            return;
        }

        // ----- Layer 1: outer glow (radius*3, alpha 40 → 0) -----
        float glowR = (float)(r * 3.0);
        RadialGradientPaint glowPaint = new RadialGradientPaint(
            (float) cx, (float) cy, glowR,
            new float[]{0f, 1f},
            new Color[]{
                new Color(color.getRed(), color.getGreen(), color.getBlue(), 40),
                new Color(0, 0, 0, 0)
            }
        );
        g.setPaint(glowPaint);
        g.fillOval((int)(cx - glowR), (int)(cy - glowR),
                   (int)(glowR * 2), (int)(glowR * 2));

        // ----- Layer 2: body gradient (white → sectorColor → darker) -----
        // Offset centre slightly for a 3-D highlight effect
        RadialGradientPaint bodyPaint = new RadialGradientPaint(
            (float)(cx - r * 0.3), (float)(cy - r * 0.3), (float) r,
            new float[]{0f, 0.55f, 1f},
            new Color[]{Color.WHITE, color, color.darker()}
        );
        g.setPaint(bodyPaint);
        int d = (int)(r * 2);
        g.fillOval((int)(cx - r), (int)(cy - r), d, d);

        // ----- Layer 3: label (only when zoom > LABEL_VISIBILITY) -----
        if (zoom > AppConstants.LABEL_SHOW_THRESHOLD) {
            float labelAlpha = (float) Math.min(1.0, (zoom - AppConstants.LABEL_SHOW_THRESHOLD) / 0.3);
            Composite original = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, labelAlpha));
            g.setColor(ThemeManager.TEXT_PRIMARY);
            g.setFont(ThemeManager.FONT_SMALL);
            FontMetrics fm = g.getFontMetrics();
            String name = note.getFileName();
            int tw = fm.stringWidth(name);
            g.drawString(name,
                (int)(cx - tw / 2.0),
                (int)(cy + r + fm.getAscent() + 2));
            g.setComposite(original);
        }
    }

    @Override
    public String getTooltipText() {
        return note.getFileName();
    }
}
