package com.docgalaxy.watcher;

import java.nio.file.Path;

public interface FileChangeListener {
    void onFileCreated(Path path);
    void onFileModified(Path path);
    void onFileDeleted(Path path);
}
