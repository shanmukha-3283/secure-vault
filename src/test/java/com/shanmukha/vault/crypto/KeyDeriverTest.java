package com.shanmukha.vault.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyDeriverTest {

    /** Fake test password only; never a real secret. */
    private static final char[] PASSWORD = "correct horse battery staple".toCharArray();

    @Test
    void saltIsSixteenRandomBytes() {
        byte[] first = KeyDeriver.newSalt();
        byte[] second = KeyDeriver.newSalt();

        assertThat(first).hasSize(KeyDeriver.SALT_LENGTH);
        assertThat(second).hasSize(KeyDeriver.SALT_LENGTH);
        assertThat(Arrays.equals(first, second)).isFalse();
    }

    @Test
    void derivedKeyIsAes256() throws Exception {
        SecretKey key = KeyDeriver.deriveKey(PASSWORD, KeyDeriver.newSalt());

        assertThat(key.getAlgorithm()).isEqualTo("AES");
        assertThat(key.getEncoded()).hasSize(KeyDeriver.KEY_LENGTH_BYTES);
    }

    @Test
    void samePasswordAndSaltProduceSameKey() throws Exception {
        byte[] salt = KeyDeriver.newSalt();

        SecretKey first = KeyDeriver.deriveKey(PASSWORD, salt);
        SecretKey second = KeyDeriver.deriveKey(PASSWORD, salt);

        assertThat(first.getEncoded()).containsExactly(second.getEncoded());
    }

    @Test
    void differentSaltsProduceDifferentKeys() throws Exception {
        SecretKey first = KeyDeriver.deriveKey(PASSWORD, KeyDeriver.newSalt());
        SecretKey second = KeyDeriver.deriveKey(PASSWORD, KeyDeriver.newSalt());

        assertThat(Arrays.equals(first.getEncoded(), second.getEncoded())).isFalse();
    }

    @Test
    void doesNotModifyTheCallersPasswordArray() throws Exception {
        char[] callerOwned = PASSWORD.clone();

        KeyDeriver.deriveKey(callerOwned, KeyDeriver.newSalt());

        assertThat(callerOwned).containsExactly(PASSWORD);
    }

    @Test
    void rejectsEmptyPasswordAndWrongSaltLength() {
        assertThatThrownBy(() -> KeyDeriver.deriveKey(new char[0], KeyDeriver.newSalt()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KeyDeriver.deriveKey(PASSWORD, new byte[8]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KeyDeriver.deriveKey(null, KeyDeriver.newSalt()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
