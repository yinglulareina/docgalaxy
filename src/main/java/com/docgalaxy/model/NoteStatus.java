package com.docgalaxy.model;

public enum NoteStatus {
    ACTIVE,
    ORPHANED,
    PENDING_RENAME,
    /** File exists but content is too short to embed (< MIN_CONTENT_LENGTH chars). */
    INCUBATOR
}
