package com.shanmukha.vault.model;

import java.util.UUID;

/**
 * One stored credential, extended to match the UI mockups:
 * entry type, favorite flag, folder, soft-delete and an optional TOTP secret.
 *
 * <p>All new fields have sensible defaults in the compact constructor so the
 * JSON stays forward-compatible with older vault files.</p>
 *
 * @param password the entry secret. Never log it and never put it in a message.
 */
public record Entry(
        String id,
        String label,
        String username,
        String password,
        String url,
        String note,
        long createdAt,
        long updatedAt,
        EntryType type,
        boolean favorite,
        String folder,
        boolean deleted,
        String totpSecret) {

    public Entry {
        url = url == null ? "" : url;
        note = note == null ? "" : note;
        type = type == null ? EntryType.LOGIN : type;
        folder = folder == null ? "" : folder;
        totpSecret = totpSecret == null ? "" : totpSecret;
    }

    /** Creates a new entry with a random id and matching create/update times. */
    public static Entry create(String label, String username, String password,
                               String url, String note) {
        long now = System.currentTimeMillis();
        return new Entry(UUID.randomUUID().toString(), label, username, password, url, note,
                now, now, EntryType.LOGIN, false, "", false, "");
    }

    /** Creates a new entry with an explicit type. */
    public static Entry create(String label, String username, String password,
                               String url, String note, EntryType type) {
        long now = System.currentTimeMillis();
        return new Entry(UUID.randomUUID().toString(), label, username, password, url, note,
                now, now, type, false, "", false, "");
    }
}
