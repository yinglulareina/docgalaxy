package com.docgalaxy.layout;

import com.docgalaxy.ai.Neighbor;
import com.docgalaxy.model.Vector2D;
import java.util.List;

public class NodeData {
    private final String noteId;
    private final Vector2D initialPosition;
    private final String sectorId;
    private final List<Neighbor> neighbors;

    public NodeData(String noteId, Vector2D initialPosition, String sectorId, List<Neighbor> neighbors) {
        this.noteId = noteId;
        this.initialPosition = initialPosition;
        this.sectorId = sectorId;
        this.neighbors = neighbors;
    }

    public String getNoteId() { return noteId; }
    public Vector2D getInitialPosition() { return initialPosition; }
    public String getSectorId() { return sectorId; }
    public List<Neighbor> getNeighbors() { return neighbors; }
}
