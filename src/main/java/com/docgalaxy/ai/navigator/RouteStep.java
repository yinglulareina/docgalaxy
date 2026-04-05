package com.docgalaxy.ai.navigator;

public class RouteStep {
    private final String noteId;
    private final String reason;
    private final int order;

    public RouteStep(String noteId, String reason, int order) {
        this.noteId = noteId;
        this.reason = reason;
        this.order = order;
    }

    public String getNoteId() { return noteId; }
    public String getReason() { return reason; }
    public int getOrder() { return order; }
}
