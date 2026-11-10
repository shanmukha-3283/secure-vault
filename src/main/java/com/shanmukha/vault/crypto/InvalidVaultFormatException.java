package com.shanmukha.vault.crypto;

/**
 * Raised when a byte array is not a well-formed Secure Vault file:
 * wrong magic bytes, unsupported version, or truncated data.
 */
public final class InvalidVaultFormatException extends VaultCryptoException {

    public InvalidVaultFormatException(String message) {
        super(message);
    }
}
