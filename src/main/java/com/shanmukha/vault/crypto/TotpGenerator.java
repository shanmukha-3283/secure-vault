package com.shanmukha.vault.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * RFC 6238 time-based one-time password (TOTP) with HMAC-SHA1 and a
 * six-digit code — the scheme Google Authenticator uses by default.
 *
 * <p>Only JDK built-ins are used (matching the project's "no crypto invented
 * here, only JDK APIs" rule). The implementation is verified against the RFC
 * 6238 appendix-B test vector in {@code TotpGeneratorTest}.</p>
 */
public final class TotpGenerator {

    /** Length of one TOTP window in milliseconds. */
    public static final long STEP_MS = 30_000L;

    /** Digits in the displayed code. */
    private static final int DIGITS = 6;

    private TotpGenerator() {
        // Utility class: never instantiated.
    }

    /** Generates the 6-digit code for a Base32-encoded secret (no padding). */
    public static String generateBase32(String base32Secret, long currentTimeMillis) {
        return generate(decodeBase32(base32Secret), currentTimeMillis);
    }

    /** Generates the 6-digit code for a raw secret at a moment in time. */
    public static String generate(byte[] secret, long currentTimeMillis) {
        long timeStep = Math.floorDiv(currentTimeMillis, STEP_MS);
        byte[] message = new byte[8];
        long value = timeStep;
        for (int i = 7; i >= 0; i--) {
            message[i] = (byte) (value & 0xff);
            value >>= 8;
        }
        int truncated = truncate(hmacSha1(secret, message));
        return formatTruncated(truncated);
    }

    /** Seconds remaining in the current window (for the countdown ring). */
    public static int remainingSeconds(long currentTimeMillis) {
        long msIntoWindow = Math.floorMod(currentTimeMillis, STEP_MS);
        return (int) Math.ceil((STEP_MS - msIntoWindow) / 1000.0);
    }

    /**
     * Eight-digit variant (RFC 6238 appendix-B vectors use 8 digits). Kept
     * package-visible for the RFC test vector; the UI always shows 6 digits.
     */
    static String generate8(byte[] secret, long currentTimeMillis) {
        long timeStep = Math.floorDiv(currentTimeMillis, 30_000L);
        byte[] message = new byte[8];
        long value = timeStep;
        for (int i = 7; i >= 0; i--) {
            message[i] = (byte) (value & 0xff);
            value >>= 8;
        }
        int offset = 0;
        try {
            byte[] hash = hmacSha1(secret, message);
            offset = hash[hash.length - 1] & 0x0F;
            int v = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return "%08d".formatted(v % 100_000_000);
        } catch (RuntimeException e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Internals                                                          */
    /* ------------------------------------------------------------------ */

    private static byte[] hmacSha1(byte[] secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // JDK guarantees HmacSHA1; a bad key length is the caller's bug.
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    /** RFC 4226 dynamic truncation. */
    private static int truncate(byte[] hash) {
        int offset = hash[hash.length - 1] & 0x0F;
        int value = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        return value % pow10(8);
    }

    private static String formatTruncated(int value) {
        int mod = value % pow10(DIGITS);
        return ("%0" + DIGITS + "d").formatted(mod);
    }

    private static int pow10(int n) {
        int result = 1;
        for (int i = 0; i < n; i++) {
            result *= 10;
        }
        return result;
    }

    /** RFC 4648 Base32 decoder (uppercase, no padding). */
    public static byte[] decodeBase32(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Base32 secret is required.");
        }
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String input = text.trim().replace("=", "").toUpperCase(java.util.Locale.ROOT);
        int bits = 0;
        int buffer = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char c : input.toCharArray()) {
            int digit = alphabet.indexOf(c);
            if (digit < 0) {
                throw new IllegalArgumentException("Invalid Base32 character.");
            }
            buffer = (buffer << 5) | digit;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
