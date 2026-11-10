package com.shanmukha.vault.ui;

import com.shanmukha.vault.App;
import com.shanmukha.vault.crypto.StrengthChecker;
import com.shanmukha.vault.crypto.VaultCryptoException;
import com.shanmukha.vault.service.VaultService;
import com.shanmukha.vault.ui.components.LockBadge;
import com.shanmukha.vault.ui.components.StrengthBar;
import com.shanmukha.vault.ui.theme.Styles;
import com.shanmukha.vault.ui.theme.Theme;
import com.shanmukha.vault.util.Validator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.Arrays;

/**
 * Unlock / create-vault screen (UI_SPEC 2.1 and 2.2, mockups login.png and
 * create-vault.png): a centered 400 px rounded card on the window color.
 *
 * <p>Unlock card: centered lock badge, title, master-password field, inline
 * error, full-width Unlock button and a bold create link. Create card:
 * password + confirm fields, the ✓/○ checklist, a four-segment strength bar,
 * the amber "no recovery" warning box and a Create button that stays disabled
 * until the spec's validation rules pass.</p>
 *
 * <p>No crypto here: unlock/create run through {@link VaultService} on a
 * SwingWorker (the 600 000-iteration PBKDF2 must not block the EDT).
 * Password chars are wiped right after use. Wrong passwords trigger an
 * exponential, capped retry delay — never a permanent lock.</p>
 */
public final class LoginFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final String CARD_UNLOCK = "unlock";
    private static final String CARD_CREATE = "create";

    private final VaultService service;

    // Card container.
    private final JPanel card = Styles.card();
    private final CardLayout pages = new CardLayout();
    private final JPanel pageHolder = new JPanel(pages);

    // ------ Unlock page ------
    private final JPasswordField unlockPassword = outlinedPassword();
    private final JPanel unlockRow = fieldRow(unlockPassword);
    private final JLabel unlockError = errorLabel();
    private final JButton unlockButton = Styles.primaryButton("Unlock");
    private final JButton toCreateLink = linkButton("New here? Create a vault");
    private int failedAttempts;
    private int backoffSeconds;
    private final Timer backoffTicker = new Timer(1000, e -> tickBackoff());

    // ------ Create page ------
    private final JPasswordField createPassword = outlinedPassword();
    private final JPanel createRow = fieldRow(createPassword);
    private final JPasswordField confirmPassword = outlinedPassword();
    private final JPanel confirmRow = fieldRow(confirmPassword);
    private final JLabel createError = errorLabel();
    private final StrengthBar strengthBar = new StrengthBar();
    private final JLabel strengthLabel = new JLabel("");

    /** Checklist rows: the ✓/○ state icon and its rule text. */
    private final JLabel lengthCheck = new JLabel("\u25CB");
    private final JLabel lengthText = new JLabel("At least 12 characters");
    private final JLabel varietyCheck = new JLabel("\u25CB");
    private final JLabel varietyText = new JLabel("Upper and lower case, number, symbol");
    private final JLabel matchCheck = new JLabel("\u25CB");
    private final JLabel matchText = new JLabel("Both passwords match");

    private final JButton createButton = Styles.primaryButton("Create vault");
    private final JButton toUnlockLink = linkButton("Already have a vault? Unlock");

    public LoginFrame(VaultService service) {
        this.service = service;

        setTitle("Secure Vault");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(new Dimension(720, 660));
        setMinimumSize(new Dimension(560, 520));
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.window());

        pageHolder.setOpaque(false);
        pageHolder.add(buildUnlockPage(), CARD_UNLOCK);
        pageHolder.add(buildCreatePage(), CARD_CREATE);

        card.setLayout(new BorderLayout());
        card.add(pageHolder, BorderLayout.CENTER);

        // Center the fixed-size 400 px card both directions.
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        card.setPreferredSize(new Dimension(400, 592));
        center.add(card);
        add(center, BorderLayout.CENTER);

        wireBehavior();
        showCard(service.vaultExists() ? CARD_UNLOCK : CARD_CREATE);
        Styles.registerThemeRefresh(this, this::refreshTheme);
        refreshTheme();
    }

    /* ==================================================================== */
    /*  Unlock page                                                         */
    /* ==================================================================== */

    private JPanel buildUnlockPage() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_32, Theme.SPACE_32,
                Theme.SPACE_32, Theme.SPACE_32));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        int row = 0;

        // Centered 56 px lock badge.
        LockBadge badge = new LockBadge(56);
        JPanel badgeRow = new JPanel(new GridBagLayout());
        badgeRow.setOpaque(false);
        badgeRow.add(badge, new GridBagConstraints());
        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_16, 0);
        page.add(badgeRow, gbc);
        row++;

        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_4, 0);
        page.add(centered(Styles.label("Unlock your vault", Theme.FONT_TITLE, true, false)), gbc);
        row++;

        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_24, 0);
        page.add(centered(Styles.label("Enter your master password to continue",
                Theme.FONT_LABEL, false, true)), gbc);
        row++;

        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_8, 0);
        page.add(Styles.fieldLabel("Master password"), gbc);
        row++;

        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_16, 0);
        page.add(unlockRow, gbc);
        row++;

        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_16, 0);
        page.add(unlockError, gbc);
        row++;

        unlockButton.addActionListener(e -> attemptUnlock());
        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, 0, 0);
        page.add(unlockButton, gbc);
        row++;

        toCreateLink.addActionListener(e -> {
            unlockPassword.setText("");
            showCard(CARD_CREATE);
        });
        gbc.gridy = row;
        gbc.insets = new Insets(Theme.SPACE_24, 0, 0, 0);
        page.add(toCreateLink, gbc);
        row++;

        gbc.gridy = row;
        gbc.insets = new Insets(Theme.SPACE_24, 0, 0, 0);
        page.add(footerNote(), gbc);
        return page;
    }

    private static JLabel centered(JLabel label) {
        label.setHorizontalAlignment(JLabel.CENTER);
        return label;
    }

    /* ==================================================================== */
    /*  Create page                                                         */
    /* ==================================================================== */

    private JPanel buildCreatePage() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_32, Theme.SPACE_32,
                Theme.SPACE_32, Theme.SPACE_32));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        int row = 0;

        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_4, 0);
        page.add(centered(Styles.label("Create your vault", Theme.FONT_TITLE, true, false)), gbc);
        row++;

        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_24, 0);
        page.add(centered(Styles.label("Choose a master password — it unlocks everything",
                Theme.FONT_LABEL, false, true)), gbc);
        row++;

        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_8, 0);
        page.add(Styles.fieldLabel("Master password"), gbc);
        row++;

        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_16, 0);
        page.add(createRow, gbc);
        row++;

        // Checklist rows: ✓/○ icon + rule text.
        JPanel checks = new JPanel(new GridLayout(3, 1, 0, Theme.SPACE_4));
        checks.setOpaque(false);
        checks.add(checkRow(lengthCheck, lengthText));
        checks.add(checkRow(varietyCheck, varietyText));
        checks.add(checkRow(matchCheck, matchText));
        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_16, 0);
        page.add(checks, gbc);
        row++;

        // Strength bar + label.
        JPanel strengthRow = new JPanel(new BorderLayout(Theme.SPACE_8, 0));
        strengthRow.setOpaque(false);
        strengthLabel.setFont(strengthLabel.getFont().deriveFont(Font.BOLD, Theme.FONT_LABEL));
        strengthRow.add(strengthBar, BorderLayout.WEST);
        strengthRow.add(strengthLabel, BorderLayout.EAST);
        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_24, 0);
        page.add(strengthRow, gbc);
        row++;

        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_8, 0);
        page.add(Styles.fieldLabel("Confirm password"), gbc);
        row++;

        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_8, 0);
        page.add(confirmRow, gbc);
        row++;

        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_16, 0);
        page.add(createError, gbc);
        row++;

        // Amber warning box (spec: no recovery possible).
        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, Theme.SPACE_24, 0);
        page.add(warningBox(), gbc);
        row++;

        createButton.addActionListener(e -> attemptCreate());
        gbc.gridy = row;
        gbc.insets = new Insets(0, 0, 0, 0);
        page.add(createButton, gbc);
        row++;

        toUnlockLink.addActionListener(e -> {
            createPassword.setText("");
            confirmPassword.setText("");
            updateChecks();
            showCard(CARD_UNLOCK);
        });
        gbc.gridy = row;
        gbc.insets = new Insets(Theme.SPACE_24, 0, 0, 0);
        page.add(toUnlockLink, gbc);
        row++;

        gbc.gridy = row;
        gbc.insets = new Insets(Theme.SPACE_24, 0, 0, 0);
        page.add(footerNote(), gbc);
        return page;
    }

    private static JPanel checkRow(JLabel icon, JLabel text) {
        JPanel row = new JPanel(new BorderLayout(Theme.SPACE_8, 0));
        row.setOpaque(false);
        icon.setFont(icon.getFont().deriveFont(Font.BOLD, Theme.FONT_LABEL));
        text.setFont(text.getFont().deriveFont(Font.PLAIN, Theme.FONT_LABEL));
        row.add(icon, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        return row;
    }

    /** Amber box telling the user the password cannot be recovered. */
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
                + "This password cannot be recovered. If you lose it, the vault and "
                + "everything inside stays encrypted forever.</div></html>");
        body.setFont(body.getFont().deriveFont(Font.PLAIN, Theme.FONT_LABEL));
        body.setForeground(Theme.muted());
        box.add(body, gbc);
        return box;
    }

    /* ==================================================================== */
    /*  Small builders                                                      */
    /* ==================================================================== */

    /** Rounded outlined field row: borderless password field + reveal glyph. */
    private JPanel fieldRow(JPasswordField field) {
        JPanel row = new JPanel(new BorderLayout()) {
            @Override
            protected void paintBorder(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Boolean.TRUE.equals(getClientProperty("field.error"))
                        ? Theme.danger() : Theme.border());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS_FIELD_BUTTON, Theme.RADIUS_FIELD_BUTTON);
                g2.dispose();
            }
        };
        row.setOpaque(true);
        row.setBackground(Theme.surface());
        row.putClientProperty("field.error", Boolean.FALSE);
        row.setPreferredSize(new Dimension(0, 38));

        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder(8, Theme.SPACE_8 + 4,
                8, Theme.SPACE_4));

        JButton reveal = Styles.ghostButton("\u25CE", "Show/hide password");
        reveal.addActionListener(e -> {
            boolean revealed = field.getEchoChar() == 0;
            field.setEchoChar(revealed ? '\u25CF' : (char) 0);
        });
        row.add(field, BorderLayout.CENTER);
        row.add(reveal, BorderLayout.EAST);
        return row;
    }

    /** Marks (or clears) the red outline of a field row. */
    private void setFieldError(JPanel row, boolean error) {
        row.putClientProperty("field.error", error);
        row.repaint();
    }

    private static JLabel errorLabel() {
        JLabel label = new JLabel("");
        label.setFont(label.getFont().deriveFont(Font.PLAIN, Theme.FONT_LABEL));
        label.setForeground(Theme.danger());
        label.setVisible(false);
        return label;
    }

    /** Bold accent text button used to switch between the two cards. */
    private static JButton linkButton(String text) {
        JButton button = new JButton(text);
        button.setFont(button.getFont().deriveFont(Font.BOLD, Theme.FONT_LABEL));
        button.setForeground(Theme.accent());
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setHorizontalAlignment(JLabel.CENTER);
        return button;
    }

    private JLabel footerNote() {
        return centered(Styles.label(
                "Offline and encrypted — data never leaves this device",
                Theme.FONT_HINT, false, true));
    }

    private static JPasswordField outlinedPassword() {
        JPasswordField field = new JPasswordField();
        field.setEchoChar('\u25CF');
        field.setFont(field.getFont().deriveFont(Theme.FONT_BODY));
        return field;
    }

    /* ==================================================================== */
    /*  Behavior wiring                                                     */
    /* ==================================================================== */

    private void wireBehavior() {
        unlockPassword.getDocument().addDocumentListener(
                new javax.swing.event.DocumentListener() {
                    public void insertUpdate(javax.swing.event.DocumentEvent e) { tryEnableUnlock(); }
                    public void removeUpdate(javax.swing.event.DocumentEvent e) { tryEnableUnlock(); }
                    public void changedUpdate(javax.swing.event.DocumentEvent e) { tryEnableUnlock(); }
                });
        createPassword.getDocument().addDocumentListener(
                new javax.swing.event.DocumentListener() {
                    public void insertUpdate(javax.swing.event.DocumentEvent e) { updateChecks(); }
                    public void removeUpdate(javax.swing.event.DocumentEvent e) { updateChecks(); }
                    public void changedUpdate(javax.swing.event.DocumentEvent e) { updateChecks(); }
                });
        confirmPassword.getDocument().addDocumentListener(
                new javax.swing.event.DocumentListener() {
                    public void insertUpdate(javax.swing.event.DocumentEvent e) { updateChecks(); }
                    public void removeUpdate(javax.swing.event.DocumentEvent e) { updateChecks(); }
                    public void changedUpdate(javax.swing.event.DocumentEvent e) { updateChecks(); }
                });

        SwingUtilities.invokeLater(unlockPassword::requestFocusInWindow);
    }

    /** Unlock stays disabled while empty or inside the back-off window. */
    private void tryEnableUnlock() {
        if (backoffTicker.isRunning()) {
            unlockButton.setEnabled(false);
            unlockButton.setText("Unlock (" + backoffSeconds + "s)");
        } else {
            setFieldError(unlockRow, false);
            unlockError.setVisible(false);
            unlockButton.setEnabled(unlockPassword.getPassword().length > 0);
            unlockButton.setText("Unlock");
        }
    }

    /** Re-evaluates the create checklist and the Create button state. */
    private void updateChecks() {
        char[] pw = createPassword.getPassword();
        char[] confirm = confirmPassword.getPassword();

        StrengthChecker.Assessment assessment = StrengthChecker.assess(pw);
        strengthBar.setStrength(assessment.strength(), assessment.reason());
        strengthLabel.setText(strengthText(assessment.strength()));
        strengthLabel.setForeground(areaColor(assessment.strength()));

        boolean longEnough = pw.length >= Validator.MASTER_PASSWORD_MIN;
        markCheck(lengthCheck, lengthText, longEnough);

        boolean noLower = true;
        boolean noUpper = true;
        boolean noDigit = true;
        boolean noSymbol = true;
        for (char c : pw) {
            if (noLower && Character.isLowerCase(c)) {
                noLower = false;
            } else if (noUpper && Character.isUpperCase(c)) {
                noUpper = false;
            } else if (noDigit && Character.isDigit(c)) {
                noDigit = false;
            } else if (noSymbol && !Character.isLetterOrDigit(c)) {
                noSymbol = false;
            }
        }
        boolean varied = !noLower && !noUpper && !noDigit && !noSymbol;
        markCheck(varietyCheck, varietyText, varied);

        boolean matches = pw.length > 0 && Arrays.equals(pw, confirm);
        markCheck(matchCheck, matchText, matches);

        createButton.setEnabled(longEnough && varied && matches
                && assessment.strength() != StrengthChecker.Strength.WEAK);
    }

    private void markCheck(JLabel icon, JLabel text, boolean ok) {
        icon.setText(ok ? "\u2714" : "\u25CB");
        icon.setForeground(ok ? Theme.success() : Theme.muted());
        text.setForeground(ok ? Theme.text() : Theme.muted());
    }

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

    /* ==================================================================== */
    /*  Card switching                                                      */
    /* ==================================================================== */

    private void showCard(String name) {
        pages.show(pageHolder, name);
        boolean creating = CARD_CREATE.equals(name);
        if (creating) {
            updateChecks();
            SwingUtilities.invokeLater(createPassword::requestFocusInWindow);
        } else {
            SwingUtilities.invokeLater(unlockPassword::requestFocusInWindow);
        }
        getRootPane().setDefaultButton(creating ? createButton : unlockButton);
        repaint();
    }

    /* ==================================================================== */
    /*  Unlock (with exponential back-off)                                  */
    /* ==================================================================== */

    private void attemptUnlock() {
        if (backoffTicker.isRunning()) {
            return;
        }
        char[] pw = unlockPassword.getPassword();
        if (pw.length == 0) {
            setFieldError(unlockRow, true);
            unlockError.setText("Enter your master password.");
            unlockError.setVisible(true);
            return;
        }

        final char[] copy = pw.clone();
        Arrays.fill(pw, '\0');
        setBusy(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.unlock(copy);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    failedAttempts = 0;
                    unlockPassword.setText("");
                    dispose();
                    App.openMain(service);
                } catch (Exception ex) {
                    unlockPassword.setText("");
                    // The service holds its own clone; wipe this copy now.
                    handleUnlockFailure(ex);
                }
            }
        }.execute();
    }

    private void handleUnlockFailure(Exception ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        setBusy(false);

        if (cause instanceof VaultCryptoException) {
            // Wrong password or tampered file: same message either way.
            failedAttempts++;
            startBackoff();
            return;
        }
        setFieldError(unlockRow, true);
        unlockError.setText("Could not read the vault file. Please try again.");
        unlockError.setVisible(true);
    }

    /**
     * Exponential but capped retry delay: 2, 4, 8, ... capped at 60 s.
     * Never a permanent lock (spec feature: delay after wrong attempts).
     */
    private void startBackoff() {
        backoffSeconds = Math.min(60, 1 << Math.min(failedAttempts, 6));
        unlockButton.setEnabled(false);
        unlockButton.setText("Unlock (" + backoffSeconds + "s)");
        setFieldError(unlockRow, true);
        unlockError.setText("Wrong master password. Try again in " + backoffSeconds + "s.");
        unlockError.setVisible(true);
        backoffTicker.restart();
    }

    private void tickBackoff() {
        backoffSeconds--;
        if (backoffSeconds <= 0) {
            backoffTicker.stop();
            setFieldError(unlockRow, false);
            unlockError.setVisible(false);
            unlockButton.setEnabled(unlockPassword.getPassword().length > 0);
            unlockButton.setText("Unlock");
        } else {
            unlockButton.setText("Unlock (" + backoffSeconds + "s)");
            unlockError.setText("Wrong master password. Try again in " + backoffSeconds + "s.");
        }
    }

    private void setBusy(boolean busy) {
        if (backoffTicker.isRunning()) {
            return; // back-off state owns the button text
        }
        unlockButton.setEnabled(!busy && unlockPassword.getPassword().length > 0);
        unlockButton.setText(busy ? "Unlocking\u2026" : "Unlock");
    }

    /* ==================================================================== */
    /*  Create                                                              */
    /* ==================================================================== */

    private void attemptCreate() {
        if (!createButton.isEnabled()) {
            return;
        }
        final char[] copy = createPassword.getPassword().clone();
        createPassword.setText("");
        confirmPassword.setText("");
        updateChecks();
        createButton.setEnabled(false);
        createButton.setText("Creating\u2026");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.createVault(copy);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    Arrays.fill(copy, '\0');
                    dispose();
                    App.openMain(service);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    Arrays.fill(copy, '\0');
                    if (cause instanceof IllegalStateException) {
                        // A vault appeared meanwhile: switch to unlock.
                        createError.setVisible(false);
                        switchCardAndReset();
                        return;
                    }
                    createError.setText("Could not create the vault. Please try again.");
                    createError.setVisible(true);
                    createButton.setEnabled(true);
                    createButton.setText("Create vault");
                    updateChecks();
                }
            }
        }.execute();
    }

    private void switchCardAndReset() {
        createButton.setEnabled(false);
        createButton.setText("Create vault");
        showCard(CARD_UNLOCK);
    }

    /* ==================================================================== */
    /*  Theme switching                                                     */
    /* ==================================================================== */

    private void refreshTheme() {
        getContentPane().setBackground(Theme.window());
        unlockButton.setBackground(Theme.accent());
        unlockButton.setForeground(Theme.buttonText());
        createButton.setBackground(Theme.accent());
        createButton.setForeground(Theme.buttonText());
        toCreateLink.setForeground(Theme.accent());
        toUnlockLink.setForeground(Theme.accent());
        updateChecks();
        if (strengthLabel.getText().isEmpty()) {
            strengthLabel.setForeground(Theme.muted());
        }
        card.repaint();
        repaint();
    }
}
