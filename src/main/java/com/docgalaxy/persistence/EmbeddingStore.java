package com.docgalaxy.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Binary storage for all embedding vectors.
 *
 * File layout (embeddings.bin):
 *
 *   Header (16 bytes, big-endian):
 *     magic[4]     'D','G','X','Y'
 *     version[4]   = 1
 *     dimension[4] = 1536 (OpenAI) or 768 (Ollama)
 *     count[4]     number of vectors stored
 *
 *   For each of count entries:
 *     id_len[2]    unsigned short – byte length of the note UUID
 *     id_bytes[]   id_len bytes of UTF-8
 *     vector[]     dimension × 8 bytes (double, big-endian)
 *
 * Strategy: load ALL vectors into a Map<noteId, double[]> on startup (~12 MB
 * for 1000 × 1536-D notes). Flush the whole map to disk every 30 s or on
 * shutdown via PersistenceManager – no incremental writes.
 */
public class EmbeddingStore extends AbstractStore<Map<String, double[]>> {

    private static final int  VERSION     = 1;
    private static final int  HEADER_SIZE = 16;
    private static final byte[] MAGIC     = {'D', 'G', 'X', 'Y'};

    private final int dimension;

    /**
     * @param storeDir  the .docgalaxy directory for this knowledge base
     * @param dimension embedding dimension: 1536 for OpenAI, 768 for Ollama
     */
    public EmbeddingStore(Path storeDir, int dimension) {
        super(storeDir.resolve("embeddings.bin"),
              storeDir.resolve("backup/embeddings.bin.bak"));
        this.dimension = dimension;
    }

    public int getDimension() { return dimension; }

    // ----------------------------------------------------------------
    // AbstractStore contract
    // ----------------------------------------------------------------

    @Override
    public Map<String, double[]> load() throws IOException {
        Map<String, double[]> result = new HashMap<>();
        if (!Files.exists(filePath)) return result;

        byte[] data = Files.readAllBytes(filePath);
        if (data.length < HEADER_SIZE) return result;

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);

        // Validate magic
        byte[] magic = new byte[4];
        buf.get(magic);
        if (magic[0] != 'D' || magic[1] != 'G' || magic[2] != 'X' || magic[3] != 'Y') {
            throw new IOException("embeddings.bin: invalid magic bytes");
        }

        int storedVersion   = buf.getInt();
        int storedDimension = buf.getInt();
        int count           = buf.getInt();

        if (storedVersion != VERSION) {
            throw new IOException("embeddings.bin: unsupported version " + storedVersion);
        }
        if (storedDimension != dimension) {
            throw new IOException("embeddings.bin: dimension mismatch – expected "
                + dimension + ", found " + storedDimension
                + ". Re-embed required when switching providers.");
        }

        for (int i = 0; i < count; i++) {
            // Read note ID
            int idLen = buf.getShort() & 0xFFFF;
            byte[] idBytes = new byte[idLen];
            buf.get(idBytes);
            String noteId = new String(idBytes, StandardCharsets.UTF_8);

            // Read vector
            double[] vector = new double[storedDimension];
            for (int j = 0; j < storedDimension; j++) {
                vector[j] = buf.getDouble();
            }
            result.put(noteId, vector);
        }
        return result;
    }

    @Override
    public void save(Map<String, double[]> data) throws IOException {
        Files.createDirectories(filePath.getParent());
        if (backupPath != null) Files.createDirectories(backupPath.getParent());

        List<Map.Entry<String, double[]>> entries = List.copyOf(data.entrySet());
        int count = entries.size();

        // Pre-calculate byte lengths for all IDs
        byte[][] idBytesArr = new byte[count][];
        int idsTotalBytes = 0;
        for (int i = 0; i < count; i++) {
            idBytesArr[i] = entries.get(i).getKey().getBytes(StandardCharsets.UTF_8);
            idsTotalBytes += 2 + idBytesArr[i].length; // 2-byte length prefix
        }

        long totalSizeLong = (long) HEADER_SIZE + idsTotalBytes + (long) count * dimension * 8;
        if (totalSizeLong > Integer.MAX_VALUE) {
            throw new IOException("Embedding data too large to fit in a single buffer ("
                + totalSizeLong + " bytes)");
        }
        int totalSize = (int) totalSizeLong;

        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN);

        // Header
        buf.put(MAGIC);
        buf.putInt(VERSION);
        buf.putInt(dimension);
        buf.putInt(count);

        // Entries (interleaved id + vector)
        for (int i = 0; i < count; i++) {
            byte[] idBytes = idBytesArr[i];
            buf.putShort((short) idBytes.length);
            buf.put(idBytes);
            for (double v : entries.get(i).getValue()) {
                buf.putDouble(v);
            }
        }

        atomicWrite(filePath, buf.array());
    }

    // ----------------------------------------------------------------
    // Dimension mismatch detection (used at startup)
    // ----------------------------------------------------------------

    /**
     * Read only the header to check the stored dimension without loading all vectors.
     * Returns -1 if the file does not exist or has an invalid header.
     */
    public int readStoredDimension() {
        try {
            if (!Files.exists(filePath)) return -1;
            byte[] header = new byte[HEADER_SIZE];
            try (var in = Files.newInputStream(filePath)) {
                if (in.read(header) < HEADER_SIZE) return -1;
            }
            ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
            byte[] magic = new byte[4];
            buf.get(magic);
            if (magic[0] != 'D' || magic[1] != 'G') return -1;
            buf.getInt(); // skip version
            return buf.getInt(); // dimension
        } catch (IOException e) {
            return -1;
        }
    }
}
