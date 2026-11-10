package com.shanmukha.vault.model;

import java.util.List;

/**
 * Everything that gets encrypted into the vault file.
 *
 * <p>{@code schemaVersion} lets future versions migrate old data, and the
 * entries list is defensively copied so a caller cannot mutate the vault
 * contents behind the service layer's back.</p>
 *
 * @param schemaVersion version of the JSON shape (currently 2)
 * @param entries       all stored credentials
 */
public record VaultData(int schemaVersion, List<Entry> entries) {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    public VaultData {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /** A brand-new vault: current schema, no entries. */
    public static VaultData empty() {
        return new VaultData(CURRENT_SCHEMA_VERSION, List.of());
    }
}
