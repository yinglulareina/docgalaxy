package com.docgalaxy.persistence;

import com.docgalaxy.ai.cluster.Cluster;
import com.docgalaxy.model.DendrogramNode;
import com.docgalaxy.model.Vector2D;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes clusters.json.
 *
 * clusters.json format:
 * {
 *   "version": 1,
 *   "savedAt": "ISO-8601",
 *   "clusters": [
 *     {
 *       "centroidX": 1.0, "centroidY": 2.0,
 *       "memberNoteIds": ["uuid1", "uuid2"],
 *       "dendrogram": { ... recursive DendrogramNode ... }
 *     }
 *   ]
 * }
 */
public class ClusterStore extends AbstractStore<List<Cluster>> {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public ClusterStore(Path storeDir) {
        super(storeDir.resolve("clusters.json"),
              storeDir.resolve("backup/clusters.json.bak"));
    }

    // ----------------------------------------------------------------
    // AbstractStore contract
    // ----------------------------------------------------------------

    @Override
    public List<Cluster> load() throws IOException {
        if (!Files.exists(filePath)) return new ArrayList<>();
        String json = Files.readString(filePath, StandardCharsets.UTF_8);
        ClusterFile file = GSON.fromJson(json, ClusterFile.class);
        if (file == null || file.clusters == null) return new ArrayList<>();
        return toClusters(file.clusters);
    }

    @Override
    public void save(List<Cluster> data) throws IOException {
        Files.createDirectories(filePath.getParent());
        if (backupPath != null) Files.createDirectories(backupPath.getParent());
        ClusterFile file = new ClusterFile();
        file.savedAt  = Instant.now().toString();
        file.clusters = toRecords(data);
        atomicWrite(filePath, GSON.toJson(file).getBytes(StandardCharsets.UTF_8));
    }

    // ----------------------------------------------------------------
    // Serialization helpers
    // ----------------------------------------------------------------

    private static List<ClusterRecord> toRecords(List<Cluster> clusters) {
        List<ClusterRecord> records = new ArrayList<>();
        for (Cluster c : clusters) {
            ClusterRecord r = new ClusterRecord();
            r.centroidX     = c.getCentroid().getX();
            r.centroidY     = c.getCentroid().getY();
            r.memberNoteIds = new ArrayList<>(c.getMemberNoteIds());
            r.dendrogram    = toNodeRecord(c.getDendrogram());
            records.add(r);
        }
        return records;
    }

    private static List<Cluster> toClusters(List<ClusterRecord> records) {
        List<Cluster> clusters = new ArrayList<>();
        for (ClusterRecord r : records) {
            Vector2D      centroid   = new Vector2D(r.centroidX, r.centroidY);
            DendrogramNode dendrogram = toNode(r.dendrogram);
            clusters.add(new Cluster(centroid,
                r.memberNoteIds != null ? r.memberNoteIds : new ArrayList<>(),
                dendrogram));
        }
        return clusters;
    }

    private static NodeRecord toNodeRecord(DendrogramNode node) {
        if (node == null) return null;
        NodeRecord r = new NodeRecord();
        r.noteId        = node.getNoteId();
        r.mergeDistance = node.getMergeDistance();
        r.left          = toNodeRecord(node.getLeft());
        r.right         = toNodeRecord(node.getRight());
        return r;
    }

    private static DendrogramNode toNode(NodeRecord r) {
        if (r == null) return null;
        if (r.left == null && r.right == null) {
            return DendrogramNode.leaf(r.noteId);
        }
        return DendrogramNode.merge(toNode(r.left), toNode(r.right), r.mergeDistance);
    }

    // ----------------------------------------------------------------
    // JSON record classes (Gson-friendly)
    // ----------------------------------------------------------------

    private static class ClusterFile {
        int version = 1;
        String savedAt;
        List<ClusterRecord> clusters;
    }

    private static class ClusterRecord {
        double       centroidX;
        double       centroidY;
        List<String> memberNoteIds;
        NodeRecord   dendrogram;
    }

    private static class NodeRecord {
        String     noteId;
        double     mergeDistance;
        NodeRecord left;
        NodeRecord right;
    }
}
