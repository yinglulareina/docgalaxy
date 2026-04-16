package com.docgalaxy.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DemoLoaderTest {

    // ----------------------------------------------------------------
    // Manifest is present on the classpath
    // ----------------------------------------------------------------
    @Test
    void manifest_isPresentOnClasspath() {
        InputStream is = getClass().getClassLoader()
            .getResourceAsStream("demo/manifest.txt");
        assertNotNull(is, "demo/manifest.txt must be on the classpath");
    }

    // ----------------------------------------------------------------
    // Manifest contains at least 15 entries
    // ----------------------------------------------------------------
    @Test
    void manifest_hasAtLeastFifteenEntries() throws IOException {
        InputStream is = getClass().getClassLoader()
            .getResourceAsStream("demo/manifest.txt");
        assertNotNull(is);
        String text = new String(is.readAllBytes());
        long count = text.lines()
            .map(String::trim)
            .filter(l -> !l.isEmpty() && !l.startsWith("#"))
            .count();
        assertTrue(count >= 15, "Manifest should list at least 15 demo notes, found: " + count);
    }

    // ----------------------------------------------------------------
    // Every file in the manifest is actually present as a resource
    // ----------------------------------------------------------------
    @Test
    void manifest_allFilesExistAsResources() throws IOException {
        InputStream manifest = getClass().getClassLoader()
            .getResourceAsStream("demo/manifest.txt");
        assertNotNull(manifest);
        List<String> files = new String(manifest.readAllBytes()).lines()
            .map(String::trim)
            .filter(l -> !l.isEmpty() && !l.startsWith("#"))
            .toList();

        for (String file : files) {
            String resource = "demo/" + file;
            InputStream res = getClass().getClassLoader().getResourceAsStream(resource);
            assertNotNull(res, "Resource missing: " + resource);
            res.close();
        }
    }

    // ----------------------------------------------------------------
    // extract() produces a directory with all expected files
    // ----------------------------------------------------------------
    @Test
    void extract_producesDirectoryWithAllFiles() throws IOException {
        Path demoRoot = DemoLoader.extract();

        assertTrue(Files.isDirectory(demoRoot), "extract() should return a directory");

        // Verify at least 15 .md files were extracted
        long mdCount;
        try (var stream = Files.walk(demoRoot)) {
            mdCount = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".md"))
                .count();
        }
        assertTrue(mdCount >= 15,
            "At least 15 .md files should be extracted, found: " + mdCount);

        // Cleanup
        try (var stream = Files.walk(demoRoot)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                  .forEach(p -> p.toFile().delete());
        }
    }

    // ----------------------------------------------------------------
    // Extracted files are above MIN_CONTENT_LENGTH
    // ----------------------------------------------------------------
    @Test
    void extract_filesAreAboveMinContentLength() throws IOException {
        Path demoRoot = DemoLoader.extract();

        try (var stream = Files.walk(demoRoot)) {
            List<Path> mdFiles = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".md"))
                .toList();

            for (Path file : mdFiles) {
                String content = Files.readString(file).strip();
                assertTrue(content.length() >= AppConstants.MIN_CONTENT_LENGTH,
                    file.getFileName() + " is too short to be indexed (" +
                    content.length() + " chars)");
            }
        } finally {
            // Cleanup
            try (var stream = Files.walk(demoRoot)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                      .forEach(p -> p.toFile().delete());
            }
        }
    }

    // ----------------------------------------------------------------
    // Each call to extract() produces a fresh independent directory
    // ----------------------------------------------------------------
    @Test
    void extract_eachCallGivesFreshDirectory() throws IOException {
        Path dir1 = DemoLoader.extract();
        Path dir2 = DemoLoader.extract();

        assertNotEquals(dir1, dir2, "Each extract() call should produce a distinct temp dir");

        // Cleanup both
        for (Path dir : new Path[]{dir1, dir2}) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                      .forEach(p -> p.toFile().delete());
            }
        }
    }
}
