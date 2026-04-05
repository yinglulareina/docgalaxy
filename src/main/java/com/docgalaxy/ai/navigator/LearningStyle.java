package com.docgalaxy.ai.navigator;

public enum LearningStyle {
    LINEAR("Build from basics to advanced, step by step"),
    OVERVIEW_FIRST("Start with the big picture, then drill into details"),
    ASSOCIATIVE("Start from what is most familiar, radiate outward");

    private final String description;
    LearningStyle(String desc) { this.description = desc; }
    public String getDescription() { return description; }
}
