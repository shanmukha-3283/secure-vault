package com.shanmukha.vault.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.shanmukha.vault.crypto.CryptoService;
import com.shanmukha.vault.crypto.VaultCryptoException;
import com.shanmukha.vault.model.VaultData;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Reads and writes the encrypted vault file.
 *
 * <p>On disk only ciphertext ever appears:</p>
 * <pre>
 * plaintext JSON  --AES-256-GCM-->  vault.dat.tmp  --ATOMIC_MOVE-->  vault.dat
 * </pre>
 *
 * <p>The atomic move means a crash mid-save leaves the previous vault intact
 * instead of a half-written file. A new random IV is used by
 * {@link CryptoService} on every save.</p>
 */
public final class VaultRepository {

    /** Default vault file name used by the app. */
    public static final String VAULT_FILE_NAME = "vault.dat";

    private static final String TEMP_SUFFIX = ".tmp";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path vaultFile;

    /** Uses {@code vault.dat} in the current working directory. */
    public VaultRepository() {
        this(Path.of(VAULT_FILE_NAME));
    }

    /** Uses an explicit path (tests point this at a temp directory). */
    public VaultRepository(Path vaultFile) {
        if (vaultFile == null) {
            throw new IllegalArgumentException("Vault path must not be null.");
        }
        this.vaultFile = vaultFile;
    }

    public Path path() {
        return vaultFile;
    }

    public boolean exists() {
        return Files.exists(vaultFile);
    }

    /**
     * Decrypts and parses the vault file.
     *
     * @throws VaultStorageException if the file is missing, unreadable or its
     *                               decrypted content is not valid VaultData
     * @throws VaultCryptoException  if the GCM tag fails (wrong password or
     *                               tampered file) or the header is invalid
     */
    public VaultData load(char[] masterPassword) throws VaultStorageException, VaultCryptoException {
        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(vaultFile);
        } catch (NoSuchFileException e) {
            throw new VaultStorageException("No vault file exists yet.", e);
        } catch (IOException e) {
            throw new VaultStorageException("The vault file could not be read.", e);
        }

        byte[] plaintext = CryptoService.decrypt(fileBytes, masterPassword);

        try {
            return MAPPER.readValue(plaintext, VaultData.class);
        } catch (IOException e) {
            throw new VaultStorageException("The vault contents are corrupted.", e);
        }
    }

    /**
     * Serializes, encrypts and atomically replaces the vault file.
     * A fresh salt and IV are generated on every call.
     *
     * @throws VaultStorageException if the file cannot be written
     * @throws VaultCryptoException  if encryption fails
     */
    public void save(VaultData data, char[] masterPassword)
            throws VaultStorageException, VaultCryptoException {
        if (data == null) {
            throw new IllegalArgumentException("Vault data must not be null.");
        }

        byte[] plaintext;
        try {
            plaintext = MAPPER.writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            throw new VaultStorageException("The vault contents could not be serialized.", e);
        }

        // Encrypt before touching the disk so a failure leaves the old file intact.
        byte[] encrypted = CryptoService.encrypt(plaintext, masterPassword);

        Path tempFile = vaultFile.resolveSibling(vaultFile.getFileName() + TEMP_SUFFIX);
        try {
            createParentDirectory();
            Files.write(tempFile, encrypted,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            Files.move(tempFile, vaultFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            deleteQuietly(tempFile);
            throw new VaultStorageException(
                    "The file system does not support atomic vault saves.", e);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new VaultStorageException("The vault file could not be saved.", e);
        }
    }

    private void createParentDirectory() throws IOException {
        Path parent = vaultFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup only; the original failure is more important.
        }
    }
}
