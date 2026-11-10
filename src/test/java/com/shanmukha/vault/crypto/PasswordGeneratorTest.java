package com.shanmukha.vault.crypto;

import com.shanmukha.vault.util.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordGeneratorTest {

    private static boolean isSymbol(char c) {
        return !Character.isLetterOrDigit(c);
    }

    @Test
    void respectsTheRequestedLength() {
        assertThat(PasswordGenerator.generate(8)).hasSize(8);
        assertThat(PasswordGenerator.generate(16)).hasSize(16);
        assertThat(PasswordGenerator.generate(64)).hasSize(64);
        assertThat(PasswordGenerator.generate(PasswordGenerator.MAX_LENGTH)).hasSize(256);
    }

    @Test
    void alwaysIncludesLowerUpperAndDigit() {
        for (int i = 0; i < 20; i++) {
            String password = PasswordGenerator.generate(16);
            assertThat(password).containsPattern("[a-z]");
            assertThat(password).containsPattern("[A-Z]");
            assertThat(password).containsPattern("[0-9]");
        }
    }

    @Test
    void includesSymbolsWhenAsked() {
        for (int i = 0; i < 20; i++) {
            assertThat(PasswordGenerator.generate(16, true).chars().anyMatch(
                    c -> isSymbol((char) c))).isTrue();
        }
    }

    @Test
    void excludesSymbolsWhenNotAsked() {
        for (int i = 0; i < 20; i++) {
            String password = PasswordGenerator.generate(32, false);
            assertThat(password.chars().anyMatch(c -> isSymbol((char) c))).isFalse();
        }
    }

    @Test
    void consecutiveCallsProduceDifferentPasswords() {
        String first = PasswordGenerator.generate(32);
        String second = PasswordGenerator.generate(32);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rejectsLengthsOutsideTheAllowedRange() {
        assertThatThrownBy(() -> PasswordGenerator.generate(7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordGenerator.generate(PasswordGenerator.MAX_LENGTH + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatedPasswordsPassMasterPasswordValidation() {
        String generated = PasswordGenerator.generate(16);

        assertThat(Validator.validateMasterPassword(generated.toCharArray())).isEmpty();
    }
}
