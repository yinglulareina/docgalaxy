package com.docgalaxy.ai.navigator;

import java.util.List;
import java.util.stream.IntStream;

public class NavigationResult {
    private final List<RouteStep> route;
    private final String summary;
    private final String estimatedTime;
    private final boolean isFallback;

    public NavigationResult(List<RouteStep> route, String summary, String estimatedTime, boolean isFallback) {
        this.route = route;
        this.summary = summary;
        this.estimatedTime = estimatedTime;
        this.isFallback = isFallback;
    }

    public static NavigationResult fallback(List<String> topKNoteIds) {
        List<RouteStep> steps = IntStream.range(0, topKNoteIds.size())
            .mapToObj(i -> new RouteStep(topKNoteIds.get(i), "Semantically similar", i))
            .toList();
        return new NavigationResult(steps, "AI unavailable. Showing most relevant notes.", null, true);
    }

    public List<RouteStep> getRoute() { return route; }
    public String getSummary() { return summary; }
    public String getEstimatedTime() { return estimatedTime; }
    public boolean isFallback() { return isFallback; }
}
