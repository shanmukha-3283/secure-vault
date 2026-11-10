package com.shanmukha.vault.storage;

/**
 * Checked failure for reading or writing the vault file (I/O, JSON, or a
 * missing file). Messages never contain passwords or entry secrets.
 */
public class VaultStorageException extends Exception {

    public VaultStorageException(String message) {
        super(message);
    }

    public VaultStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
