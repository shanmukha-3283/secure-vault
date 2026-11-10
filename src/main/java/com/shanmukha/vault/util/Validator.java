package com.shanmukha.vault.util;

import com.shanmukha.vault.crypto.StrengthChecker;
import com.shanmukha.vault.model.Entry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * All input validation rules live here. The UI only displays the
 * returned messages; it never re-implements validation.
 *
 * <p>Every method returns an immutable list of error messages. An empty list
 * means "valid". Field names come from the form, so messages are safe to show
 * to the user and never contain secrets.</p>
 */
public final class Validator {

    public static final int LABEL_MAX = 64;
    public static final int USERNAME_MAX = 128;
    public static final int PASSWORD_MAX = 256;
    public static final int URL_MAX = 256;
    public static final int NOTE_MAX = 1000;
    public static final int MASTER_PASSWORD_MIN = 12;

    private Validator() {
        // Utility class: never instantiated.
    }

    /** Validates entry fields without checking against existing entries. */
    public static List<String> validateEntry(String label, String username, String password,
                                             String url, String note) {
        return validateEntry(label, username, password, url, note, List.of(), null);
    }

    /**
     * Validates entry fields and rejects a duplicate label.
     *
     * @param existingEntries all entries currently in the vault
     * @param editedEntryId   id of the entry being edited (its own label is
     *                        allowed), or {@code null} when adding
     */
    public static List<String> validateEntry(String label, String username, String password,
                                             String url, String note,
                                             List<Entry> existingEntries, String editedEntryId) {
        List<String> errors = new ArrayList<>();

        String trimmedLabel = label == null ? "" : label.trim();
        if (trimmedLabel.isEmpty()) {
            errors.add("Label is required.");
        } else if (trimmedLabel.length() > LABEL_MAX) {
            errors.add("Label must be " + LABEL_MAX + " characters or fewer.");
        } else if (existingEntries != null && isDuplicateLabel(existingEntries, trimmedLabel, editedEntryId)) {
            errors.add("An entry with this label already exists.");
        }

        if (username == null || username.isBlank()) {
            errors.add("Username is required.");
        } else if (username.length() > USERNAME_MAX) {
            errors.add("Username must be " + USERNAME_MAX + " characters or fewer.");
        }

        if (password == null || password.isEmpty()) {
            errors.add("Password is required.");
        } else if (password.length() > PASSWORD_MAX) {
            errors.add("Password must be " + PASSWORD_MAX + " characters or fewer.");
        }

        if (url != null && url.length() > URL_MAX) {
            errors.add("URL must be " + URL_MAX + " characters or fewer.");
        }
        if (note != null && note.length() > NOTE_MAX) {
            errors.add("Note must be " + NOTE_MAX + " characters or fewer.");
        }

        return List.copyOf(errors);
    }

    /**
     * Validates a master password: at least 12 characters and not "weak".
     * Takes {@code char[]} so the secret is never a {@code String}.
     */
    public static List<String> validateMasterPassword(char[] masterPassword) {
        List<String> errors = new ArrayList<>();
        if (masterPassword == null || masterPassword.length < MASTER_PASSWORD_MIN) {
            errors.add("Master password must be at least "
                    + MASTER_PASSWORD_MIN + " characters.");
        } else if (StrengthChecker.assess(masterPassword).strength() == StrengthChecker.Strength.WEAK) {
            errors.add("Master password is too weak. Use a longer password with mixed characters.");
        }
        return List.copyOf(errors);
    }

    /** Checks that the two master-password fields match (create/change flows). */
    public static List<String> validateMasterPasswordConfirmation(char[] masterPassword,
                                                                  char[] confirmation) {
        if (!Arrays.equals(masterPassword, confirmation)) {
            return List.of("Passwords do not match.");
        }
        return List.of();
    }

    private static boolean isDuplicateLabel(List<Entry> entries, String trimmedLabel,
                                            String editedEntryId) {
        return entries.stream()
                .filter(entry -> !entry.id().equals(editedEntryId))
                .anyMatch(entry -> entry.label().equalsIgnoreCase(trimmedLabel));
    }
}
