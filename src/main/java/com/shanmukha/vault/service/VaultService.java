package com.shanmukha.vault.service;

import com.shanmukha.vault.crypto.TotpGenerator;
import com.shanmukha.vault.crypto.VaultCryptoException;
import com.shanmukha.vault.model.Entry;
import com.shanmukha.vault.model.EntryType;
import com.shanmukha.vault.model.VaultData;
import com.shanmukha.vault.storage.VaultRepository;
import com.shanmukha.vault.storage.VaultStorageException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

/**
 * Business logic for the vault: authentication state plus CRUD.
 *
 * <p>The UI and crypto/storage layers never talk to each other directly.
 * While unlocked, the service keeps a <em>clone</em> of the master password so
 * it can re-encrypt on every save. {@link #lock()} wipes that clone. Secrets
 * still exist in JVM memory until garbage collection, which is documented in
 * LIMITATIONS.md.</p>
 *
 * <p>Deletes are soft: an entry is flagged {@code deleted} and moved to the
 * Trash view instead of being erased, so the sidebar can show a Trash count.</p>
 *
 * <p>All methods are synchronized: auto-lock (Phase 7) may call
 * {@link #lock()} from a timer thread while the UI is reading entries.</p>
 */
public final class VaultService {

    private final VaultRepository repository;

    /** Non-null only while unlocked. */
    private VaultData data;

    /** Cloned master password; non-null only while unlocked. */
    private char[] sessionPassword;

    public VaultService(VaultRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("Repository must not be null.");
        }
        this.repository = repository;
    }

    /** Uses the default {@code vault.dat} location. */
    public VaultService() {
        this(new VaultRepository());
    }

    public synchronized boolean vaultExists() {
        return repository.exists();
    }

    public synchronized boolean isUnlocked() {
        return data != null && sessionPassword != null;
    }

    /**
     * Creates an empty vault and leaves the service unlocked.
     *
     * @throws IllegalStateException if a vault file already exists
     */
    public synchronized void createVault(char[] masterPassword)
            throws VaultStorageException, VaultCryptoException {
        requirePassword(masterPassword);
        if (repository.exists()) {
            throw new IllegalStateException("A vault already exists at this location.");
        }
        VaultData empty = VaultData.empty();
        repository.save(empty, masterPassword);
        replaceSession(empty, masterPassword);
    }

    /**
     * Decrypts the vault with the master password.
     * On failure the service stays locked.
     *
     * @throws VaultCryptoException if the password is wrong or the file was tampered with
     */
    public synchronized void unlock(char[] masterPassword)
            throws VaultStorageException, VaultCryptoException {
        requirePassword(masterPassword);
        VaultData loaded = repository.load(masterPassword);
        replaceSession(loaded, masterPassword);
    }

    /** Locks the vault and wipes the in-memory password clone. */
    public synchronized void lock() {
        wipe(sessionPassword);
        sessionPassword = null;
        data = null;
    }

    /**
     * Re-encrypts the whole vault with a new master password
     * (bonus feature). There is no stored password hash to update: instead the
     * current password is <em>proven</em> by decrypting the file with it, and
     * then the whole payload is written again under a new key. Because every
     * save derives a fresh salt and IV and replaces the file atomically, no
     * separate "rekey" code path in the repository is needed.
     *
     * <p>Steps are ordered so a failure never damages the vault on disk:</p>
     * <ol>
     *   <li>decrypt with {@code currentPassword} — proves knowledge and also
     *       refuses to overwrite a corrupted or tampered file,</li>
     *   <li>{@link VaultRepository#save} with {@code newPassword} — writes
     *       fresh salt + IV through {@code vault.dat.tmp} + atomic move,</li>
     *   <li>only after a successful save, swap the in-memory session clone;
     *       the service stays unlocked, now keyed by the new password.</li>
     * </ol>
     *
     * <p>As everywhere else, the caller's arrays are never stored or
     * modified; the session keeps a clone.</p>
     *
     * @throws VaultCryptoException if the current password is wrong or the
     *                              file was tampered with
     */
    public synchronized void changeMasterPassword(char[] currentPassword, char[] newPassword)
            throws VaultStorageException, VaultCryptoException {
        requireUnlocked();
        requirePassword(currentPassword);
        requirePassword(newPassword);

        // Step 1: prove the caller knows the current password. The parsed
        // copy is thrown away on purpose — step 2 writes what the UI showed.
        repository.load(currentPassword);

        // Step 2: full re-encryption. If this fails, everything below is
        // skipped and the old password still opens the unchanged vault.
        repository.save(requireUnlocked(), newPassword);

        // Step 3: swap the session clone (wipes the old one inside).
        replaceSession(data, newPassword);
    }

    /** All non-deleted entries, snapshot. Callers cannot mutate the vault through this list. */
    public synchronized List<Entry> entries() {
        return activeEntries(requireUnlocked());
    }

    /** All entries including soft-deleted ones (for the Trash view). */
    public synchronized List<Entry> allEntries() {
        return List.copyOf(requireUnlocked().entries());
    }

    /** Non-deleted entries of a given type (sidebar section counts). */
    public synchronized List<Entry> entriesByType(EntryType type) {
        VaultData current = requireUnlocked();
        return current.entries().stream()
                .filter(e -> !e.deleted() && e.type() == type)
                .toList();
    }

    /** Non-deleted favorite entries. */
    public synchronized List<Entry> favoriteEntries() {
        VaultData current = requireUnlocked();
        return current.entries().stream()
                .filter(e -> !e.deleted() && e.favorite())
                .toList();
    }

    /** Non-deleted entries in a folder (empty string means "no folder"). */
    public synchronized List<Entry> entriesInFolder(String folder) {
        VaultData current = requireUnlocked();
        String f = folder == null ? "" : folder;
        return current.entries().stream()
                .filter(e -> !e.deleted() && f.equals(e.folder()))
                .toList();
    }

    /** Distinct folder names used by non-deleted entries (sidebar FOLDERS list). */
    public synchronized List<String> folders() {
        VaultData current = requireUnlocked();
        return current.entries().stream()
                .filter(e -> !e.deleted() && !e.folder().isBlank())
                .map(Entry::folder)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /** Case-insensitive search over label, username and URL; blank returns active entries. */
    public synchronized List<Entry> search(String query) {
        VaultData current = requireUnlocked();
        List<Entry> active = activeEntries(current);
        if (query == null || query.isBlank()) {
            return List.copyOf(active);
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return active.stream()
                .filter(entry -> containsIgnoreCase(entry.label(), needle)
                        || containsIgnoreCase(entry.username(), needle)
                        || containsIgnoreCase(entry.url(), needle)
                        || containsIgnoreCase(entry.note(), needle))
                .toList();
    }

    /**
     * Adds a new entry and persists the vault immediately.
     * The label is trimmed; duplicate labels are rejected case-insensitively.
     */
    public synchronized Entry addEntry(String label, String username, String password,
                                       String url, String note, EntryType type,
                                       String folder, boolean favorite)
            throws DuplicateLabelException, VaultStorageException, VaultCryptoException {
        VaultData current = requireUnlocked();
        String normalizedLabel = normalizeLabel(label);
        ensureLabelAvailable(current, normalizedLabel, null);

        Entry entry = Entry.create(normalizedLabel, username, password, url, note, type);
        entry = new Entry(entry.id(), entry.label(), entry.username(), entry.password(),
                entry.url(), entry.note(), entry.createdAt(), entry.updatedAt(),
                entry.type(), favorite, folder, false, entry.totpSecret());
        List<Entry> entries = new ArrayList<>(current.entries());
        entries.add(entry);
        persist(current, entries);
        return entry;
    }

    /**
     * Updates an entry in place (id and createdAt are preserved, updatedAt is
     * refreshed) and persists the vault.
     *
     * @throws NoSuchElementException if no entry has the given id
     */
    public synchronized Entry updateEntry(String id, String label, String username, String password,
                                          String url, String note, EntryType type,
                                          String folder, boolean favorite, String totpSecret)
            throws DuplicateLabelException, VaultStorageException, VaultCryptoException {
        VaultData current = requireUnlocked();
        int index = indexOf(current, id);
        Entry existing = current.entries().get(index);

        String normalizedLabel = normalizeLabel(label);
        ensureLabelAvailable(current, normalizedLabel, existing.id());

        Entry updated = new Entry(existing.id(), normalizedLabel, username, password, url, note,
                existing.createdAt(), System.currentTimeMillis(),
                type, favorite, folder, existing.deleted(), totpSecret);
        List<Entry> entries = new ArrayList<>(current.entries());
        entries.set(index, updated);
        persist(current, entries);
        return updated;
    }

    /**
     * Soft-deletes an entry (moves it to Trash) and persists the vault.
     *
     * @throws NoSuchElementException if no entry has the given id
     */
    public synchronized Entry deleteEntry(String id)
            throws VaultStorageException, VaultCryptoException {
        VaultData current = requireUnlocked();
        int index = indexOf(current, id);
        Entry existing = current.entries().get(index);
        List<Entry> entries = new ArrayList<>(current.entries());
        entries.set(index, new Entry(existing.id(), existing.label(), existing.username(),
                existing.password(), existing.url(), existing.note(),
                existing.createdAt(), existing.updatedAt(),
                existing.type(), existing.favorite(), existing.folder(), true,
                existing.totpSecret()));
        persist(current, entries);
        return existing;
    }

    /** Restores a soft-deleted entry and persists the vault. */
    public synchronized Entry restoreEntry(String id)
            throws VaultStorageException, VaultCryptoException {
        VaultData current = requireUnlocked();
        int index = indexOf(current, id);
        Entry existing = current.entries().get(index);
        if (!existing.deleted()) {
            throw new IllegalStateException("Entry is not in the Trash.");
        }
        Entry restored = new Entry(existing.id(), existing.label(), existing.username(),
                existing.password(), existing.url(), existing.note(),
                existing.createdAt(), existing.updatedAt(),
                existing.type(), existing.favorite(), existing.folder(), false,
                existing.totpSecret());
        List<Entry> entries = new ArrayList<>(current.entries());
        entries.set(index, restored);
        persist(current, entries);
        return restored;
    }

    /** Permanently erases a soft-deleted entry. Trash only. */
    public synchronized Entry purgeEntry(String id)
            throws VaultStorageException, VaultCryptoException {
        VaultData current = requireUnlocked();
        int index = indexOf(current, id);
        Entry existing = current.entries().get(index);
        if (!existing.deleted()) {
            throw new IllegalStateException("Entry is not in the Trash.");
        }
        List<Entry> entries = new ArrayList<>(current.entries());
        Entry removed = entries.remove(index);
        persist(current, entries);
        return removed;
    }

    /** Counts non-deleted entries of each type for sidebar badges. */
    public synchronized int countByType(EntryType type) {
        return entriesByType(type).size();
    }

    /** Count of non-deleted favorites. */
    public synchronized int favoriteCount() {
        return favoriteEntries().size();
    }

    /** Count of entries in the Trash. */
    public synchronized int trashCount() {
        VaultData current = requireUnlocked();
        return (int) current.entries().stream()
                .filter(Entry::deleted)
                .count();
    }

    /** All soft-deleted entries (Trash view). */
    public synchronized List<Entry> trashEntries() {
        VaultData current = requireUnlocked();
        return current.entries().stream()
                .filter(Entry::deleted)
                .toList();
    }

    /**
     * Current six-digit TOTP code for an entry's secret (empty string when no
     * secret is set). Cipher work stays in the crypto package; the UI calls
     * this method only.
     */
    public synchronized String totpCode(String secret) {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        return TotpGenerator.generateBase32(secret, System.currentTimeMillis());
    }

    private static List<Entry> activeEntries(VaultData current) {
        return current.entries().stream()
                .filter(e -> !e.deleted())
                .toList();
    }

    private void replaceSession(VaultData loaded, char[] masterPassword) {
        wipe(sessionPassword);
        sessionPassword = masterPassword.clone();
        data = loaded;
    }

    private void persist(VaultData current, List<Entry> entries)
            throws VaultStorageException, VaultCryptoException {
        VaultData updated = new VaultData(current.schemaVersion(), entries);
        // Save first: if the disk write fails the in-memory state stays unchanged.
        repository.save(updated, sessionPassword);
        data = updated;
    }

    private VaultData requireUnlocked() {
        if (data == null || sessionPassword == null) {
            throw new VaultLockedException("The vault is locked.");
        }
        return data;
    }

    private static int indexOf(VaultData current, String id) {
        if (id == null) {
            throw new IllegalArgumentException("Entry id must not be null.");
        }
        List<Entry> entries = current.entries();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new NoSuchElementException("No entry with that id exists.");
    }

    private static void ensureLabelAvailable(VaultData current, String label, String ignoredId)
            throws DuplicateLabelException {
        boolean duplicate = activeEntries(current).stream()
                .filter(entry -> !entry.id().equals(ignoredId))
                .anyMatch(entry -> entry.label().equalsIgnoreCase(label));
        if (duplicate) {
            throw new DuplicateLabelException("An entry with this label already exists.");
        }
    }

    private static String normalizeLabel(String label) {
        return label == null ? "" : label.trim();
    }

    private static boolean containsIgnoreCase(String value, String lowerCaseNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerCaseNeedle);
    }

    private static void requirePassword(char[] masterPassword) {
        if (masterPassword == null || masterPassword.length == 0) {
            throw new IllegalArgumentException("Master password must not be empty.");
        }
    }

    private static void wipe(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
