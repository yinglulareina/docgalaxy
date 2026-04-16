package com.docgalaxy.persistence;

/**
 * Top-level config model for config.json.
 * All fields have safe defaults so a missing or partial config still works.
 *
 * config.json structure:
 * {
 *   "version": 1,
 *   "embedding": { "provider", "model", "dimension", "apiKey" },
 *   "chat":      { "provider", "model" },
 *   "layout":    { "defaultStrategy", "forceDirected": { ... } },
 *   "fileWatch": { "debounceSeconds", "minContentLength", "driftThreshold" },
 *   "learningStyle": "OVERVIEW_FIRST"
 * }
 */
public class AppConfig {

    private int    version       = 1;
    private String learningStyle = "OVERVIEW_FIRST";

    private EmbeddingConfig embedding = new EmbeddingConfig();
    private ChatConfig      chat      = new ChatConfig();
    private LayoutConfig    layout    = new LayoutConfig();
    private FileWatchConfig fileWatch = new FileWatchConfig();

    // ----------------------------------------------------------------
    // Nested config classes
    // ----------------------------------------------------------------

    public static class EmbeddingConfig {
        public String provider  = "openai";
        public String model     = "text-embedding-3-small";
        public int    dimension = 1536;
        public String apiKey    = null;   // stored as "encrypted:base64..." or plain
    }

    public static class ChatConfig {
        public String provider = "ollama";
        public String model    = "llama3";
        public String apiKey   = null;
    }

    public static class ForceDirectedConfig {
        public double gravityConstant = 0.03;
        public int    knnK            = 8;
        public int    maxIterations   = 300;
    }

    public static class LayoutConfig {
        public String              defaultStrategy = "force_directed";
        public ForceDirectedConfig forceDirected   = new ForceDirectedConfig();
    }

    public static class FileWatchConfig {
        public int    debounceSeconds    = 5;
        public int    minContentLength   = 50;
        public double driftThreshold     = 0.95;
    }

    // ----------------------------------------------------------------
    // Getters / setters
    // ----------------------------------------------------------------

    public int    getVersion()       { return version; }
    public void   setVersion(int v)  { this.version = v; }

    public String getLearningStyle()          { return learningStyle; }
    public void   setLearningStyle(String s)  { this.learningStyle = s; }

    public EmbeddingConfig getEmbedding()                   { return embedding; }
    public void            setEmbedding(EmbeddingConfig e)  { this.embedding = e; }

    public ChatConfig getChatConfig()              { return chat; }
    public void       setChatConfig(ChatConfig c)  { this.chat = c; }

    public LayoutConfig getLayout()                { return layout; }
    public void         setLayout(LayoutConfig l)  { this.layout = l; }

    public FileWatchConfig getFileWatch()                    { return fileWatch; }
    public void            setFileWatch(FileWatchConfig fw)  { this.fileWatch = fw; }

    /** Ensure no null sub-objects after Gson deserialization. */
    public AppConfig sanitize() {
        if (embedding == null)            embedding = new EmbeddingConfig();
        if (chat      == null)            chat      = new ChatConfig();
        if (layout    == null)            layout    = new LayoutConfig();
        if (layout.forceDirected == null) layout.forceDirected = new ForceDirectedConfig();
        if (fileWatch == null)            fileWatch = new FileWatchConfig();
        if (learningStyle == null)        learningStyle = "OVERVIEW_FIRST";
        return this;
    }
}
