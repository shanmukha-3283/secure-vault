package com.shanmukha.vault.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.shanmukha.vault.crypto.StrengthChecker;
import com.shanmukha.vault.crypto.VaultCryptoException;
import com.shanmukha.vault.service.VaultService;
import com.shanmukha.vault.storage.VaultStorageException;
import com.shanmukha.vault.ui.components.StrengthBar;
import com.shanmukha.vault.ui.components.Toast;
import com.shanmukha.vault.ui.theme.Styles;
import com.shanmukha.vault.ui.theme.Theme;
import com.shanmukha.vault.util.Validator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

/**
 * Change master password dialog (bonus feature), styled after
 * the add-item card: undecorated rounded card, title + ✕, inline red errors
 * under each field (never popup boxes), amber "no recovery" warning box and
 * a Cancel / Save footer.
 *
 * <p>Save stays disabled until the current password is filled in, the new
 * password passes {@link Validator#validateMasterPassword(char[])} and the
 * confirmation matches. The slow re-encryption (two PBKDF2 passes) runs in a
 * {@link SwingWorker} so the event dispatch thread never blocks; while it
 * runs the fields and buttons are disabled and the Save button shows
 * "Updating…". All {@code char[]} copies are wiped when no longer needed.</p>
 */
public final class ChangePasswordDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final VaultService service;

    // Fields.
    private final JPasswordField currentField = passwordField();
    private final JPasswordField newField = passwordField();
    private final JPasswordField confirmField = passwordField();

    // Strength feedback for the new password.
    private final StrengthBar strengthBar = new StrengthBar();
    private final JLabel strengthLabel = new JLabel("");

    // Inline errors.
    private final JLabel currentError = errorLabel();
    private final JLabel newError = errorLabel();
    private final JLabel confirmError = errorLabel();
    private final JLabel generalError = errorLabel();

    private final JButton saveButton = Styles.primaryButton("Save");
    private final JButton cancelButton = new JButton("Cancel");
    private boolean busy;

    public ChangePasswordDialog(java.awt.Window owner, VaultService service) {
        super(owner);
        this.service = service;

        setTitle("Change master password");
        setModalityType(ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        setResizable(false);
        setSize(new Dimension(560, 590));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.window());

        buildLayout();

        // Esc closes; Enter saves when valid.
        javax.swing.JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                "dialog.esc");
        root.getActionMap().put("dialog.esc", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0),
                "dialog.enter");
        root.getActionMap().put("dialog.enter", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (saveButton.isEnabled()) {
                    attemptSave();
                }
            }
        });

        DocumentListener listener = new FieldListener();
        currentField.getDocument().addDocumentListener(listener);
        newField.getDocument().addDocumentListener(listener);
        confirmField.getDocument().addDocumentListener(listener);
        evaluate();

        Styles.registerThemeRefresh(this, this::refreshTheme);
        refreshTheme();
    }

    /* ==================================================================== */
    /*  Layout                                                              */
    /* ==================================================================== */

    private void buildLayout() {
        JPanel card = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.surface());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS_CARD_DIALOG, Theme.RADIUS_CARD_DIALOG);
                g2.setColor(Theme.border());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS_CARD_DIALOG - 1, Theme.RADIUS_CARD_DIALOG - 1);
                g2.dispose();
            }
        };
        card.setOpaque(false);

        // ---- Title bar (draggable) + close ----
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.setBorder(BorderFactory.createEmptyBorder(
                Theme.SPACE_24, Theme.SPACE_24, Theme.SPACE_16, Theme.SPACE_16));
        JLabel title = Styles.label("Change master password", 20f, true, false);
        titleBar.add(title, BorderLayout.WEST);

        JButton close = Styles.ghostButton("\u2715", "Close");
        close.addActionListener(e -> dispose());
        titleBar.add(close, BorderLayout.EAST);

        javax.swing.event.MouseInputAdapter drag = new javax.swing.event.MouseInputAdapter() {
            private java.awt.Point press;

            @Override
            public void mousePressed(MouseEvent e) {
                press = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                java.awt.Point onScreen = e.getLocationOnScreen();
                setLocation(onScreen.x - press.x, onScreen.y - press.y);
            }
        };
        titleBar.addMouseListener(drag);
        titleBar.addMouseMotionListener(drag);

        // ---- Form area ----
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(
                Theme.SPACE_8, Theme.SPACE_24, Theme.SPACE_24, Theme.SPACE_24));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        int row = 0;
        row = place(form, gbc, row, passwordRow("Current password", currentField,
                currentError), 0);
        row = place(form, gbc, row, newPasswordRow(), Theme.SPACE_16);
        row = place(form, gbc, row, passwordRow("Confirm new password", confirmField,
                confirmError), Theme.SPACE_16);
        row = place(form, gbc, row, warningBox(), Theme.SPACE_24);
        row = place(form, gbc, row, generalError, Theme.SPACE_8);

        form.add(new JPanel() {
            {
                setOpaque(false);
            }
        }, bottomStretcher(gbc));

        // Footer: hairline + Cancel/Save buttons bottom right.
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(
                0, Theme.SPACE_24, Theme.SPACE_24, Theme.SPACE_24));
        footer.add(Styles.hline(), BorderLayout.NORTH);
        JPanel footerButtons = new JPanel(new GridLayout(1, 2, Theme.SPACE_8, 0));
        footerButtons.setOpaque(false);
        footerButtons.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_16, 0, 0, 0));
        cancelButton.addActionListener(e -> dispose());
        saveButton.addActionListener(e -> attemptSave());
        saveButton.setEnabled(false);
        footerButtons.add(cancelButton);
        footerButtons.add(saveButton);
        footer.add(footerButtons, BorderLayout.CENTER);

        card.add(titleBar, BorderLayout.NORTH);
        card.add(form, BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        card.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_4, Theme.SPACE_4,
                Theme.SPACE_4, Theme.SPACE_4));
        add(card, BorderLayout.CENTER);
        getRootPane().setDefaultButton(saveButton);
    }

    private static GridBagConstraints bottomStretcher(GridBagConstraints gbc) {
        gbc.gridy = 99;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.VERTICAL;
        return gbc;
    }

    private int place(JPanel parent, GridBagConstraints gbc, int row,
                      javax.swing.JComponent component, int top) {
        gbc.gridy = row;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(top, 0, 0, 0);
        parent.add(component, gbc);
        return row + 1;
    }

    /* ==================================================================== */
    /*  Row builders                                                        */
    /* ==================================================================== */

    /** Label + borderless field in a bordered row + reveal button + error. */
    private JPanel passwordRow(String labelText, JPasswordField field, JLabel error) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        row.add(Styles.fieldLabel(labelText), g);

        JPanel values = new JPanel(new GridBagLayout());
        values.setOpaque(false);
        GridBagConstraints v = new GridBagConstraints();
        v.fill = GridBagConstraints.HORIZONTAL;
        v.weightx = 1;
        v.gridy = 0;
        values.add(field, v);

        JButton reveal = Styles.ghostButton("\u25CE", "Show/hide password");
        reveal.addActionListener(e -> {
            boolean revealed = field.getEchoChar() == 0;
            field.setEchoChar(revealed ? '\u25CF' : (char) 0);
        });
        v.weightx = 0;
        v.gridx = 1;
        v.insets = new Insets(0, Theme.SPACE_8, 0, 0);
        values.add(reveal, v);

        g.gridy = 1;
        g.insets = new Insets(Theme.SPACE_8, 0, 0, 0);
        row.add(values, g);

        g.gridy = 2;
        g.insets = new Insets(Theme.SPACE_4, 0, 0, 0);
        row.add(error, g);
        return row;
    }

    /** New password row + strength bar with its label beside it. */
    private JPanel newPasswordRow() {
        JPanel row = passwordRow("New password", newField, newError);

        JPanel strength = new JPanel(new GridBagLayout());
        strength.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        strength.add(strengthBar, g);
        strengthLabel.setFont(strengthLabel.getFont().deriveFont(Font.BOLD, Theme.FONT_LABEL));
        GridBagConstraints l = (GridBagConstraints) g.clone();
        l.gridx = 1;
        l.weightx = 0;
        l.fill = GridBagConstraints.NONE;
        l.anchor = GridBagConstraints.EAST;
        strength.add(strengthLabel, l);

        GridBagConstraints g2 = new GridBagConstraints();
        g2.gridy = 3;
        g2.gridx = 0;
        g2.fill = GridBagConstraints.HORIZONTAL;
        g2.weightx = 1;
        g2.insets = new Insets(Theme.SPACE_8, 0, 0, 0);
        row.add(strength, g2);
        return row;
    }

    /** Amber box from the create screen: re-keying has no recovery either. */
    private JPanel warningBox() {
        JPanel box = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.warningBg());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS_FIELD_BUTTON, Theme.RADIUS_FIELD_BUTTON);
                g2.setColor(Theme.warningBorder());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS_FIELD_BUTTON, Theme.RADIUS_FIELD_BUTTON);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_16, Theme.SPACE_16,
                Theme.SPACE_16, Theme.SPACE_16));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        JLabel title = Styles.label("Keep it safe", Theme.FONT_LABEL, true, false);
        title.setForeground(Theme.warningTitle());
        box.add(title, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(Theme.SPACE_4, 0, 0, 0);
        JLabel body = new JLabel("<html><div style='width:300px'>"
                + "Changing this re-encrypts the whole vault. Like before, the new "
                + "password cannot be recovered.</div></html>");
        body.setFont(body.getFont().deriveFont(Font.PLAIN, Theme.FONT_LABEL));
        body.setForeground(Theme.muted());
        box.add(body, gbc);
        return box;
    }

    /* ==================================================================== */
    /*  Small builders                                                      */
    /* ==================================================================== */

    private static JPasswordField passwordField() {
        JPasswordField field = new JPasswordField();
        field.setEchoChar('\u25CF');
        field.setFont(field.getFont().deriveFont(Theme.FONT_BODY));
        return field;
    }

    private static JLabel errorLabel() {
        JLabel error = new JLabel("");
        error.setFont(error.getFont().deriveFont(Font.PLAIN, Theme.FONT_HINT));
        error.setForeground(Theme.danger());
        error.setVisible(false);
        return error;
    }

    /* ==================================================================== */
    /*  Validation                                                          */
    /* ==================================================================== */

    private class FieldListener implements DocumentListener {
        public void insertUpdate(DocumentEvent e) { evaluate(); }
        public void removeUpdate(DocumentEvent e) { evaluate(); }
        public void changedUpdate(DocumentEvent e) { evaluate(); }
    }

    /** Mirrors the create flow: full {@link Validator} rules, inline errors. */
    private void evaluate() {
        char[] current = currentField.getPassword();
        char[] next = newField.getPassword();
        char[] confirm = confirmField.getPassword();
        try {
            StrengthChecker.Assessment assessment = StrengthChecker.assess(next);
            strengthBar.setStrength(assessment.strength(), assessment.reason());
            strengthLabel.setText(strengthText(assessment.strength()));
            strengthLabel.setForeground(strengthColor(assessment.strength()));

            clearAllErrors();
            boolean valid = true;

            if (current.length == 0) {
                show(currentField, currentError, "Current password is required.");
                valid = false;
            }
            if (next.length == 0) {
                show(newField, newError, "New password is required.");
                valid = false;
            } else {
                List<String> rules = Validator.validateMasterPassword(next);
                if (!rules.isEmpty()) {
                    show(newField, newError, String.join(" ", rules));
                    valid = false;
                }
            }
            if (!Arrays.equals(next, confirm)) {
                show(confirmField, confirmError, "Passwords do not match.");
                valid = false;
            }

            saveButton.setEnabled(valid);
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(next, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    private void show(javax.swing.JComponent field, JLabel error, String message) {
        error.setText(message);
        error.setVisible(true);
        field.putClientProperty(FlatClientProperties.OUTLINE,
                FlatClientProperties.OUTLINE_ERROR);
    }

    private void clearAllErrors() {
        for (JLabel error : new JLabel[]{currentError, newError, confirmError,
                generalError}) {
            error.setText("");
            error.setVisible(false);
        }
        for (JComponent field : new JComponent[]{currentField, newField,
                confirmField}) {
            field.putClientProperty(FlatClientProperties.OUTLINE, null);
        }
    }

    private static String strengthText(StrengthChecker.Strength strength) {
        return switch (strength) {
            case WEAK -> "Weak";
            case MEDIUM -> "Okay";
            case STRONG -> "Strong";
        };
    }

    private static Color strengthColor(StrengthChecker.Strength strength) {
        return switch (strength) {
            case WEAK -> Theme.danger();
            case MEDIUM -> Theme.warning();
            case STRONG -> Theme.success();
        };
    }

    /* ==================================================================== */
    /*  Save                                                                */
    /* ==================================================================== */

    private void attemptSave() {
        if (busy) {
            return;
        }
        char[] current = currentField.getPassword();
        char[] next = newField.getPassword();
        char[] confirm = confirmField.getPassword();
        try {
            // Worker copies so the caller-owned snapshots can be wiped here.
            final char[] currentCopy = current.clone();
            final char[] nextCopy = next.clone();
            setBusy(true);

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    service.changeMasterPassword(currentCopy, nextCopy);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                    } catch (Exception ex) {
                        Arrays.fill(currentCopy, '\0');
                        Arrays.fill(nextCopy, '\0');
                        setBusy(false);
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        if (cause instanceof VaultCryptoException) {
                            // Wrong current password or a tampered file: the
                            // same honest message as the unlock screen. The
                            // vault on disk was not touched either way.
                            show(currentField, currentError,
                                    "Current password is wrong.");
                            currentField.selectAll();
                        } else if (cause instanceof VaultStorageException) {
                            generalError.setText(
                                    "Could not re-encrypt the vault. Try again.");
                            generalError.setVisible(true);
                        } else {
                            generalError.setText(
                                    "Something went wrong while saving. Try again.");
                            generalError.setVisible(true);
                        }
                        return;
                    }
                    Arrays.fill(currentCopy, '\0');
                    Arrays.fill(nextCopy, '\0');
                    dispose();
                    new Toast(getOwner(), "Master password changed").showAndFade();
                }
            };
            worker.execute();
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(next, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    /** Disables the form while the slow re-encryption runs. */
    private void setBusy(boolean value) {
        busy = value;
        currentField.setEnabled(!value);
        newField.setEnabled(!value);
        confirmField.setEnabled(!value);
        cancelButton.setEnabled(!value);
        saveButton.setText(value ? "Updating\u2026" : "Save");
        saveButton.setEnabled(false);
        if (!value) {
            // Same inputs, nothing changed while busy: evaluate() restores
            // the Save button state with the normal validation path.
            evaluate();
        }
    }

    /* ==================================================================== */
    /*  Theme                                                               */
    /* ==================================================================== */

    private void refreshTheme() {
        currentField.setForeground(Theme.text());
        newField.setForeground(Theme.text());
        confirmField.setForeground(Theme.text());
        strengthLabel.setForeground(strengthColor(strengthBar.strength()));
        cancelButton.setBackground(Theme.surface());
        cancelButton.setForeground(Theme.text());
        saveButton.setBackground(Theme.accent());
        saveButton.setForeground(Theme.buttonText());
        repaint();
    }
}
