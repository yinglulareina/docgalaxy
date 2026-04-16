package com.docgalaxy.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigStoreTest {

    @TempDir
    Path tempDir;

    private ConfigStore store;

    @BeforeEach
    void setUp() {
        store = new ConfigStore(tempDir);
    }

    // ----------------------------------------------------------------
    // load() on missing file → default config, no exception
    // ----------------------------------------------------------------
    @Test
    void load_missingFile_returnsDefaultConfig() throws IOException {
        AppConfig cfg = store.load();
        assertNotNull(cfg);
        assertNotNull(cfg.getEmbedding());
        assertNotNull(cfg.getChatConfig());
    }

    // ----------------------------------------------------------------
    // save() then load() round-trip
    // ----------------------------------------------------------------
    @Test
    void saveAndLoad_roundTrip() throws IOException {
        AppConfig cfg = new AppConfig();
        cfg.getEmbedding().provider  = "openai";
        cfg.getEmbedding().model     = "text-embedding-3-small";
        cfg.getEmbedding().dimension = 1536;
        cfg.getChatConfig().model    = "gpt-4o-mini";

        store.save(cfg);
        AppConfig loaded = store.load();

        assertEquals("openai",                  loaded.getEmbedding().provider);
        assertEquals("text-embedding-3-small",  loaded.getEmbedding().model);
        assertEquals(1536,                       loaded.getEmbedding().dimension);
        assertEquals("gpt-4o-mini",             loaded.getChatConfig().model);
    }

    // ----------------------------------------------------------------
    // encrypt + decrypt round-trip on same machine
    // ----------------------------------------------------------------
    @Test
    void encryptDecrypt_roundTrip() {
        String original  = "sk-test-api-key-1234567890";
        String encrypted = ConfigStore.encryptApiKey(original);

        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("encrypted:"));
        assertNotEquals(original, encrypted);

        String decrypted = ConfigStore.decryptApiKey(encrypted);
        assertEquals(original, decrypted);
    }

    // ----------------------------------------------------------------
    // Encrypt null/blank → null (not an exception)
    // ----------------------------------------------------------------
    @Test
    void encryptApiKey_nullOrBlank_returnsNull() {
        assertNull(ConfigStore.encryptApiKey(null));
        assertNull(ConfigStore.encryptApiKey(""));
        assertNull(ConfigStore.encryptApiKey("   "));
    }

    // ----------------------------------------------------------------
    // decrypt non-encrypted string → returns it unchanged
    // ----------------------------------------------------------------
    @Test
    void decryptApiKey_plaintext_returnedAsIs() {
        String plain = "sk-plaintext-key";
        assertEquals(plain, ConfigStore.decryptApiKey(plain));
    }

    // ----------------------------------------------------------------
    // decrypt null → null
    // ----------------------------------------------------------------
    @Test
    void decryptApiKey_null_returnsNull() {
        assertNull(ConfigStore.decryptApiKey(null));
    }

    // ----------------------------------------------------------------
    // decrypt corrupted → null (graceful)
    // ----------------------------------------------------------------
    @Test
    void decryptApiKey_corrupted_returnsNull() {
        String corrupted = "encrypted:not-valid-base64!!!";
        assertNull(ConfigStore.decryptApiKey(corrupted));
    }

    // ----------------------------------------------------------------
    // Each encrypt call produces a different ciphertext (random IV)
    // ----------------------------------------------------------------
    @Test
    void encryptApiKey_differentEachCall() {
        String key  = "sk-stable-key";
        String enc1 = ConfigStore.encryptApiKey(key);
        String enc2 = ConfigStore.encryptApiKey(key);
        assertNotEquals(enc1, enc2, "Two encryptions of the same key should differ (random IV)");
    }

    // ----------------------------------------------------------------
    // Backup created on second save
    // ----------------------------------------------------------------
    @Test
    void save_createsBackupOnSecondSave() throws IOException {
        store.save(new AppConfig());
        store.save(new AppConfig());

        Path backup = tempDir.resolve("backup/config.json.bak");
        assertTrue(backup.toFile().exists(), "Backup should exist after second save");
    }

    // ----------------------------------------------------------------
    // loadOrDefault never returns null
    // ----------------------------------------------------------------
    @Test
    void loadOrDefault_neverNull() {
        AppConfig cfg = store.loadOrDefault();
        assertNotNull(cfg);
    }
}
