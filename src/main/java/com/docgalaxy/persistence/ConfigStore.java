package com.docgalaxy.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * Reads and writes config.json.
 *
 * API key encryption:
 *   - Key  = first 16 bytes of SHA-256(hostname)   → AES-128, machine-bound
 *   - Mode = AES/GCM/NoPadding, 12-byte random IV
 *   - Stored as "encrypted:base64(iv || ciphertext)"
 *   - If decryption fails (e.g. moved to another machine) → returns null,
 *     caller must re-prompt for the key.
 */
public class ConfigStore extends AbstractStore<AppConfig> {

    private static final Gson   GSON        = new GsonBuilder().setPrettyPrinting().create();
    private static final String ENC_PREFIX  = "encrypted:";
    private static final int    GCM_IV_LEN  = 12;
    private static final int    GCM_TAG_LEN = 128;   // bits

    public ConfigStore(Path storeDir) {
        super(storeDir.resolve("config.json"),
              storeDir.resolve("backup/config.json.bak"));
    }

    // ----------------------------------------------------------------
    // AbstractStore contract
    // ----------------------------------------------------------------

    @Override
    public AppConfig load() throws IOException {
        if (!Files.exists(filePath)) return new AppConfig();
        String json  = Files.readString(filePath, StandardCharsets.UTF_8);
        AppConfig cfg = GSON.fromJson(json, AppConfig.class);
        return cfg == null ? new AppConfig() : cfg.sanitize();
    }

    @Override
    public void save(AppConfig data) throws IOException {
        Files.createDirectories(filePath.getParent());
        if (backupPath != null) Files.createDirectories(backupPath.getParent());
        String json = GSON.toJson(data);
        atomicWrite(filePath, json.getBytes(StandardCharsets.UTF_8));
    }

    // ----------------------------------------------------------------
    // Convenience helpers
    // ----------------------------------------------------------------

    /** Load with fallback; never returns null (returns default config instead). */
    public AppConfig loadOrDefault() {
        AppConfig cfg = loadWithFallback();
        return cfg != null ? cfg.sanitize() : new AppConfig();
    }

    // ----------------------------------------------------------------
    // API key encryption / decryption (AES-128-GCM, machine-bound key)
    // ----------------------------------------------------------------

    /**
     * Encrypt a plaintext API key.
     * Returns a string of the form "encrypted:base64(iv||ciphertext)".
     */
    public static String encryptApiKey(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            byte[] key    = getMachineKey();
            byte[] iv     = new byte[GCM_IV_LEN];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_LEN, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv,         0, combined, 0,          iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length,  ciphertext.length);

            return ENC_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("API key encryption failed", e);
        }
    }

    /**
     * Decrypt an encrypted API key.
     * Returns null if decryption fails (wrong machine / corrupted data).
     */
    public static String decryptApiKey(String encrypted) {
        if (encrypted == null || !encrypted.startsWith(ENC_PREFIX)) return encrypted;
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted.substring(ENC_PREFIX.length()));
            byte[] iv         = Arrays.copyOfRange(combined, 0, GCM_IV_LEN);
            byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_LEN, combined.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(getMachineKey(), "AES"),
                new GCMParameterSpec(GCM_TAG_LEN, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;   // moved to another machine or key changed
        }
    }

    // ----------------------------------------------------------------
    // Machine-bound key derivation
    // ----------------------------------------------------------------

    private static byte[] getMachineKey() throws Exception {
        String machineId = getMachineId();
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha.digest(machineId.getBytes(StandardCharsets.UTF_8));
        return Arrays.copyOf(hash, 16);   // AES-128
    }

    private static String getMachineId() {
        Path idFile = Path.of(System.getProperty("user.home"), ".docgalaxy-machine-id");

        // 1. Try to read an existing persisted ID
        if (Files.exists(idFile)) {
            try {
                String id = Files.readString(idFile, StandardCharsets.UTF_8).trim();
                if (!id.isBlank()) return id;
            } catch (IOException ignored) { }
        }

        // 2. Generate a new UUID and persist it
        String newId = java.util.UUID.randomUUID().toString();
        try {
            Files.writeString(idFile, newId, StandardCharsets.UTF_8);
            return newId;
        } catch (IOException ignored) { }

        // 3. Secondary fallback: hostname
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) { }

        // 4. Last resort
        return "docgalaxy-fallback-machine-id";
    }
}
