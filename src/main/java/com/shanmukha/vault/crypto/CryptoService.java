package com.shanmukha.vault.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Authenticated encryption for the vault file using AES-256-GCM.
 *
 * <p>Why GCM and not CBC? GCM is authenticated encryption: it detects both
 * wrong passwords and tampering with the ciphertext/header, and it needs no
 * separate MAC. A wrong password is detected purely by the GCM tag check,
 * so no password hash is ever stored.</p>
 *
 * <p>File layout produced and consumed here:</p>
 * <pre>
 * [4]  magic "JVLT"
 * [1]  version = 1
 * [16] random salt
 * [12] random IV (new on every save)
 * [N]  ciphertext + 16-byte GCM tag
 * </pre>
 *
 * <p>The header (magic + version + salt + IV) is authenticated as AAD, so it
 * cannot be altered without breaking decryption.</p>
 */
public final class CryptoService {

    private static final byte[] MAGIC = {'J', 'V', 'L', 'T'};
    private static final int VERSION = 1;

    /** 12-byte IV is the standard size for AES-GCM. */
    public static final int IV_LENGTH = 12;

    /** 128-bit authentication tag. */
    public static final int GCM_TAG_BITS = 128;

    private static final int VERSION_LENGTH = 1;
    private static final int TAG_LENGTH_BYTES = GCM_TAG_BITS / 8;

    /** Offset of the IV inside the header (magic + version + salt). */
    private static final int IV_OFFSET = MAGIC.length + VERSION_LENGTH + KeyDeriver.SALT_LENGTH;

    /** Size of magic + version + salt + IV, used as AAD and as the header. */
    public static final int HEADER_LENGTH = IV_OFFSET + IV_LENGTH;

    private static final int MIN_FILE_LENGTH = HEADER_LENGTH + TAG_LENGTH_BYTES;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** SecureRandom is mandatory for IVs; never java.util.Random. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoService() {
        // Utility class: never instantiated.
    }

    /**
     * Encrypts arbitrary plaintext into the complete vault file bytes.
     * A fresh salt and a fresh IV are generated for every call, so saving
     * the same data twice never reuses an IV.
     */
    public static byte[] encrypt(byte[] plaintext, char[] masterPassword) throws VaultCryptoException {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext must not be null.");
        }
        if (masterPassword == null || masterPassword.length == 0) {
            throw new IllegalArgumentException("Master password must not be empty.");
        }

        byte[] salt = KeyDeriver.newSalt();
        byte[] iv = randomIv();
        byte[] header = buildHeader(salt, iv);

        try {
            SecretKey key = KeyDeriver.deriveKey(masterPassword, salt);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            // Authenticate the header so version/salt/IV tampering is detected.
            cipher.updateAAD(header);
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] result = new byte[header.length + ciphertext.length];
            System.arraycopy(header, 0, result, 0, header.length);
            System.arraycopy(ciphertext, 0, result, header.length, ciphertext.length);
            return result;
        } catch (GeneralSecurityException e) {
            throw new VaultCryptoException("The vault could not be encrypted.", e);
        }
    }

    /**
     * Decrypts and authenticates complete vault file bytes.
     *
     * @throws InvalidVaultFormatException if the magic, version or length is wrong
     * @throws VaultCryptoException        if the GCM tag fails (wrong password
     *                                     or tampered data)
     */
    public static byte[] decrypt(byte[] vaultFile, char[] masterPassword) throws VaultCryptoException {
        if (masterPassword == null || masterPassword.length == 0) {
            throw new IllegalArgumentException("Master password must not be empty.");
        }
        validateHeader(vaultFile);

        byte[] salt = extractSalt(vaultFile);
        byte[] iv = extractIv(vaultFile);
        byte[] ciphertext = Arrays.copyOfRange(vaultFile, HEADER_LENGTH, vaultFile.length);
        byte[] aad = Arrays.copyOf(vaultFile, HEADER_LENGTH);

        try {
            SecretKey key = KeyDeriver.deriveKey(masterPassword, salt);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad);
            return cipher.doFinal(ciphertext);
        } catch (AEADBadTagException e) {
            // Wrong password and tampering are indistinguishable by design.
            throw new VaultCryptoException(
                    "Wrong master password or the vault file was tampered with.", e);
        } catch (GeneralSecurityException e) {
            throw new VaultCryptoException("The vault could not be decrypted.", e);
        }
    }

    /** Extracts the per-save salt; package-private for tests and future storage code. */
    static byte[] extractSalt(byte[] vaultFile) {
        return Arrays.copyOfRange(vaultFile, MAGIC.length + VERSION_LENGTH, IV_OFFSET);
    }

    /** Extracts the per-save IV; package-private for tests and future storage code. */
    static byte[] extractIv(byte[] vaultFile) {
        return Arrays.copyOfRange(vaultFile, IV_OFFSET, HEADER_LENGTH);
    }

    private static byte[] randomIv() {
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    private static byte[] buildHeader(byte[] salt, byte[] iv) {
        byte[] header = new byte[HEADER_LENGTH];
        System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
        header[MAGIC.length] = (byte) VERSION;
        System.arraycopy(salt, 0, header, MAGIC.length + VERSION_LENGTH, salt.length);
        System.arraycopy(iv, 0, header, IV_OFFSET, iv.length);
        return header;
    }

    private static void validateHeader(byte[] vaultFile) throws InvalidVaultFormatException {
        if (vaultFile == null) {
            throw new InvalidVaultFormatException("Vault data is missing.");
        }
        if (vaultFile.length < MIN_FILE_LENGTH) {
            throw new InvalidVaultFormatException("Vault file is too short to be valid.");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (vaultFile[i] != MAGIC[i]) {
                throw new InvalidVaultFormatException("File is not a Secure Vault file.");
            }
        }
        int version = vaultFile[MAGIC.length] & 0xFF;
        if (version != VERSION) {
            throw new InvalidVaultFormatException("Unsupported vault version: " + version);
        }
    }
}
