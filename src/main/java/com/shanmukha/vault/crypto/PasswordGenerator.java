package com.shanmukha.vault.crypto;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates random entry passwords with {@link SecureRandom} (never
 * {@code java.util.Random}, which is predictable).
 *
 * <p>The generator guarantees at least one character from every selected
 * class (lower, upper, digit and optionally symbol) and then shuffles the
 * result, so the guaranteed characters are not stuck at the front.</p>
 *
 * <p>Returns a {@code String} because generated entry passwords are ordinary
 * data shown in a text field; the master password is the only secret kept as
 * {@code char[]}.</p>
 */
public final class PasswordGenerator {

    /** Shortest password the generator will produce. */
    public static final int MIN_LENGTH = 8;

    /** Longest password the vault accepts (see Validator.PASSWORD_MAX). */
    public static final int MAX_LENGTH = 256;

    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/";

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
        // Utility class: never instantiated.
    }

    /** Generates a password with symbols included. */
    public static String generate(int length) {
        return generate(length, true);
    }

    /**
     * Generates a password of the given length.
     *
     * @param length         number of characters, between {@value #MIN_LENGTH}
     *                       and {@value #MAX_LENGTH}
     * @param includeSymbols whether to allow punctuation characters
     */
    public static String generate(int length, boolean includeSymbols) {
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Password length must be between " + MIN_LENGTH + " and " + MAX_LENGTH + ".");
        }

        List<String> pools = new ArrayList<>();
        pools.add(LOWER);
        pools.add(UPPER);
        pools.add(DIGITS);
        if (includeSymbols) {
            pools.add(SYMBOLS);
        }

        StringBuilder everything = new StringBuilder();
        for (String pool : pools) {
            everything.append(pool);
        }
        String all = everything.toString();

        char[] password = new char[length];
        int index = 0;
        // One guaranteed character from each selected class.
        for (String pool : pools) {
            password[index++] = randomChar(pool);
        }
        while (index < length) {
            password[index++] = randomChar(all);
        }
        shuffle(password);
        return new String(password);
    }

    private static char randomChar(String pool) {
        return pool.charAt(RANDOM.nextInt(pool.length()));
    }

    /** Fisher-Yates shuffle using SecureRandom. */
    private static void shuffle(char[] chars) {
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
    }
}
