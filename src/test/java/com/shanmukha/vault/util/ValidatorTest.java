package com.shanmukha.vault.util;

import com.shanmukha.vault.model.Entry;
import com.shanmukha.vault.model.EntryType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatorTest {

    private static final String VALID_LABEL = "GitHub";
    private static final String VALID_USERNAME = "alice";
    private static final String VALID_PASSWORD = "fake-password";

    private static Entry entry(String id, String label) {
        return new Entry(id, label, "user", "fake-password", "", "", 1L, 1L,
                EntryType.LOGIN, false, "", false, "");
    }

    @Test
    void aValidEntryHasNoErrors() {
        assertThat(Validator.validateEntry(
                VALID_LABEL, VALID_USERNAME, VALID_PASSWORD, "https://example.test", "note"))
                .isEmpty();
    }

    @Test
    void labelIsRequiredAndLengthCheckedAfterTrimming() {
        assertThat(Validator.validateEntry(null, VALID_USERNAME, VALID_PASSWORD, null, null))
                .contains("Label is required.");
        assertThat(Validator.validateEntry("   ", VALID_USERNAME, VALID_PASSWORD, null, null))
                .contains("Label is required.");

        String maxLabel = "a".repeat(Validator.LABEL_MAX);
        assertThat(Validator.validateEntry(maxLabel, VALID_USERNAME, VALID_PASSWORD, null, null))
                .isEmpty();
        assertThat(Validator.validateEntry("  " + maxLabel + "  ", VALID_USERNAME,
                VALID_PASSWORD, null, null)).isEmpty();

        String tooLong = "a".repeat(Validator.LABEL_MAX + 1);
        assertThat(Validator.validateEntry(tooLong, VALID_USERNAME, VALID_PASSWORD, null, null))
                .contains("Label must be 64 characters or fewer.");
    }

    @Test
    void usernameIsRequiredAndLimited() {
        assertThat(Validator.validateEntry(VALID_LABEL, null, VALID_PASSWORD, null, null))
                .contains("Username is required.");
        assertThat(Validator.validateEntry(VALID_LABEL, "  ", VALID_PASSWORD, null, null))
                .contains("Username is required.");

        String maxUsername = "u".repeat(Validator.USERNAME_MAX);
        assertThat(Validator.validateEntry(VALID_LABEL, maxUsername, VALID_PASSWORD, null, null))
                .isEmpty();
        assertThat(Validator.validateEntry(VALID_LABEL, maxUsername + "x", VALID_PASSWORD,
                null, null)).contains("Username must be 128 characters or fewer.");
    }

    @Test
    void passwordIsRequiredAndLimited() {
        assertThat(Validator.validateEntry(VALID_LABEL, VALID_USERNAME, null, null, null))
                .contains("Password is required.");
        assertThat(Validator.validateEntry(VALID_LABEL, VALID_USERNAME, "", null, null))
                .contains("Password is required.");

        String maxPassword = "p".repeat(Validator.PASSWORD_MAX);
        assertThat(Validator.validateEntry(VALID_LABEL, VALID_USERNAME, maxPassword, null, null))
                .isEmpty();
        assertThat(Validator.validateEntry(VALID_LABEL, VALID_USERNAME, maxPassword + "x",
                null, null)).contains("Password must be 256 characters or fewer.");
    }

    @Test
    void urlAndNoteAreOptionalWithLimits() {
        assertThat(Validator.validateEntry(VALID_LABEL, VALID_USERNAME, VALID_PASSWORD, null, null))
                .isEmpty();
        assertThat(Validator.validateEntry(VALID_LABEL, VALID_USERNAME, VALID_PASSWORD,
                "u".repeat(Validator.URL_MAX), "n".repeat(Validator.NOTE_MAX))).isEmpty();

        assertThat(Validator.validateEntry(VALID_LABEL, VALID_USERNAME, VALID_PASSWORD,
                "u".repeat(Validator.URL_MAX + 1), null))
                .contains("URL must be 256 characters or fewer.");
        assertThat(Validator.validateEntry(VALID_LABEL, VALID_USERNAME, VALID_PASSWORD, null,
                "n".repeat(Validator.NOTE_MAX + 1)))
                .contains("Note must be 1000 characters or fewer.");
    }

    @Test
    void duplicateLabelsAreRejectedCaseInsensitively() {
        List<Entry> existing = List.of(entry("id-1", "GitHub"));

        assertThat(Validator.validateEntry("github", VALID_USERNAME, VALID_PASSWORD,
                null, null, existing, null))
                .contains("An entry with this label already exists.");
        assertThat(Validator.validateEntry("  GitHub  ", VALID_USERNAME, VALID_PASSWORD,
                null, null, existing, null))
                .contains("An entry with this label already exists.");
    }

    @Test
    void editingAnEntryMayKeepItsOwnLabel() {
        List<Entry> existing = List.of(entry("id-1", "GitHub"), entry("id-2", "Gmail"));

        assertThat(Validator.validateEntry("GitHub", VALID_USERNAME, VALID_PASSWORD,
                null, null, existing, "id-1"))
                .isEmpty();
    }

    @Test
    void masterPasswordNeedsTwelveCharacters() {
        assertThat(Validator.validateMasterPassword(null))
                .contains("Master password must be at least 12 characters.");
        assertThat(Validator.validateMasterPassword("short".toCharArray()))
                .contains("Master password must be at least 12 characters.");
        assertThat(Validator.validateMasterPassword("a".repeat(11).toCharArray()))
                .contains("Master password must be at least 12 characters.");
    }

    @Test
    void weakMasterPasswordIsRejected() {
        assertThat(Validator.validateMasterPassword("aaaaaaaaaaaa".toCharArray()))
                .contains("Master password is too weak. Use a longer password with mixed characters.");
    }

    @Test
    void strongMasterPasswordIsAccepted() {
        assertThat(Validator.validateMasterPassword(
                "correct horse battery staple".toCharArray())).isEmpty();
        assertThat(Validator.validateMasterPassword(
                "Abcdefgh1234!xyz".toCharArray())).isEmpty();
    }

    @Test
    void masterPasswordConfirmationMustMatch() {
        char[] first = "Abcdefgh1234!xyz".toCharArray();
        char[] second = "Abcdefgh1234!xyz".toCharArray();
        char[] different = "Abcdefgh1234!xyy".toCharArray();

        assertThat(Validator.validateMasterPasswordConfirmation(first, second)).isEmpty();
        assertThat(Validator.validateMasterPasswordConfirmation(first, different))
                .contains("Passwords do not match.");
    }
}
