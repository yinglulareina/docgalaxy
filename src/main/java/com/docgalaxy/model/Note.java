package com.docgalaxy.model;

import java.time.Instant;
import java.util.UUID;

public class Note {
    private final String id;
    private String filePath;
    private String fileName;
    private String contentHash;
    private String fileKey;         // OS inode, nullable
    private long fileSize;
    private long lastModified;
    private NoteStatus status;
    private int embeddingOffset;
    private Instant createdAt;

    public Note(String filePath, String fileName) {
        this.id = UUID.randomUUID().toString();
        this.filePath = filePath;
        this.fileName = fileName;
        this.status = NoteStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    // Constructor for loading from persistence (with existing ID)
    public Note(String id, String filePath, String fileName) {
        this.id = id;
        this.filePath = filePath;
        this.fileName = fileName;
        this.status = NoteStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getFileKey() { return fileKey; }
    public void setFileKey(String fileKey) { this.fileKey = fileKey; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    public NoteStatus getStatus() { return status; }
    public void setStatus(NoteStatus status) { this.status = status; }
    public int getEmbeddingOffset() { return embeddingOffset; }
    public void setEmbeddingOffset(int embeddingOffset) { this.embeddingOffset = embeddingOffset; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Note{id=" + id + ", fileName=" + fileName + ", status=" + status + "}";
    }
}
