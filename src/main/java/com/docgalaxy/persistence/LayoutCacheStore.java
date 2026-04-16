package com.docgalaxy.persistence;

import com.docgalaxy.model.Vector2D;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads and writes layout_cache.json.
 *
 * Stores per-strategy position maps so the layout engine doesn't
 * have to recompute from scratch on every launch.
 *
 * layout_cache.json format:
 * {
 *   "version": 1,
 *   "savedAt": "ISO-8601",
 *   "layouts": {
 *     "Galaxy":  { "noteId1": {"x": 1.0, "y": 2.0}, ... },
 *     "Tree":    { ... },
 *     "Radial":  { ... }
 *   }
 * }
 *
 * In-memory type: Map<strategyName, Map<noteId, Vector2D>>
 */
public class LayoutCacheStore extends AbstractStore<Map<String, Map<String, Vector2D>>> {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public LayoutCacheStore(Path storeDir) {
        super(storeDir.resolve("layout_cache.json"),
              storeDir.resolve("backup/layout_cache.json.bak"));
    }

    // ----------------------------------------------------------------
    // AbstractStore contract
    // ----------------------------------------------------------------

    @Override
    public Map<String, Map<String, Vector2D>> load() throws IOException {
        Map<String, Map<String, Vector2D>> result = new HashMap<>();
        if (!Files.exists(filePath)) return result;

        String    json = Files.readString(filePath, StandardCharsets.UTF_8);
        CacheFile file = GSON.fromJson(json, CacheFile.class);
        if (file == null || file.layouts == null) return result;

        for (Map.Entry<String, Map<String, double[]>> strategy : file.layouts.entrySet()) {
            Map<String, Vector2D> positions = new HashMap<>();
            for (Map.Entry<String, double[]> entry : strategy.getValue().entrySet()) {
                double[] xy = entry.getValue();
                if (xy != null && xy.length >= 2) {
                    positions.put(entry.getKey(), new Vector2D(xy[0], xy[1]));
                }
            }
            result.put(strategy.getKey(), positions);
        }
        return result;
    }

    @Override
    public void save(Map<String, Map<String, Vector2D>> data) throws IOException {
        Files.createDirectories(filePath.getParent());
        if (backupPath != null) Files.createDirectories(backupPath.getParent());

        CacheFile file = new CacheFile();
        file.savedAt = Instant.now().toString();
        file.layouts = new HashMap<>();

        for (Map.Entry<String, Map<String, Vector2D>> strategy : data.entrySet()) {
            Map<String, double[]> positions = new HashMap<>();
            for (Map.Entry<String, Vector2D> entry : strategy.getValue().entrySet()) {
                positions.put(entry.getKey(),
                    new double[]{entry.getValue().getX(), entry.getValue().getY()});
            }
            file.layouts.put(strategy.getKey(), positions);
        }

        atomicWrite(filePath, GSON.toJson(file).getBytes(StandardCharsets.UTF_8));
    }

    // ----------------------------------------------------------------
    // Convenience helpers
    // ----------------------------------------------------------------

    /** Get cached positions for a specific layout strategy, or empty map. */
    public Map<String, Vector2D> getLayout(String strategyName) {
        try {
            Map<String, Map<String, Vector2D>> all = load();
            return all.getOrDefault(strategyName, new HashMap<>());
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    /** Update cached positions for one strategy and save. */
    public void putLayout(String strategyName, Map<String, Vector2D> positions)
            throws IOException {
        Map<String, Map<String, Vector2D>> all;
        try {
            all = load();
        } catch (IOException e) {
            all = new HashMap<>();
        }
        all.put(strategyName, positions);
        save(all);
    }

    // ----------------------------------------------------------------
    // JSON record classes
    // ----------------------------------------------------------------

    private static class CacheFile {
        int version = 1;
        String savedAt;
        Map<String, Map<String, double[]>> layouts;
    }
}
