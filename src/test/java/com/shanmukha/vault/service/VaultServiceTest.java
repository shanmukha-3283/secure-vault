package com.shanmukha.vault.service;

import com.shanmukha.vault.crypto.VaultCryptoException;
import com.shanmukha.vault.model.Entry;
import com.shanmukha.vault.model.EntryType;
import com.shanmukha.vault.storage.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VaultServiceTest {

    /** Fake test password only; never a real secret. */
    private static final char[] PASSWORD = "correct horse battery staple".toCharArray();

    /** Second fake password used by the master-password-rotation tests. */
    private static final char[] NEW_PASSWORD = "yet another battery staple row".toCharArray();

    @TempDir
    Path tempDir;

    private VaultService service;

    @BeforeEach
    void setUp() {
        service = new VaultService(new VaultRepository(tempDir.resolve("vault.dat")));
    }

    private void newVault() throws Exception {
        service.createVault(PASSWORD);
    }

    @Test
    void createVaultCreatesTheFileAndUnlocks() throws Exception {
        newVault();

        assertThat(service.vaultExists()).isTrue();
        assertThat(service.isUnlocked()).isTrue();
        assertThat(service.entries()).isEmpty();
    }

    @Test
    void createVaultRefusesToOverwriteAnExistingVault() throws Exception {
        newVault();
        service.lock();

        assertThatThrownBy(() -> service.createVault(PASSWORD))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unlockWithTheCorrectPasswordReloadsSavedEntries() throws Exception {
        newVault();
        service.addEntry("GitHub", "alice", "fake-password", "https://github.com", "note",
                EntryType.LOGIN, "", false);
        service.lock();

        service.unlock(PASSWORD);

        assertThat(service.isUnlocked()).isTrue();
        assertThat(service.entries()).extracting(Entry::label).containsExactly("GitHub");
    }

    @Test
    void wrongPasswordKeepsTheVaultLocked() throws Exception {
        newVault();
        service.lock();

        assertThatThrownBy(() -> service.unlock("wrong horse battery staple!".toCharArray()))
                .isInstanceOf(VaultCryptoException.class);
        assertThat(service.isUnlocked()).isFalse();
    }

    @Test
    void lockBlocksAllVaultOperations() throws Exception {
        newVault();
        service.lock();

        assertThatThrownBy(service::entries).isInstanceOf(VaultLockedException.class);
        assertThatThrownBy(() -> service.search("git")).isInstanceOf(VaultLockedException.class);
        assertThatThrownBy(() -> service.addEntry("A", "u", "p", null, null,
                EntryType.LOGIN, "", false))
                .isInstanceOf(VaultLockedException.class);
    }

    @Test
    void addEntryPersistsAcrossLockAndUnlock() throws Exception {
        newVault();
        Entry added = service.addEntry("GitHub", "alice", "fake-password",
                "https://github.com", "demo note", EntryType.LOGIN, "", false);
        service.lock();

        service.unlock(PASSWORD);

        assertThat(service.entries()).containsExactly(added);
    }

    @Test
    void duplicateLabelsAreRejectedCaseInsensitively() throws Exception {
        newVault();
        service.addEntry("GitHub", "alice", "fake-password", null, null,
                EntryType.LOGIN, "", false);

        assertThatThrownBy(() -> service.addEntry("github", "bob", "fake-password", null, null,
                EntryType.LOGIN, "", false))
                .isInstanceOf(DuplicateLabelException.class);
        assertThatThrownBy(() -> service.addEntry("  GitHub  ", "bob", "fake-password", null, null,
                EntryType.LOGIN, "", false))
                .isInstanceOf(DuplicateLabelException.class);
        assertThat(service.entries()).hasSize(1);
    }

    @Test
    void updateChangesFieldsButKeepsIdAndCreatedAt() throws Exception {
        newVault();
        Entry original = service.addEntry("GitHub", "alice", "fake-password", null, null,
                EntryType.LOGIN, "", false);

        Entry updated = service.updateEntry(original.id(), "GitHub Work", "alice2",
                "another-fake-password", "https://github.com", "updated",
                EntryType.LOGIN, "", false, "");

        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.createdAt()).isEqualTo(original.createdAt());
        assertThat(updated.updatedAt()).isGreaterThanOrEqualTo(original.updatedAt());
        assertThat(updated.label()).isEqualTo("GitHub Work");
        assertThat(updated.username()).isEqualTo("alice2");
        assertThat(updated.password()).isEqualTo("another-fake-password");
        assertThat(updated.note()).isEqualTo("updated");
    }

    @Test
    void updateAllowsKeepingTheEntriesOwnLabel() throws Exception {
        newVault();
        Entry original = service.addEntry("GitHub", "alice", "fake-password", null, null,
                EntryType.LOGIN, "", false);

        Entry updated = service.updateEntry(original.id(), "GitHub", "alice2",
                "fake-password", null, null, EntryType.LOGIN, "", false, "");

        assertThat(updated.label()).isEqualTo("GitHub");
        assertThat(updated.username()).isEqualTo("alice2");
    }

    @Test
    void updateRejectsALabelUsedByAnotherEntry() throws Exception {
        newVault();
        Entry first = service.addEntry("GitHub", "alice", "fake-password", null, null,
                EntryType.LOGIN, "", false);
        service.addEntry("Gmail", "bob", "fake-password", null, null,
                EntryType.LOGIN, "", false);

        assertThatThrownBy(() -> service.updateEntry(first.id(), "gmail", "alice",
                "fake-password", null, null, EntryType.LOGIN, "", false, ""))
                .isInstanceOf(DuplicateLabelException.class);
    }

    @Test
    void updateAndDeleteRejectUnknownIds() throws Exception {
        newVault();

        assertThatThrownBy(() -> service.updateEntry("missing", "A", "u", "p", null, null,
                EntryType.LOGIN, "", false, ""))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.deleteEntry("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void softDeleteMovesEntryToTrashAndPersists() throws Exception {
        newVault();
        Entry entry = service.addEntry("GitHub", "alice", "fake-password", null, null,
                EntryType.LOGIN, "", false);

        service.deleteEntry(entry.id());
        service.lock();
        service.unlock(PASSWORD);

        assertThat(service.entries()).isEmpty();
        assertThat(service.trashCount()).isEqualTo(1);
        assertThat(service.allEntries()).extracting(Entry::deleted).containsExactly(true);
    }

    @Test
    void restoreEntryBringsItBackFromTrash() throws Exception {
        newVault();
        Entry entry = service.addEntry("GitHub", "alice", "fake-password", null, null,
                EntryType.LOGIN, "", false);

        service.deleteEntry(entry.id());
        Entry restored = service.restoreEntry(entry.id());
        service.lock();
        service.unlock(PASSWORD);

        assertThat(restored.deleted()).isFalse();
        assertThat(service.entries()).extracting(Entry::label).containsExactly("GitHub");
        assertThat(service.trashCount()).isZero();
    }

    @Test
    void purgeEntryRemovesTrashedEntryForever() throws Exception {
        newVault();
        Entry entry = service.addEntry("GitHub", "alice", "fake-password", null, null,
                EntryType.LOGIN, "", false);

        service.deleteEntry(entry.id());
        service.purgeEntry(entry.id());
        service.lock();
        service.unlock(PASSWORD);

        assertThat(service.allEntries()).isEmpty();
        assertThat(service.trashCount()).isZero();
    }

    @Test
    void entriesByTypeFiltersByType() throws Exception {
        newVault();
        service.addEntry("GitHub", "alice", "fake-password", null, null,
                EntryType.LOGIN, "", false);
        service.addEntry("Notes", "n/a", "fake-password", null, null,
                EntryType.SECURE_NOTE, "", false);

        assertThat(service.entriesByType(EntryType.LOGIN)).extracting(Entry::label)
                .containsExactly("GitHub");
        assertThat(service.entriesByType(EntryType.SECURE_NOTE)).extracting(Entry::label)
                .containsExactly("Notes");
    }

    @Test
    void favoriteEntriesAndCount() throws Exception {
        newVault();
        service.addEntry("GitHub", "alice", "fake-password", null, null,
                EntryType.LOGIN, "", true);
        service.addEntry("Gmail", "bob", "fake-password", null, null,
                EntryType.LOGIN, "", false);

        assertThat(service.favoriteCount()).isEqualTo(1);
        assertThat(service.favoriteEntries()).extracting(Entry::label).containsExactly("GitHub");
    }

    @Test
    void foldersAreDistinctAndSorted() throws Exception {
        newVault();
        service.addEntry("Work", "alice", "fake-password", null, null,
                EntryType.LOGIN, "Work", false);
        service.addEntry("Personal", "bob", "fake-password", null, null,
                EntryType.LOGIN, "Personal", false);
        service.addEntry("Work 2", "carol", "fake-password", null, null,
                EntryType.LOGIN, "Work", false);

        assertThat(service.folders()).containsExactly("Personal", "Work");
    }

    @Test
    void searchMatchesLabelUsernameAndUrlCaseInsensitively() throws Exception {
        newVault();
        service.addEntry("GitHub", "alice-dev", "fake-password", "https://github.com", null,
                EntryType.LOGIN, "", false);
        service.addEntry("Gmail", "bob", "fake-password", "https://mail.example.test", null,
                EntryType.LOGIN, "", false);
        service.addEntry("Bank", "carol", "fake-password", "https://bank.example.test", null,
                EntryType.LOGIN, "", false);

        assertThat(service.search("GIT")).extracting(Entry::label).containsExactly("GitHub");
        assertThat(service.search("ALICE")).extracting(Entry::label).containsExactly("GitHub");
        assertThat(service.search("mail.example")).extracting(Entry::label).containsExactly("Gmail");
        assertThat(service.search("example.test"))
                .extracting(Entry::label)
                .containsExactly("Gmail", "Bank");
    }

    @Test
    void blankSearchReturnsEverythingAndNoMatchReturnsEmpty() throws Exception {
        newVault();
        service.addEntry("GitHub", "alice", "fake-password", null, null,
                EntryType.LOGIN, "", false);

        assertThat(service.search(null)).hasSize(1);
        assertThat(service.search("   ")).hasSize(1);
        assertThat(service.search("nothing-matches")).isEmpty();
    }

    @Test
    void serviceNeverModifiesTheCallersPasswordArray() throws Exception {
        char[] callerOwned = PASSWORD.clone();

        service.createVault(callerOwned);
        service.lock();
        service.unlock(callerOwned);

        assertThat(callerOwned).containsExactly(PASSWORD);
    }

    @Test
    void emptyMasterPasswordIsRejected() {
        assertThatThrownBy(() -> service.createVault(new char[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /* ==================================================================== */
    /*  Master password rotation                                            */
    /* ==================================================================== */

    private Path vaultPath() {
        return tempDir.resolve("vault.dat");
    }

    @Test
    void changeMasterPasswordReencryptsWithTheNewPassword() throws Exception {
        newVault();
        Entry entry = service.addEntry("GitHub", "alice", "fake-password", null, null,
                EntryType.LOGIN, "", false);

        service.changeMasterPassword(PASSWORD, NEW_PASSWORD);
        service.lock();

        // The old password must stop working after the rotation.
        assertThatThrownBy(() -> service.unlock(PASSWORD))
                .isInstanceOf(VaultCryptoException.class);
        // The new password opens the vault with the entries intact.
        service.unlock(NEW_PASSWORD);
        assertThat(service.entries()).containsExactly(entry);
    }

    @Test
    void changeMasterPasswordRejectsAWrongCurrentPassword() throws Exception {
        newVault();
        byte[] before = Files.readAllBytes(vaultPath());

        assertThatThrownBy(() -> service.changeMasterPassword(
                "wrong staple row".toCharArray(), NEW_PASSWORD))
                .isInstanceOf(VaultCryptoException.class);

        // Nothing changed on disk and the session is still usable.
        assertThat(Files.readAllBytes(vaultPath())).isEqualTo(before);
        assertThat(service.isUnlocked()).isTrue();
        service.lock();
        service.unlock(PASSWORD);
        assertThat(service.isUnlocked()).isTrue();
    }

    @Test
    void changeMasterPasswordWritesAFreshSaltAndIv() throws Exception {
        newVault();
        byte[] before = Files.readAllBytes(vaultPath());

        service.changeMasterPassword(PASSWORD, NEW_PASSWORD);
        byte[] after = Files.readAllBytes(vaultPath());

        // Header: 4 magic + 1 version + 16 salt + 12 IV.
        // Every save must use a new salt and a new IV; 256-bit key assumed.
        assertThat(copyRange(after, 0, 4)).isEqualTo(new byte[]{'J', 'V', 'L', 'T'});
        assertThat(copyRange(after, 5, 21)).isNotEqualTo(copyRange(before, 5, 21));
        assertThat(copyRange(after, 21, 33)).isNotEqualTo(copyRange(before, 21, 33));
    }

    @Test
    void changeMasterPasswordRequiresAnUnlockedVault() throws Exception {
        newVault();
        service.lock();

        assertThatThrownBy(() -> service.changeMasterPassword(PASSWORD, NEW_PASSWORD))
                .isInstanceOf(VaultLockedException.class);
    }

    @Test
    void changeMasterPasswordRejectsEmptyPasswords() throws Exception {
        newVault();

        assertThatThrownBy(() -> service.changeMasterPassword(new char[0], NEW_PASSWORD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.changeMasterPassword(PASSWORD, new char[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeMasterPasswordKeepsTheVaultUsableAfterTheChange() throws Exception {
        newVault();
        service.changeMasterPassword(PASSWORD, NEW_PASSWORD);

        // Adding an entry right after the change proves the session clone
        // was swapped: saves must now encrypt with the new key.
        service.addEntry("Gmail", "bob", "fake-password", null, null,
                EntryType.LOGIN, "", false);
        service.lock();
        service.unlock(NEW_PASSWORD);

        assertThat(service.entries()).extracting(Entry::label).containsExactly("Gmail");
    }

    @Test
    void changeMasterPasswordRefusesToRewriteACorruptedVault() throws Exception {
        newVault();
        // Corrupt the file on disk while the session stays unlocked, as a
        // second process or a disk fault would.
        byte[] bytes = Files.readAllBytes(vaultPath());
        bytes[bytes.length - 16] ^= 0x01; // first byte of the GCM tag
        Files.write(vaultPath(), bytes);
        byte[] corrupted = Files.readAllBytes(vaultPath());

        assertThatThrownBy(() -> service.changeMasterPassword(PASSWORD, NEW_PASSWORD))
                .isInstanceOf(VaultCryptoException.class);

        // The corrupt bytes were not silently replaced by a fresh vault.
        assertThat(Files.readAllBytes(vaultPath())).isEqualTo(corrupted);
        assertThat(service.isUnlocked()).isTrue();
    }

    @Test
    void changeMasterPasswordNeverModifiesTheCallersArrays() throws Exception {
        newVault();
        char[] current = PASSWORD.clone();
        char[] next = NEW_PASSWORD.clone();

        service.changeMasterPassword(current, next);

        assertThat(current).containsExactly(PASSWORD);
        assertThat(next).containsExactly(NEW_PASSWORD);
    }

    private static byte[] copyRange(byte[] data, int from, int to) {
        return Arrays.copyOfRange(data, from, to);
    }

}
