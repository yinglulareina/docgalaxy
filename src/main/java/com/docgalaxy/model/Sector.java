package com.docgalaxy.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Sector {
    private String id;
    private String label;
    private Color color;
    private Vector2D centroid;
    private List<String> noteIds;
    private DendrogramNode dendrogram;

    public Sector(String id, String label, Color color) {
        this.id = id;
        this.label = label;
        this.color = color;
        this.noteIds = new ArrayList<>();
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }
    public Vector2D getCentroid() { return centroid; }
    public void setCentroid(Vector2D centroid) { this.centroid = centroid; }
    public List<String> getNoteIds() { return noteIds; }
    public void setNoteIds(List<String> noteIds) { this.noteIds = noteIds; }
    public DendrogramNode getDendrogram() { return dendrogram; }
    public void setDendrogram(DendrogramNode dendrogram) { this.dendrogram = dendrogram; }
    public int getNoteCount() { return noteIds.size(); }
}
