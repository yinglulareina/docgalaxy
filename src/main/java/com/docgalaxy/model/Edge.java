package com.docgalaxy.model;

public class Edge {
    private final String fromId;
    private final String toId;
    private final double similarity;

    /** LLM-generated one-sentence relationship description; null until lazily loaded. */
    private volatile String relationDescription;

    /** Whether this edge is currently highlighted by a click. */
    private boolean highlighted;

    public Edge(String fromId, String toId, double similarity) {
        this.fromId = fromId;
        this.toId = toId;
        this.similarity = similarity;
    }

    public String getFromId() { return fromId; }
    public String getToId()   { return toId; }
    public double getSimilarity() { return similarity; }

    public String getRelationDescription() { return relationDescription; }
    public void   setRelationDescription(String desc) { this.relationDescription = desc; }

    public boolean isHighlighted() { return highlighted; }
    public void    setHighlighted(boolean highlighted) { this.highlighted = highlighted; }
}
