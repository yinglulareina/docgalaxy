package com.docgalaxy.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public abstract class AbstractStore<T> {
    protected final Path filePath;
    protected final Path backupPath;

    protected AbstractStore(Path filePath, Path backupPath) {
        this.filePath = filePath;
        this.backupPath = backupPath;
    }

    public abstract T load() throws IOException;
    public abstract void save(T data) throws IOException;

    protected void atomicWrite(Path target, byte[] content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, content);

        if (Files.exists(target) && backupPath != null) {
            Files.createDirectories(backupPath.getParent());
            Files.copy(target, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }

        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
    }

    protected T loadWithFallback() {
        try {
            return load();
        } catch (Exception e) {
            if (backupPath != null && Files.exists(backupPath)) {
                try {
                    Path original = this.filePath;
                    Files.copy(backupPath, original, StandardCopyOption.REPLACE_EXISTING);
                    return load();
                } catch (Exception e2) {
                    return null;
                }
            }
            return null;
        }
    }
}
