package com.docgalaxy.model.celestial;

import com.docgalaxy.model.Note;
import com.docgalaxy.model.Sector;
import com.docgalaxy.model.Vector2D;
import java.awt.Graphics2D;

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

    @Override
    public void draw(Graphics2D g, double zoom) {
        // TODO: Ying implements 3-layer rendering (glow → body → label)
    }

    @Override
    public String getTooltipText() {
        return note.getFileName();
    }
}
