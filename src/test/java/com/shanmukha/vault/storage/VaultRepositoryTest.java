package com.shanmukha.vault.storage;

import com.shanmukha.vault.crypto.CryptoService;
import com.shanmukha.vault.crypto.VaultCryptoException;
import com.shanmukha.vault.model.Entry;
import com.shanmukha.vault.model.EntryType;
import com.shanmukha.vault.model.VaultData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VaultRepositoryTest {

    /** Fake test password only; never a real secret. */
    private static final char[] PASSWORD = "correct horse battery staple".toCharArray();

    @TempDir
    Path tempDir;

    private VaultRepository repository() {
        return new VaultRepository(tempDir.resolve("vault.dat"));
    }

    private static VaultData sampleData(String label) {
        return new VaultData(2, List.of(
                new Entry("id-1", label, "alice", "fake-password",
                        "https://example.test", "demo note", 1_000L, 2_000L,
                        EntryType.LOGIN, false, "", false, "")));
    }

    @Test
    void savesAndLoadsARoundTrip() throws Exception {
        VaultRepository repo = repository();
        VaultData data = sampleData("GitHub");

        repo.save(data, PASSWORD);

        assertThat(repo.exists()).isTrue();
        assertThat(repo.load(PASSWORD)).isEqualTo(data);
    }

    @Test
    void fileOnDiskIsEncryptedAndHasTheDocumentedMagic() throws Exception {
        VaultRepository repo = repository();
        repo.save(sampleData("GitHub"), PASSWORD);

        byte[] raw = Files.readAllBytes(repo.path());
        String asText = new String(raw, StandardCharsets.ISO_8859_1);

        assertThat(new String(raw, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("JVLT");
        assertThat(asText)
                .doesNotContain("GitHub")
                .doesNotContain("alice")
                .doesNotContain("fake-password")
                .doesNotContain("example.test");
    }

    @Test
    void wrongPasswordCannotLoadTheVault() throws Exception {
        VaultRepository repo = repository();
        repo.save(sampleData("GitHub"), PASSWORD);

        assertThatThrownBy(() -> repo.load("wrong horse battery staple!".toCharArray()))
                .isInstanceOf(VaultCryptoException.class);
    }

    @Test
    void tamperedFileCannotLoadTheVault() throws Exception {
        VaultRepository repo = repository();
        repo.save(sampleData("GitHub"), PASSWORD);

        byte[] raw = Files.readAllBytes(repo.path());
        raw[raw.length - 1] ^= 0x01;
        Files.write(repo.path(), raw);

        assertThatThrownBy(() -> repo.load(PASSWORD))
                .isInstanceOf(VaultCryptoException.class);
    }

    @Test
    void successfulSaveLeavesNoTempFileBehind() throws Exception {
        VaultRepository repo = repository();

        repo.save(sampleData("GitHub"), PASSWORD);

        assertThat(tempDir.resolve("vault.dat.tmp")).doesNotExist();
        assertThat(repo.path()).exists();
    }

    @Test
    void aSecondSaveReplacesThePreviousContents() throws Exception {
        VaultRepository repo = repository();

        repo.save(sampleData("Old"), PASSWORD);
        repo.save(sampleData("New"), PASSWORD);

        assertThat(repo.load(PASSWORD).entries()).extracting(Entry::label).containsExactly("New");
    }

    @Test
    void missingVaultFileIsReportedAsStorageError() {
        VaultRepository repo = repository();

        assertThatThrownBy(() -> repo.load(PASSWORD))
                .isInstanceOf(VaultStorageException.class)
                .hasMessageContaining("No vault file");
    }

    @Test
    void saveCreatesMissingParentDirectories() throws Exception {
        VaultRepository repo = new VaultRepository(tempDir.resolve("nested/dir/vault.dat"));

        repo.save(VaultData.empty(), PASSWORD);

        assertThat(repo.exists()).isTrue();
        assertThat(repo.load(PASSWORD)).isEqualTo(VaultData.empty());
    }

    @Test
    void decryptedButInvalidJsonIsReportedAsStorageError() throws Exception {
        VaultRepository repo = repository();
        byte[] notJson = CryptoService.encrypt(
                "this is not json".getBytes(StandardCharsets.UTF_8), PASSWORD);
        Files.write(repo.path(), notJson);

        assertThatThrownBy(() -> repo.load(PASSWORD))
                .isInstanceOf(VaultStorageException.class)
                .hasMessageContaining("corrupted");
    }

    @Test
    void defaultRepositoryUsesVaultDatInTheWorkingDirectory() {
        assertThat(new VaultRepository().path().getFileName().toString())
                .isEqualTo(VaultRepository.VAULT_FILE_NAME);
    }
}
