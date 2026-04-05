package com.docgalaxy.ai.cluster;

import com.docgalaxy.model.DendrogramNode;
import com.docgalaxy.model.Vector2D;
import java.util.List;

public class Cluster {
    private final Vector2D centroid;
    private final List<String> memberNoteIds;
    private final DendrogramNode dendrogram;

    public Cluster(Vector2D centroid, List<String> memberNoteIds, DendrogramNode dendrogram) {
        this.centroid = centroid;
        this.memberNoteIds = memberNoteIds;
        this.dendrogram = dendrogram;
    }

    public Vector2D getCentroid() { return centroid; }
    public List<String> getMemberNoteIds() { return memberNoteIds; }
    public DendrogramNode getDendrogram() { return dendrogram; }
}
