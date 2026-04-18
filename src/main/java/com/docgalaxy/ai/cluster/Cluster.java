package com.docgalaxy.ai.cluster;

import com.docgalaxy.model.DendrogramNode;
import com.docgalaxy.model.Vector2D;

import java.awt.Color;
import java.util.List;

public class Cluster {
    private final Vector2D centroid;
    private final List<String> memberNoteIds;
    private final DendrogramNode dendrogram;

    /** Sector color assigned by the pipeline; persisted in clusters.json. Nullable. */
    private Color color;

    /** Sector label assigned by LLM; persisted in clusters.json to avoid re-calling LLM on cache hit. */
    private String label;

    public Cluster(Vector2D centroid, List<String> memberNoteIds, DendrogramNode dendrogram) {
        this.centroid = centroid;
        this.memberNoteIds = memberNoteIds;
        this.dendrogram = dendrogram;
    }

    public Vector2D getCentroid()                { return centroid; }
    public List<String> getMemberNoteIds()       { return memberNoteIds; }
    public DendrogramNode getDendrogram()        { return dendrogram; }
    public Color getColor()                      { return color; }
    public void setColor(Color color)            { this.color = color; }
    public String getLabel()                     { return label; }
    public void setLabel(String label)           { this.label = label; }
}
