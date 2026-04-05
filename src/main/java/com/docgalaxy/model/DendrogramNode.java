package com.docgalaxy.model;

public class DendrogramNode {
    private DendrogramNode left;
    private DendrogramNode right;
    private String noteId;          // non-null only for leaf
    private double mergeDistance;

    // Leaf constructor
    public static DendrogramNode leaf(String noteId) {
        DendrogramNode node = new DendrogramNode();
        node.noteId = noteId;
        return node;
    }

    // Internal node constructor
    public static DendrogramNode merge(DendrogramNode left, DendrogramNode right, double distance) {
        DendrogramNode node = new DendrogramNode();
        node.left = left;
        node.right = right;
        node.mergeDistance = distance;
        return node;
    }

    public boolean isLeaf() { return noteId != null; }
    public DendrogramNode getLeft() { return left; }
    public DendrogramNode getRight() { return right; }
    public String getNoteId() { return noteId; }
    public double getMergeDistance() { return mergeDistance; }
}
