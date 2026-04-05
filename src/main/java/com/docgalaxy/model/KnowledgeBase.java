package com.docgalaxy.model;

import java.nio.file.Path;
import java.util.*;

public class KnowledgeBase {
    private final Path rootPath;
    private final Map<String, Note> notes;      // noteId -> Note
    private List<Sector> sectors;

    public KnowledgeBase(Path rootPath) {
        this.rootPath = rootPath;
        this.notes = new LinkedHashMap<>();
        this.sectors = new ArrayList<>();
    }

    public Path getRootPath() { return rootPath; }

    public void addNote(Note note) { notes.put(note.getId(), note); }
    public void removeNote(String noteId) { notes.remove(noteId); }
    public Note getNote(String noteId) { return notes.get(noteId); }

    public Note getNoteByPath(String filePath) {
        return notes.values().stream()
            .filter(n -> n.getFilePath().equals(filePath))
            .findFirst().orElse(null);
    }

    public List<Note> getNotesByHash(String hash) {
        return notes.values().stream()
            .filter(n -> hash.equals(n.getContentHash()))
            .toList();
    }

    public Collection<Note> getAllNotes() { return notes.values(); }
    public int getNoteCount() { return notes.size(); }

    public List<Sector> getSectors() { return sectors; }
    public void setSectors(List<Sector> sectors) { this.sectors = sectors; }
}
