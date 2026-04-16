package com.docgalaxy.persistence;

import com.docgalaxy.model.Note;
import com.docgalaxy.model.NoteStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level wrapper for index.json.
 * Gson serializes/deserializes this directly.
 *
 * index.json format:
 * {
 *   "version": 1,
 *   "lastUpdated": "ISO-8601",
 *   "notes": [ { ...NoteRecord... } ]
 * }
 */
public class NoteIndex {
    private int version = 1;
    private String lastUpdated;
    private List<NoteRecord> notes = new ArrayList<>();

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }
    public List<NoteRecord> getNotes() { return notes; }
    public void setNotes(List<NoteRecord> notes) { this.notes = notes; }

    // ----------------------------------------------------------------
    // Inner record class – mirrors each note entry in JSON
    // ----------------------------------------------------------------
    public static class NoteRecord {
        private String id;
        private String filePath;
        private String fileName;
        private String contentHash;
        private String fileKey;          // nullable
        private long   fileSize;
        private long   lastModified;     // epoch millis
        private int    embeddingOffset;
        private String status;           // NoteStatus name
        private String createdAt;        // ISO-8601

        public NoteRecord() {}

        /** Convert a live Note to its JSON record form. */
        public static NoteRecord fromNote(Note note) {
            NoteRecord r = new NoteRecord();
            r.id              = note.getId();
            r.filePath        = note.getFilePath();
            r.fileName        = note.getFileName();
            r.contentHash     = note.getContentHash();
            r.fileKey         = note.getFileKey();
            r.fileSize        = note.getFileSize();
            r.lastModified    = note.getLastModified();
            r.embeddingOffset = note.getEmbeddingOffset();
            r.status          = note.getStatus() != null ? note.getStatus().name() : NoteStatus.ACTIVE.name();
            r.createdAt       = note.getCreatedAt() != null
                                  ? note.getCreatedAt().toString()
                                  : Instant.now().toString();
            return r;
        }

        /** Reconstruct a Note from its JSON record – defensive (missing fields → defaults). */
        public Note toNote() {
            Note note = new Note(
                id != null ? id : java.util.UUID.randomUUID().toString(),
                filePath != null ? filePath : "",
                fileName != null ? fileName : ""
            );
            note.setContentHash(contentHash);
            note.setFileKey(fileKey);
            note.setFileSize(fileSize);
            note.setLastModified(lastModified);
            note.setEmbeddingOffset(embeddingOffset);

            // Defensive status parsing
            NoteStatus resolvedStatus = NoteStatus.ACTIVE;
            if (status != null) {
                try { resolvedStatus = NoteStatus.valueOf(status); }
                catch (IllegalArgumentException ignored) {}
            }
            note.setStatus(resolvedStatus);

            // Defensive createdAt parsing
            Instant resolvedCreatedAt = Instant.now();
            if (createdAt != null) {
                try { resolvedCreatedAt = Instant.parse(createdAt); }
                catch (Exception ignored) {}
            }
            note.setCreatedAt(resolvedCreatedAt);
            return note;
        }

        // Getters & setters (required by Gson reflective serialization)
        public String getId()                       { return id; }
        public void   setId(String id)              { this.id = id; }
        public String getFilePath()                 { return filePath; }
        public void   setFilePath(String p)         { this.filePath = p; }
        public String getFileName()                 { return fileName; }
        public void   setFileName(String n)         { this.fileName = n; }
        public String getContentHash()              { return contentHash; }
        public void   setContentHash(String h)      { this.contentHash = h; }
        public String getFileKey()                  { return fileKey; }
        public void   setFileKey(String k)          { this.fileKey = k; }
        public long   getFileSize()                 { return fileSize; }
        public void   setFileSize(long s)           { this.fileSize = s; }
        public long   getLastModified()             { return lastModified; }
        public void   setLastModified(long m)       { this.lastModified = m; }
        public int    getEmbeddingOffset()          { return embeddingOffset; }
        public void   setEmbeddingOffset(int o)     { this.embeddingOffset = o; }
        public String getStatus()                   { return status; }
        public void   setStatus(String s)           { this.status = s; }
        public String getCreatedAt()                { return createdAt; }
        public void   setCreatedAt(String c)        { this.createdAt = c; }
    }
}
