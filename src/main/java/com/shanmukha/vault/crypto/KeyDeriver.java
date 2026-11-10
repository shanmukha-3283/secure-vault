package com.shanmukha.vault.crypto;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Turns a master password into a 256-bit AES key with PBKDF2-HMAC-SHA256.
 *
 * <p>Why PBKDF2 with 600,000 iterations? PBKDF2 is deliberately slow, so an
 * attacker who steals the vault file must pay that cost for every password
 * guess. 600,000 is the OWASP-recommended order of magnitude for
 * PBKDF2-HMAC-SHA256 and is painful to brute-force but still quick for the
 * legitimate user (well under a second on a normal desktop).</p>
 *
 * <p>The master password is handled as {@code char[]} (never {@code String})
 * so it can be wiped. The caller owns its array and is responsible for
 * wiping it when the vault locks.</p>
 */
public final class KeyDeriver {

    /** OWASP-style iteration count; raising it makes brute-force harder. */
    public static final int ITERATIONS = 600_000;

    /** Random salt length in bytes; stored unencrypted in the file header. */
    public static final int SALT_LENGTH = 16;

    /** AES-256 key length in bits. */
    public static final int KEY_LENGTH_BITS = 256;

    /** AES-256 key length in bytes. */
    public static final int KEY_LENGTH_BYTES = KEY_LENGTH_BITS / 8;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String KEY_ALGORITHM = "AES";

    /** SecureRandom is the only acceptable RNG for cryptographic salts. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private KeyDeriver() {
        // Utility class: never instantiated.
    }

    /**
     * Creates a fresh 16-byte salt. A new salt makes precomputed rainbow
     * tables useless and ensures the same password yields a different key
     * for every vault.
     */
    public static byte[] newSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * Derives the AES key for the given password and salt.
     *
     * <p>The returned key lives only in memory. Its encoded bytes are copied
     * by {@link SecretKeySpec}, so the temporary buffer is wiped immediately;
     * the JVM cannot guarantee wiping of the key object itself, which is
     * documented in LIMITATIONS.md.</p>
     */
    public static SecretKey deriveKey(char[] masterPassword, byte[] salt) throws VaultCryptoException {
        if (masterPassword == null || masterPassword.length == 0) {
            throw new IllegalArgumentException("Master password must not be empty.");
        }
        if (salt == null || salt.length != SALT_LENGTH) {
            throw new IllegalArgumentException("Salt must be exactly " + SALT_LENGTH + " bytes.");
        }

        PBEKeySpec spec = new PBEKeySpec(masterPassword, salt, ITERATIONS, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] encoded = factory.generateSecret(spec).getEncoded();
            try {
                return new SecretKeySpec(encoded, KEY_ALGORITHM);
            } finally {
                Arrays.fill(encoded, (byte) 0);
            }
        } catch (GeneralSecurityException e) {
            // No secret is included in the message or the cause chain.
            throw new VaultCryptoException("This Java runtime cannot derive vault keys.", e);
        } finally {
            // Clears PBKDF2's internal password copy; the caller's array is untouched.
            spec.clearPassword();
        }
    }
}
