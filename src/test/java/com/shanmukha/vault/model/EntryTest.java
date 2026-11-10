package com.shanmukha.vault.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntryTest {

    @Test
    void createGeneratesIdAndTimestamps() {
        Entry entry = Entry.create("GitHub", "alice", "fake-password", null, null);

        assertThat(entry.id()).isNotBlank();
        assertThat(entry.label()).isEqualTo("GitHub");
        assertThat(entry.username()).isEqualTo("alice");
        assertThat(entry.password()).isEqualTo("fake-password");
        assertThat(entry.createdAt()).isPositive();
        assertThat(entry.updatedAt()).isEqualTo(entry.createdAt());
        assertThat(entry.type()).isEqualTo(EntryType.LOGIN);
        assertThat(entry.favorite()).isFalse();
        assertThat(entry.deleted()).isFalse();
    }

    @Test
    void createWithTypeSetsType() {
        Entry entry = Entry.create("Note", "n/a", "fake-password", null, null, EntryType.SECURE_NOTE);

        assertThat(entry.type()).isEqualTo(EntryType.SECURE_NOTE);
    }

    @Test
    void createNormalizesMissingUrlAndNoteToEmptyStrings() {
        Entry entry = Entry.create("Mail", "bob", "fake-password", null, null);

        assertThat(entry.url()).isEmpty();
        assertThat(entry.note()).isEmpty();
        assertThat(entry.folder()).isEmpty();
        assertThat(entry.totpSecret()).isEmpty();
    }

    @Test
    void createUsesUniqueIds() {
        Entry first = Entry.create("A", "u", "p", null, null);
        Entry second = Entry.create("B", "u", "p", null, null);

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void vaultDataStartsEmptyWithCurrentSchema() {
        VaultData data = VaultData.empty();

        assertThat(data.schemaVersion()).isEqualTo(VaultData.CURRENT_SCHEMA_VERSION);
        assertThat(data.entries()).isEmpty();
    }

    @Test
    void vaultDataDefensivelyCopiesEntries() {
        List<Entry> mutable = new ArrayList<>();
        VaultData data = new VaultData(1, mutable);

        mutable.add(Entry.create("A", "u", "p", null, null));

        assertThat(data.entries()).isEmpty();
    }
}
