# DocGalaxy

**DocGalaxy** is a Java desktop application that turns a folder of local notes into an interactive, AI-powered knowledge galaxy. Each note becomes a star; semantically similar notes cluster into constellations. You can explore your knowledge visually, search across notes, and navigate learning paths using a built-in RAG-based AI assistant.

---

## Features

| Feature | Description |
|---|---|
| **Semantic Galaxy** | Notes embedded via OpenAI or Ollama are positioned by meaning — similar notes appear close together |
| **3 Layout Modes** | Force-directed (physics simulation), radial (concentric rings), tree (dendrogram hierarchy) |
| **Live File Watching** | Changes to your notes folder are detected and re-embedded automatically (5s debounce) |
| **AI Navigator** | Ask the AI for a personalized study path; it highlights the relevant stars and draws a route |
| **Fulltext Search** | Real-time search across all indexed notes with highlighted results on the canvas |
| **Edge Relationships** | click the lines connecting stars to get LLM-generated relationship descriptions |
| **Incubator** | Files under 50 characters are quarantined until they have enough content to embed |
| **Dual AI Backend** | OpenAI (`text-embedding-3-small`, 1536-dim) or Ollama (`llama3`, 768-dim) |
| **Persistent Index** | Embeddings, clusters, and layout cache survive restarts; atomic writes prevent corruption |
| **Dark Theme** | FlatLaf dark skin with a custom galaxy color palette |

---

## Screenshots

> Open the app, select a folder of Markdown/text notes, and the galaxy renders automatically.

---

## Requirements

- **Java 17** or later
- **Maven 3.8+**
- An **OpenAI API key** (or a running [Ollama](https://ollama.ai) instance)

---

## Quick Start

### 1. Clone and build

```bash
git clone <repo-url>
cd docgalaxy
mvn compile
```

### 2. Run

```bash
mvn exec:java -Dexec.mainClass="com.docgalaxy.App"
```

Or build a self-contained fat JAR and run it directly:

```bash
mvn package
java -jar target/docgalaxy-fat.jar
```

### 3. First launch

A welcome dialog appears. Either:
- **Select a knowledge base folder** — any folder containing `.txt`, `.md`, or other text files
- **Open the demo** — loads a built-in sample knowledge base so you can explore immediately

### 4. Configure your AI provider

Open **Settings** (toolbar) and enter your API key and preferred model:

| Field | Default | Notes |
|---|---|---|
| Provider | `openai` | Also supports `ollama` |
| Model | `text-embedding-3-small` | For Ollama: any model you have pulled |
| Dimension | `1536` | Ollama typically uses 768 — **changing this triggers a full re-embed** |
| API Key | *(empty)* | Not required for Ollama |

Config is saved to `<knowledge-base>/.docgalaxy/config.json`.

---

## Usage

### Canvas navigation

| Action | Gesture |
|---|---|
| **Pan** | Click + drag |
| **Zoom** | Scroll wheel or trackpad pinch |
| **Hover a star** | Shows a preview card with the note snippet |
| **Click a star** | Highlights that note |
| **Click an edge** | Shows the relationship description between two notes |
| **Double-click a star** | Opens the note file in your default OS application |
| **Click empty space** | Clears all highlights |

### Sidebar panels

- **Search** — type to filter notes in real time; matching stars glow on the canvas
- **Sectors** — list of discovered topic clusters; click one to highlight its stars
- **Incubator** — notes too short to embed; add more content to graduate them
- **AI Navigator** — ask a question; the AI plans a study route and highlights it on the galaxy

### Layout modes

Switch layouts from the toolbar:

| Mode | Best for |
|---|---|
| **Force-directed** | General exploration; physics-based clustering by semantic similarity |
| **Radial** | Seeing how notes radiate from a central hub across concentric rings |
| **Tree** | Viewing the hierarchical dendrogram of clusters |

---

## Architecture

DocGalaxy follows a strict 4-layer architecture. Each layer depends only on layers below it — no upward imports.

```
┌─────────────────────────────────────────────────┐
│  Presentation  (com.docgalaxy.ui)               │
│  Swing + FlatLaf, GalaxyCanvas, Sidebar         │
├─────────────────────────────────────────────────┤
│  Layout Engine  (com.docgalaxy.layout)          │
│  ForceDirectedLayout, RadialLayout, TreeLayout  │
│  DimensionReducer (PCA), SpatialGrid            │
├─────────────────────────────────────────────────┤
│  AI Service  (com.docgalaxy.ai)                 │
│  EmbeddingProvider, ChatProvider, VectorDatabase│
│  ClusterStrategy, NavigatorService (RAG)        │
├─────────────────────────────────────────────────┤
│  Persistence  (com.docgalaxy.persistence)       │
│  AbstractStore → IndexStore, EmbeddingStore,    │
│  ClusterStore, ConfigStore, LayoutCacheStore    │
└─────────────────────────────────────────────────┘
```

### Key design decisions

- **All UI updates** go through `SwingUtilities.invokeLater()` — Swing is never touched from worker threads
- **API calls and layout** run in `SwingWorker` or `ExecutorService` — never on the Event Dispatch Thread
- **VectorDatabase** is a singleton (`VectorDatabase.getInstance()`)
- **Persistence** uses atomic writes: write to `.tmp` → back up old file → rename — crash-safe
- **Force-directed repulsion** uses a 10×10 `SpatialGrid` for O(n) complexity instead of naïve O(n²)
- **PCA** reduces embeddings to 2D initial positions before the force simulation runs

---

## Project Structure

```
src/
├── main/java/com/docgalaxy/
│   ├── App.java                   # Entry point
│   ├── ai/                        # Embedding, chat, vector DB, clustering, RAG navigator
│   │   ├── cluster/               # KMeans + hierarchical clustering
│   │   └── navigator/             # RAG-based AI Navigator (LearningStyle, RouteStep)
│   ├── layout/                    # Layout algorithms + PCA + force physics
│   ├── model/                     # Note, Sector, Edge, Vector2D, CelestialBody hierarchy
│   │   └── celestial/             # Star, Nebula, Constellation
│   ├── persistence/               # Atomic stores, AppConfig
│   ├── ui/                        # MainFrame, ThemeManager
│   │   ├── canvas/                # GalaxyCanvas, Camera, CanvasController
│   │   │   └── layer/             # RenderLayer impls (Background, Star, Edge, Overlay…)
│   │   ├── components/            # Sidebar, SearchPanel, NavigatorPanel, IncubatorPanel
│   │   └── dialogs/               # Settings, Welcome, Progress, Reconciliation dialogs
│   ├── util/                      # AppConstants, VectorMath, HashUtil, DemoLoader
│   └── watcher/                   # FileWatcher, KnowledgeBaseManager, FileChangeListener
└── test/java/com/docgalaxy/       # 967 JUnit 5 tests across 35 test files
```

---

## Configuration Reference

All settings live in `<kb-folder>/.docgalaxy/config.json`.

| Key | Default | Description |
|---|---|---|
| `embedding.provider` | `openai` | `openai` or `ollama` |
| `embedding.model` | `text-embedding-3-small` | Embedding model name |
| `embedding.dimension` | `1536` | Vector dimension (768 for Ollama) |
| `chat.provider` | `ollama` | `openai` or `ollama` |
| `chat.model` | `llama3` | Chat/completion model |
| `layout.defaultStrategy` | `force_directed` | `force_directed`, `radial`, or `tree` |
| `layout.forceDirected.knnK` | `8` | K-nearest neighbours for semantic attraction |
| `layout.forceDirected.maxIterations` | `300` | Force simulation iteration cap |
| `layout.forceDirected.gravityConstant` | `0.03` | Gravity pulling stars toward cluster centres |
| `fileWatch.debounceSeconds` | `5` | Delay before processing a file-change event |
| `fileWatch.minContentLength` | `50` | Min characters required to embed a note |
| `fileWatch.driftThreshold` | `0.95` | Cosine similarity below which a note is re-embedded |
| `learningStyle` | `OVERVIEW_FIRST` | AI Navigator learning style preference |

---

## Development

### Run tests

```bash
mvn test
```

967 tests, 35 test files. Tests use mock `EmbeddingProvider` — no real API calls.

### Key constants

Edit `AppConstants.java` to tune rendering thresholds, physics parameters, and persistence intervals without touching business logic.

### Adding a new layout

1. Implement `LayoutStrategy` in `com.docgalaxy.layout`
2. Register it in `LayoutManager`
3. Add a toolbar button in `ToolBar.java`

### Adding a new AI provider

1. Implement `EmbeddingProvider` and/or `ChatProvider` in `com.docgalaxy.ai`
2. Wire it up in `MainFrame` where providers are instantiated
3. Do **not** modify the interface files — they are shared contracts

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| [FlatLaf](https://github.com/JFormDesigner/FlatLaf) | 3.4 | Modern Swing dark theme |
| [Gson](https://github.com/google/gson) | 2.10.1 | JSON serialization for persistence |
| [JUnit Jupiter](https://junit.org/junit5/) | 5.10.0 | Unit testing |

All AI communication uses the JDK built-in `java.net.http.HttpClient` — no additional HTTP library required.

---

## License

This project is for educational and research purposes.
