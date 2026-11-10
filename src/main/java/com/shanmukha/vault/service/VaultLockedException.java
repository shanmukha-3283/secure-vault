package com.shanmukha.vault.service;

/**
 * Raised when an operation needs an unlocked vault but the vault is locked.
 * Unchecked because it is a programming/usage error, not an expected I/O failure.
 */
public class VaultLockedException extends IllegalStateException {

    public VaultLockedException(String message) {
        super(message);
    }
}
