package com.docgalaxy.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts the bundled demo knowledge base from classpath resources to a
 * temporary directory and returns the root path.
 *
 * Demo notes live in src/main/resources/demo/ and are listed in
 * src/main/resources/demo/manifest.txt (one relative path per line).
 *
 * Usage:
 *   Path demoRoot = DemoLoader.extract();
 *   mainFrame.openKnowledgeBase(demoRoot);
 */
public final class DemoLoader {

    private static final Logger LOGGER     = Logger.getLogger(DemoLoader.class.getName());
    private static final String DEMO_BASE  = "demo/";
    private static final String MANIFEST   = DEMO_BASE + "manifest.txt";

    private DemoLoader() {}

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    /**
     * Extract the demo knowledge base to a fresh temp directory.
     *
     * @return Path to the temp directory containing the demo notes.
     * @throws IOException if extraction fails.
     */
    public static Path extract() throws IOException {
        Path tempDir = Files.createTempDirectory("docgalaxy-demo-");
        LOGGER.info("Extracting demo KB to: " + tempDir);

        List<String> files = readManifest();
        for (String relativePath : files) {
            copyResource(DEMO_BASE + relativePath, tempDir.resolve(relativePath));
        }

        LOGGER.info("Demo KB extracted: " + files.size() + " files");
        return tempDir;
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /** Read manifest.txt and return the list of file paths. */
    private static List<String> readManifest() throws IOException {
        InputStream is = DemoLoader.class.getClassLoader().getResourceAsStream(MANIFEST);
        if (is == null) {
            throw new IOException("Demo manifest not found on classpath: " + MANIFEST);
        }
        try (is) {
            String text = new String(is.readAllBytes());
            return text.lines()
                .map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .toList();
        }
    }

    /** Copy a single classpath resource to a destination path. */
    private static void copyResource(String resource, Path dest) throws IOException {
        InputStream is = DemoLoader.class.getClassLoader().getResourceAsStream(resource);
        if (is == null) {
            LOGGER.warning("Demo resource not found: " + resource);
            return;
        }
        try (is) {
            Files.createDirectories(dest.getParent());
            Files.write(dest, is.readAllBytes());
        }
    }
}
