package com.docgalaxy.util;

public final class AppConstants {
    private AppConstants() {}

    // File watching
    public static final int MIN_CONTENT_LENGTH = 50;
    public static final int DEBOUNCE_SECONDS = 5;
    public static final int RENAME_TIMEOUT_MS = 2000;

    // Embedding
    public static final double DRIFT_THRESHOLD = 0.95;

    // Layout — force directed
    public static final int MAX_ITERATIONS = 300;
    public static final double INITIAL_TEMPERATURE = 100.0;
    public static final double COOLING_FACTOR = 0.95;
    public static final double MIN_TEMPERATURE = 0.5;
    public static final double GRAVITY_CONSTANT = 0.03;
    public static final int KNN_K = 8;
    public static final int GRID_SIZE = 10;
    public static final double OVERLAP_PADDING = 2.0;

    // Rendering thresholds
    public static final double NEBULA_SHOW_THRESHOLD = 0.6;
    public static final double STAR_SHOW_THRESHOLD = 0.3;
    public static final double LABEL_SHOW_THRESHOLD = 0.8;
    public static final float SEARCH_MASK_ALPHA = 100f / 255f;
    public static final int SEARCH_GLOW_RADIUS_MULTIPLIER = 5;

    // Persistence
    public static final int FLUSH_INTERVAL_SECONDS = 30;
    public static final String DOT_DIR = ".docgalaxy";
    public static final byte[] EMBEDDING_MAGIC = "DGXY".getBytes();

    // PCA
    public static final double REFIT_THRESHOLD = 0.2;

    // UI
    public static final int DEFAULT_WINDOW_WIDTH = 1400;
    public static final int DEFAULT_WINDOW_HEIGHT = 900;
    public static final int SIDEBAR_WIDTH = 240;
    public static final int PREVIEW_CARD_WIDTH = 280;
    public static final int PREVIEW_CARD_HEIGHT = 160;
    public static final int PROGRESS_DIALOG_WIDTH = 320;
    public static final int PROGRESS_DIALOG_HEIGHT = 100;
    public static final int NAVIGATOR_PANEL_WIDTH = 210;

    // Embedding
    public static final int DEFAULT_EMBEDDING_DIMENSION = 1536;
}
