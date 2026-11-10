package com.shanmukha.vault.crypto;

/**
 * Checked failure raised when the vault cannot be encrypted or decrypted.
 *
 * <p>Messages never contain passwords, keys or plaintext. A failed GCM tag
 * check is reported here as "wrong password or tampered file" because
 * authenticated encryption cannot tell those two cases apart.</p>
 */
public class VaultCryptoException extends Exception {

    public VaultCryptoException(String message) {
        super(message);
    }

    public VaultCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
