package com.shanmukha.vault.ui;

import com.shanmukha.vault.crypto.StrengthChecker;
import com.shanmukha.vault.crypto.VaultCryptoException;
import com.shanmukha.vault.model.Entry;
import com.shanmukha.vault.service.VaultService;
import com.shanmukha.vault.storage.VaultStorageException;
import com.shanmukha.vault.ui.components.Avatar;
import com.shanmukha.vault.ui.components.StrengthBar;
import com.shanmukha.vault.ui.components.Toast;
import com.shanmukha.vault.ui.components.TotpRing;
import com.shanmukha.vault.ui.theme.Styles;
import com.shanmukha.vault.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Right-hand 420 px details panel (UI_SPEC 2.3): avatar + name header, then
 * hairline-separated groups — Username, Password (four-segment strength bar),
 * Website, Verification code (TOTP ring + copy), Notes — and Edit/Delete
 * buttons left-aligned with the "Updated" timestamp underneath.
 *
 * <p>Values are borderless read-only fields so users can select and re-copy
 * them. Writes go through {@link VaultService} only. In the Trash view the
 * panel offers Restore / Delete forever.</p>
 */
public final class DetailsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault());

    private final VaultService service;
    private final Runnable onSaved;

    /** Avatar + name + subtitle + favorite star (inner class below). */
    private final HeaderPanel header = new HeaderPanel();

    private final JTextField usernameField = plainValueField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JTextField websiteField = plainValueField();
    private final JTextArea notesArea = plainNotesArea();
    private final JLabel totpLabel = new JLabel("\u2014");
    private final JLabel strengthLabel = new JLabel("");
    private final StrengthBar strengthBar = new StrengthBar();
    private final TotpRing totpRing = new TotpRing();

    private final JButton editButton = Styles.primaryButton("Edit");
    private final JButton deleteButton = Styles.dangerButton("Delete");
    private final JButton restoreButton = Styles.primaryButton("Restore");
    private final JButton purgeButton = Styles.dangerButton("Delete forever");
    private final JLabel updatedLabel = new JLabel("");

    private Entry entry;
    private final javax.swing.Timer totpTicker;

    DetailsPanel(VaultService service, Runnable onSaved) {
        this.service = service;
        this.onSaved = onSaved;

        setBackground(Theme.surface());
        setPreferredSize(new Dimension(Theme.DETAILS_W, 0));
        setMinimumSize(new Dimension(Theme.DETAILS_W, 0));
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createCompoundBorder(
                new LeftHairline(),
                BorderFactory.createEmptyBorder(Theme.SPACE_24, Theme.SPACE_24,
                        Theme.SPACE_24, Theme.SPACE_24)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        int row = 0;

        // Header.
        row = place(gbc, row, header, 0, 0);

        // Username.
        row = place(gbc, row, Styles.hline(), Theme.SPACE_16, 0);
        row = place(gbc, row, group("Username", trailingRow(usernameField,
                copyIcon("Copy username", usernameField::getText))), 0, 0);

        // Password.
        row = place(gbc, row, Styles.hline(), Theme.SPACE_16, 0);
        row = place(gbc, row, group("Password", passwordRow()), 0, 0);

        // Strength bar + label directly under the password.
        JPanel strengthRow = new JPanel(new BorderLayout(Theme.SPACE_8, 0));
        strengthRow.setOpaque(false);
        strengthLabel.setFont(strengthLabel.getFont().deriveFont(Font.BOLD, Theme.FONT_LABEL));
        strengthRow.add(strengthBar, BorderLayout.WEST);
        strengthRow.add(strengthLabel, BorderLayout.EAST);
        row = place(gbc, row, strengthRow, Theme.SPACE_8, 0);

        // Website.
        row = place(gbc, row, Styles.hline(), Theme.SPACE_16, 0);
        row = place(gbc, row, group("Website", trailingRow(websiteField,
                copyIcon("Copy website", websiteField::getText))), 0, 0);

        // Verification code (TOTP).
        row = place(gbc, row, Styles.hline(), Theme.SPACE_16, 0);
        row = place(gbc, row, totpGroup(), 0, 0);

        // Notes.
        row = place(gbc, row, Styles.fieldLabel("Notes"), Theme.SPACE_16, Theme.SPACE_8);
        row = place(gbc, row, notesArea, 0, 0);

        // Buttons (Edit/Delete, or Restore/Delete forever in the Trash).
        row = place(gbc, row, Styles.hline(), Theme.SPACE_16, 0);
        row = place(gbc, row, actionArea(), Theme.SPACE_16, 0);

        // Elastic filler pushes everything up.
        gbc.gridy = row;
        gbc.weighty = 1;
        add(new JPanel(), gbc);

        totpTicker = new javax.swing.Timer(1000, e -> tickTotp());
        wireButtons();
        setEntry(null);
    }

    /** Adds a component at the given row with top/bottom insets; returns row+1. */
    private int place(GridBagConstraints gbc, int row, java.awt.Component component,
                      int top, int bottom) {
        gbc.gridy = row;
        gbc.insets = new Insets(top, 0, bottom, 0);
        add(component, gbc);
        return row + 1;
    }

    /* ==================================================================== */
    /*  Group builders                                                      */
    /* ==================================================================== */

    /** Muted 12 pt bold label above a value row. */
    private JPanel group(String labelText, JPanel valueRow) {
        JPanel box = new JPanel(new GridBagLayout());
        box.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        box.add(Styles.fieldLabel(labelText), g);
        g.gridy = 1;
        g.insets = new Insets(Theme.SPACE_8, 0, 0, 0);
        box.add(valueRow, g);
        return box;
    }

    /** Value field with one icon button on the right. */
    private JPanel trailingRow(JTextField primary, JButton trailing) {
        JPanel row = new JPanel(new BorderLayout(Theme.SPACE_8, 0));
        row.setOpaque(false);
        row.add(primary, BorderLayout.CENTER);
        JPanel holder = new JPanel(new GridLayout(1, 1, Theme.SPACE_4, 0));
        holder.setOpaque(false);
        holder.add(trailing);
        row.add(holder, BorderLayout.EAST);
        return row;
    }

    private JPanel passwordRow() {
        JPanel row = new JPanel(new BorderLayout(Theme.SPACE_8, 0));
        row.setOpaque(false);
        passwordField.setEchoChar('\u25CF');
        passwordField.setEditable(false);
        passwordField.setBorder(BorderFactory.createEmptyBorder());
        passwordField.setOpaque(false);
        passwordField.setFont(passwordField.getFont().deriveFont(Font.PLAIN, Theme.FONT_BODY));

        JPanel icons = new JPanel(new GridLayout(1, 2, Theme.SPACE_4, 0));
        icons.setOpaque(false);
        JButton reveal = Styles.ghostButton("\u25CE", "Show/hide password");
        reveal.addActionListener(e -> toggleReveal());
        icons.add(reveal);
        icons.add(copyIcon("Copy password", () ->
                entry != null ? String.valueOf(entry.password()) : ""));

        row.add(passwordField, BorderLayout.CENTER);
        row.add(icons, BorderLayout.EAST);
        return row;
    }

    private JPanel totpGroup() {
        JPanel box = new JPanel(new GridBagLayout());
        box.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        box.add(Styles.fieldLabel("Verification code"), g);

        JPanel value = new JPanel(new BorderLayout(Theme.SPACE_8, 0));
        value.setOpaque(false);
        totpLabel.setFont(totpLabel.getFont().deriveFont(Font.BOLD, Theme.FONT_TOTP));

        JPanel ringHolder = new JPanel(new GridLayout(1, 2, Theme.SPACE_4, 0));
        ringHolder.setOpaque(false);
        ringHolder.add(totpRing);
        ringHolder.add(copyIcon("Copy code", this::currentTotpCode));

        value.add(totpLabel, BorderLayout.WEST);
        value.add(ringHolder, BorderLayout.EAST);

        g.gridy = 1;
        g.insets = new Insets(Theme.SPACE_8, 0, 0, 0);
        box.add(value, g);
        return box;
    }

    private JPanel actionArea() {
        JPanel box = new JPanel(new GridBagLayout());
        box.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;

        JPanel activeButtons = new JPanel(new GridLayout(1, 2, Theme.SPACE_16, 0));
        activeButtons.setOpaque(false);
        activeButtons.add(editButton);
        activeButtons.add(deleteButton);

        JPanel trashButtons = new JPanel(new GridLayout(1, 2, Theme.SPACE_16, 0));
        trashButtons.setOpaque(false);
        trashButtons.add(restoreButton);
        trashButtons.add(purgeButton);

        g.gridy = 0;
        box.add(activeButtons, g);
        g.gridy = 1;
        box.add(trashButtons, g);

        updatedLabel.setFont(updatedLabel.getFont().deriveFont(Font.PLAIN, Theme.FONT_HINT));
        g.gridy = 2;
        g.insets = new Insets(Theme.SPACE_16, 0, 0, 0);
        box.add(updatedLabel, g);
        return box;
    }

    private static JTextField plainValueField() {
        JTextField field = new JTextField();
        field.setEditable(false);
        field.setBorder(BorderFactory.createEmptyBorder());
        field.setFont(field.getFont().deriveFont(Font.PLAIN, Theme.FONT_BODY));
        return field;
    }

    private static JTextArea plainNotesArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder());
        area.setFont(area.getFont().deriveFont(Font.PLAIN, Theme.FONT_BODY));
        area.setOpaque(false);
        return area;
    }

    /* ==================================================================== */
    /*  Header inner class                                                  */
    /* ==================================================================== */

    /** Avatar + name + "Login · Work" subtitle + favorite star. */
    private static final class HeaderPanel extends JPanel {

        private final Avatar avatar = new Avatar("?", 44);
        private final JTextField nameField = new JTextField();
        private final JLabel subtitle = new JLabel("");
        private final JLabel star = new JLabel("\u2605");

        HeaderPanel() {
            setOpaque(false);
            setLayout(new BorderLayout(Theme.SPACE_16, 0));
            add(avatar, BorderLayout.WEST);

            nameField.setEditable(false);
            nameField.setBorder(BorderFactory.createEmptyBorder());
            nameField.setOpaque(false);
            nameField.setFont(nameField.getFont().deriveFont(Font.BOLD, Theme.FONT_TITLE));

            JPanel names = new JPanel(new GridLayout(2, 1));
            names.setOpaque(false);
            subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, Theme.FONT_LABEL));
            names.add(nameField);
            names.add(subtitle);
            add(names, BorderLayout.CENTER);

            star.setFont(star.getFont().deriveFont(18f));
            star.setForeground(Theme.star());
            add(star, BorderLayout.EAST);
        }

        void show(Entry entry) {
            avatar.setFrom(entry.label());
            nameField.setText(entry.label());
            subtitle.setText(entrySubtitle(entry));
            star.setVisible(entry.favorite());
        }

        void refresh() {
            nameField.setForeground(Theme.text());
            subtitle.setForeground(Theme.muted());
            repaint();
        }
    }

    private static String entrySubtitle(Entry entry) {
        return switch (entry.type()) {
            case LOGIN -> entry.folder().isBlank() ? "Login"
                    : "Login \u00B7 " + entry.folder();
            case SECURE_NOTE -> entry.folder().isBlank() ? "Secure note"
                    : "Secure note \u00B7 " + entry.folder();
            case CARD -> entry.folder().isBlank() ? "Card" : "Card \u00B7 " + entry.folder();
        };
    }

    /* ==================================================================== */
    /*  Entry display                                                       */
    /* ==================================================================== */

    public void setEntry(Entry newEntry) {
        this.entry = newEntry;
        boolean hasEntry = newEntry != null;
        boolean deleted = hasEntry && newEntry.deleted();

        header.setVisible(hasEntry);

        if (hasEntry) {
            header.show(newEntry);
            usernameField.setText(newEntry.username());

            passwordField.setText(newEntry.password());
            passwordField.setEchoChar('\u25CF');

            websiteField.setText(newEntry.url());
            notesArea.setText(newEntry.note());

            StrengthChecker.Assessment assessment =
                    StrengthChecker.assess(newEntry.password().toCharArray());
            strengthBar.setStrength(assessment.strength(), assessment.reason());
            strengthLabel.setText(strengthText(assessment.strength()));
            strengthLabel.setForeground(areaColor(assessment.strength()));

            updatedLabel.setText("Updated " + DATE.format(
                    Instant.ofEpochMilli(newEntry.updatedAt())
                            .atZone(ZoneId.systemDefault())));
        } else {
            usernameField.setText("");
            passwordField.setText("");
            websiteField.setText("");
            notesArea.setText("");
            strengthLabel.setText("");
            updatedLabel.setText("");
            totpLabel.setText("\u2014");
        }

        boolean hasTotp = hasEntry && !newEntry.totpSecret().isBlank();
        totpLabel.setVisible(hasTotp);
        totpRing.setVisible(hasTotp);
        if (hasTotp) {
            tickTotp();
            totpTicker.start();
        } else {
            totpTicker.stop();
        }

        editButton.setVisible(hasEntry && !deleted);
        deleteButton.setVisible(hasEntry && !deleted);
        restoreButton.setVisible(hasEntry && deleted);
        purgeButton.setVisible(hasEntry && deleted);
        updatedLabel.setVisible(hasEntry);

        revalidate();
        repaint();
    }

    /* ==================================================================== */
    /*  Helpers                                                             */
    /* ==================================================================== */

    private static String strengthText(StrengthChecker.Strength strength) {
        return switch (strength) {
            case WEAK -> "Weak";
            case MEDIUM -> "Okay";
            case STRONG -> "Strong";
        };
    }

    private static Color areaColor(StrengthChecker.Strength strength) {
        return switch (strength) {
            case WEAK -> Theme.danger();
            case MEDIUM -> Theme.warning();
            case STRONG -> Theme.success();
        };
    }

    private String currentTotpCode() {
        if (entry == null) {
            return "";
        }
        String code = service.totpCode(entry.totpSecret());
        return code.length() == 6
                ? code.substring(0, 3) + " " + code.substring(3)
                : code;
    }

    private void toggleReveal() {
        boolean revealed = passwordField.getEchoChar() == 0;
        passwordField.setEchoChar(revealed ? '\u25CF' : (char) 0);
    }

    private void tickTotp() {
        totpRing.setRemaining(
                com.shanmukha.vault.crypto.TotpGenerator.remainingSeconds(
                        System.currentTimeMillis()),
                (int) (com.shanmukha.vault.crypto.TotpGenerator.STEP_MS / 1000));
        if (entry != null) {
            totpLabel.setText(currentTotpCode());
        }
    }

    /* ==================================================================== */
    /*  Actions                                                             */
    /* ==================================================================== */

    private void wireButtons() {
        editButton.addActionListener(e -> {
            if (entry != null) {
                EntryDialog dialog = new EntryDialog(
                        SwingUtilities.getWindowAncestor(this), service, entry);
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent event) {
                        if (onSaved != null) {
                            onSaved.run();
                        }
                    }
                });
                dialog.setVisible(true);
            }
        });

        deleteButton.addActionListener(e -> softDelete());
        restoreButton.addActionListener(e -> restore());
        purgeButton.addActionListener(e -> purge());
    }

    private void softDelete() {
        if (entry == null) {
            return;
        }
        int answer = javax.swing.JOptionPane.showConfirmDialog(this,
                "Delete \"" + entry.label() + "\"? It moves to Trash.",
                "Delete entry", javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
        if (answer == javax.swing.JOptionPane.YES_OPTION) {
            runInBackground(entry.id(), id -> service.deleteEntry(id));
        }
    }

    private void restore() {
        if (entry != null) {
            runInBackground(entry.id(), id -> service.restoreEntry(id));
        }
    }

    private void purge() {
        if (entry == null) {
            return;
        }
        int answer = javax.swing.JOptionPane.showConfirmDialog(this,
                "Delete \"" + entry.label() + "\" forever? This cannot be undone.",
                "Delete forever", javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
        if (answer == javax.swing.JOptionPane.YES_OPTION) {
            runInBackground(entry.id(), id -> service.purgeEntry(id));
        }
    }

    /** Runs a mutating service call on a worker thread and refreshes after. */
    private void runInBackground(String id, ThrowingEntryAction action) {
        new SwingWorker<Entry, Void>() {
            @Override
            protected Entry doInBackground() throws Exception {
                return action.run(id);
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof VaultStorageException) {
                        javax.swing.JOptionPane.showMessageDialog(DetailsPanel.this,
                                "Could not update the vault. Please try again.",
                                "Vault action failed", javax.swing.JOptionPane.ERROR_MESSAGE);
                    } else {
                        javax.swing.JOptionPane.showMessageDialog(DetailsPanel.this,
                                "That didn't work. Please try again.",
                                "Vault action failed", javax.swing.JOptionPane.ERROR_MESSAGE);
                    }
                }
                if (onSaved != null) {
                    onSaved.run();
                }
            }
        }.execute();
    }

    private interface ThrowingEntryAction {
        Entry run(String id) throws VaultStorageException, VaultCryptoException;
    }

    /* ==================================================================== */
    /*  Copy                                                                */
    /* ==================================================================== */

    private JButton copyIcon(String tooltip, Supplier<String> source) {
        JButton button = Styles.ghostButton("\u29C9", tooltip);
        button.addActionListener(e -> copy(source.get()));
        return button;
    }

    private void copy(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(value), null);
        new Toast(SwingUtilities.getWindowAncestor(this), "Copied").showAndFade();

        // Spec behavior: the clipboard clears again after 20 seconds.
        new Thread(() -> {
            try {
                Thread.sleep(20_000);
            } catch (InterruptedException ignored) {
                // fall through to clearing
            }
            SwingUtilities.invokeLater(() ->
                    clipboard.setContents(new StringSelection(""), null));
        }, "clipboard-clearer").start();
    }

    /* ==================================================================== */
    /*  Theme                                                               */
    /* ==================================================================== */

    /** Re-applies palette-derived colors after a theme switch. */
    public void refreshTheme() {
        setBackground(Theme.surface());
        header.refresh();
        usernameField.setForeground(Theme.text());
        passwordField.setForeground(Theme.text());
        websiteField.setForeground(Theme.text());
        notesArea.setForeground(Theme.text());
        totpLabel.setForeground(Theme.text());
        editButton.setBackground(Theme.accent());
        editButton.setForeground(Theme.buttonText());
        restoreButton.setBackground(Theme.accent());
        restoreButton.setForeground(Theme.buttonText());
        deleteButton.setForeground(Theme.danger());
        purgeButton.setForeground(Theme.danger());
        strengthLabel.setForeground(areaColor(strengthBar.strength()));
        updatedLabel.setForeground(Theme.muted());
        revalidate();
        repaint();
    }

    /** 1 px vertical hairline on the left edge, painted from Theme. */
    private static final class LeftHairline extends javax.swing.border.AbstractBorder {
        @Override
        public void paintBorder(java.awt.Component c, java.awt.Graphics g,
                                int x, int y, int w, int h) {
            g.setColor(Theme.border());
            g.fillRect(0, 0, 1, h);
        }

        @Override
        public Insets getBorderInsets(java.awt.Component c) {
            return new Insets(0, 1, 0, 0);
        }
    }
}
