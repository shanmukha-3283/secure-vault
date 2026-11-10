package com.shanmukha.vault.crypto;

/**
 * Rates a password as WEAK, MEDIUM or STRONG and explains why in one short
 * sentence the UI can show next to a strength bar.
 *
 * <p>This is a deliberately simple heuristic, not an entropy calculation:
 * it looks at length and how many character classes are used. A long
 * passphrase with spaces is treated as strong even with only two classes.</p>
 *
 * <p>Passwords are taken as {@code char[]} so the master password is never
 * held in an immutable {@code String}.</p>
 */
public final class StrengthChecker {

    public enum Strength {
        WEAK,
        MEDIUM,
        STRONG
    }

    /**
     * @param strength overall rating
     * @param reason   short human-readable explanation
     */
    public record Assessment(Strength strength, String reason) {
    }

    private static final String REASON_EMPTY = "Password is empty.";
    private static final String REASON_SHORT = "Use at least 8 characters; 12 or more is better.";
    private static final String REASON_VARIETY = "Mix upper case, lower case, digits and symbols.";
    private static final String REASON_PREDICTABLE = "Too short or too predictable.";
    private static final String REASON_MEDIUM = "Decent, but longer or more varied would be stronger.";
    private static final String REASON_STRONG = "Good length and character variety.";
    private static final String REASON_PASSPHRASE = "Long password with good variety.";

    private StrengthChecker() {
        // Utility class: never instantiated.
    }

    public static Assessment assess(char[] password) {
        int length = password == null ? 0 : password.length;
        if (length == 0) {
            return new Assessment(Strength.WEAK, REASON_EMPTY);
        }

        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;
        for (char c : password) {
            if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSymbol = true;
            }
        }
        int classes = (hasLower ? 1 : 0) + (hasUpper ? 1 : 0)
                + (hasDigit ? 1 : 0) + (hasSymbol ? 1 : 0);

        int score = 0;
        if (length >= 8) {
            score++;
        }
        if (length >= 12) {
            score++;
        }
        if (length >= 16) {
            score++;
        }
        if (classes >= 2) {
            score++;
        }
        if (classes >= 3) {
            score++;
        }
        if (classes >= 4) {
            score++;
        }

        if (length >= 20 && classes >= 2) {
            return new Assessment(Strength.STRONG, REASON_PASSPHRASE);
        }
        if (length >= 12 && score >= 5) {
            return new Assessment(Strength.STRONG, REASON_STRONG);
        }
        if (length >= 8 && score >= 3) {
            return new Assessment(Strength.MEDIUM, REASON_MEDIUM);
        }
        if (classes < 2) {
            return new Assessment(Strength.WEAK, REASON_VARIETY);
        }
        if (length < 8) {
            return new Assessment(Strength.WEAK, REASON_SHORT);
        }
        return new Assessment(Strength.WEAK, REASON_PREDICTABLE);
    }
}
