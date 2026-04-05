package com.docgalaxy.model;

public class Edge {
    private final String fromId;
    private final String toId;
    private final double similarity;

    public Edge(String fromId, String toId, double similarity) {
        this.fromId = fromId;
        this.toId = toId;
        this.similarity = similarity;
    }

    public String getFromId() { return fromId; }
    public String getToId() { return toId; }
    public double getSimilarity() { return similarity; }
}
