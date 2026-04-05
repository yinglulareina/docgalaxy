package com.docgalaxy.ai;

public class Neighbor {
    private final String noteId;
    private final double similarity;

    public Neighbor(String noteId, double similarity) {
        this.noteId = noteId;
        this.similarity = similarity;
    }

    public String getNoteId() { return noteId; }
    public double getSimilarity() { return similarity; }
}
