package com.shanmukha.vault.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoServiceTest {

    /** Fake test password only; never a real secret. */
    private static final char[] PASSWORD = "correct horse battery staple".toCharArray();
    private static final char[] WRONG_PASSWORD = "wrong horse battery staple!".toCharArray();
    private static final byte[] PLAINTEXT =
            "{\"entries\":[{\"label\":\"demo\"}]}".getBytes(StandardCharsets.UTF_8);

    @Test
    void encryptThenDecryptRoundTrips() throws Exception {
        byte[] vaultFile = CryptoService.encrypt(PLAINTEXT, PASSWORD);

        assertThat(CryptoService.decrypt(vaultFile, PASSWORD)).containsExactly(PLAINTEXT);
    }

    @Test
    void wrongPasswordIsRejectedByTheGcmTag() throws Exception {
        byte[] vaultFile = CryptoService.encrypt(PLAINTEXT, PASSWORD);

        assertThatThrownBy(() -> CryptoService.decrypt(vaultFile, WRONG_PASSWORD))
                .isInstanceOf(VaultCryptoException.class)
                .hasMessageContaining("Wrong master password");
    }

    @Test
    void tamperedCiphertextIsRejected() throws Exception {
        byte[] vaultFile = CryptoService.encrypt(PLAINTEXT, PASSWORD);
        vaultFile[vaultFile.length - 1] ^= 0x01;

        assertThatThrownBy(() -> CryptoService.decrypt(vaultFile, PASSWORD))
                .isInstanceOf(VaultCryptoException.class);
    }

    @Test
    void tamperedIvIsRejected() throws Exception {
        byte[] vaultFile = CryptoService.encrypt(PLAINTEXT, PASSWORD);
        vaultFile[CryptoService.HEADER_LENGTH - 1] ^= 0x01;

        assertThatThrownBy(() -> CryptoService.decrypt(vaultFile, PASSWORD))
                .isInstanceOf(VaultCryptoException.class);
    }

    @Test
    void unknownMagicIsRejected() throws Exception {
        byte[] vaultFile = CryptoService.encrypt(PLAINTEXT, PASSWORD);
        vaultFile[0] = 'X';

        assertThatThrownBy(() -> CryptoService.decrypt(vaultFile, PASSWORD))
                .isInstanceOf(InvalidVaultFormatException.class);
    }

    @Test
    void unsupportedVersionIsRejected() throws Exception {
        byte[] vaultFile = CryptoService.encrypt(PLAINTEXT, PASSWORD);
        vaultFile[4] = 2;

        assertThatThrownBy(() -> CryptoService.decrypt(vaultFile, PASSWORD))
                .isInstanceOf(InvalidVaultFormatException.class)
                .hasMessageContaining("version");
    }

    @Test
    void truncatedOrMissingDataIsRejected() {
        assertThatThrownBy(() -> CryptoService.decrypt(new byte[10], PASSWORD))
                .isInstanceOf(InvalidVaultFormatException.class);
        assertThatThrownBy(() -> CryptoService.decrypt(null, PASSWORD))
                .isInstanceOf(InvalidVaultFormatException.class);
    }

    @Test
    void everySaveUsesAFreshSaltAndIv() throws Exception {
        byte[] first = CryptoService.encrypt(PLAINTEXT, PASSWORD);
        byte[] second = CryptoService.encrypt(PLAINTEXT, PASSWORD);

        assertThat(Arrays.equals(
                CryptoService.extractIv(first), CryptoService.extractIv(second))).isFalse();
        assertThat(Arrays.equals(
                CryptoService.extractSalt(first), CryptoService.extractSalt(second))).isFalse();
    }

    @Test
    void fileUsesTheDocumentedHeaderLayout() throws Exception {
        byte[] vaultFile = CryptoService.encrypt(PLAINTEXT, PASSWORD);

        assertThat(new String(vaultFile, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("JVLT");
        assertThat(vaultFile[4]).isEqualTo((byte) 1);
        assertThat(CryptoService.HEADER_LENGTH).isEqualTo(33);
        assertThat(CryptoService.extractSalt(vaultFile)).hasSize(KeyDeriver.SALT_LENGTH);
        assertThat(CryptoService.extractIv(vaultFile)).hasSize(CryptoService.IV_LENGTH);
        assertThat(vaultFile.length)
                .isGreaterThanOrEqualTo(CryptoService.HEADER_LENGTH + 16);
    }

    @Test
    void plaintextIsNeverStoredInTheFile() throws Exception {
        String marker = "TOP-SECRET-MARKER";
        byte[] vaultFile = CryptoService.encrypt(
                marker.getBytes(StandardCharsets.UTF_8), PASSWORD);

        assertThat(new String(vaultFile, StandardCharsets.ISO_8859_1)).doesNotContain(marker);
    }

    @Test
    void rejectsEmptyOrMissingPasswordAndNullPlaintext() {
        assertThatThrownBy(() -> CryptoService.encrypt(PLAINTEXT, new char[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CryptoService.encrypt(null, PASSWORD))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
