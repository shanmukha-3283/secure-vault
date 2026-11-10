package com.shanmukha.vault.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.shanmukha.vault.crypto.PasswordGenerator;
import com.shanmukha.vault.crypto.StrengthChecker;
import com.shanmukha.vault.model.Entry;
import com.shanmukha.vault.model.EntryType;
import com.shanmukha.vault.service.DuplicateLabelException;
import com.shanmukha.vault.service.VaultService;
import com.shanmukha.vault.storage.VaultStorageException;
import com.shanmukha.vault.ui.components.StrengthBar;
import com.shanmukha.vault.ui.components.Toast;
import com.shanmukha.vault.ui.theme.Styles;
import com.shanmukha.vault.ui.theme.Theme;
import com.shanmukha.vault.util.Validator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Add / edit item dialog (UI_SPEC 2.4, mockup add-item-dialog.png): 560 px
 * rounded card with a title bar ("Add item" + ✕), a segmented control
 * (Login | Secure note | Card), fields with * markers, inline red errors
 * (never popup boxes), a Generate button next to the password, a folder
 * dropdown, a favorite checkbox and a Cancel / Save footer.
 *
 * <p>Save stays disabled until every visible required field passes the
 * {@link Validator}; Enter saves when valid, Esc closes.</p>
 */
public final class EntryDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final VaultService service;
    private final Entry editing; // null when adding

    // Fields.
    private final JTextField nameField = outlinedField();
    private final JTextField usernameField = outlinedField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JTextField websiteField = outlinedField();
    private final JTextArea notesArea = outlinedNotes();
    private final JCheckBox favoriteCheck = new JCheckBox("Favorite");
    private final JLabel favoriteStar = new JLabel("\u2605");
    private final JComboBox<String> folderCombo = new JComboBox<>(new String[]{
            "", "Personal", "Work", "Finance", "Health"});

    // Strength feedback.
    private final StrengthBar strengthBar = new StrengthBar();
    private final JLabel strengthLabel = new JLabel("");

    // Inline errors.
    private final JLabel nameError = errorLabel();
    private final JLabel usernameError = errorLabel();
    private final JLabel passwordError = errorLabel();
    private final JLabel websiteError = errorLabel();

    // Row visibility groups (label + field + error are toggled together).
    private final JPanel usernameRow = new JPanel(new GridBagLayout());
    private final JPanel passwordRow = new JPanel(new GridBagLayout());
    private final JPanel websiteRow = new JPanel(new GridBagLayout());
    private final JPanel folderRow = new JPanel(new GridBagLayout());
    private final JPanel favoriteRow = new JPanel(new GridBagLayout());
    private final JPanel notesRow = new JPanel(new GridBagLayout());
    private final JPanel strengthRow = new JPanel(new GridBagLayout());
    private final JLabel usernameLabel = Styles.fieldLabel("Username *");
    private final JLabel passwordLabel = Styles.fieldLabel("Password *");

    // Segmented control.
    private final JButton loginSeg = new JButton("Login");
    private final JButton noteSeg = new JButton("Secure note");
    private final JButton cardSeg = new JButton("Card");

    private final JButton saveButton = Styles.primaryButton("Save");
    private final JButton cancelButton = new JButton("Cancel");

    private EntryType currentType = EntryType.LOGIN;

    public EntryDialog(java.awt.Window owner, VaultService service, Entry editing) {
        super(owner);
        this.service = service;
        this.editing = editing;

        setTitle(editing == null ? "Add item" : "Edit item");
        setModalityType(ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        setSize(new Dimension(560, 660));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.window());

        favoriteCheck.setOpaque(false);
        passwordField.setEchoChar('\u25CF');

        // Live validation on every field.
        FieldListener listener = new FieldListener();
        nameField.getDocument().addDocumentListener(listener);
        usernameField.getDocument().addDocumentListener(listener);
        passwordField.getDocument().addDocumentListener(listener);
        websiteField.getDocument().addDocumentListener(listener);
        notesArea.getDocument().addDocumentListener(listener);
        favoriteCheck.addActionListener(e -> favoriteStar.setVisible(favoriteCheck.isSelected()));

        buildLayout();
        refreshSegmentStyles();
        refreshVisibility();
        evaluate();

        if (editing != null) {
            loadEntry(editing);
        }

        // Esc closes; Enter saves when valid.
        javax.swing.JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                "dialog.esc");
        root.getActionMap().put("dialog.esc", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0),
                "dialog.enter");
        root.getActionMap().put("dialog.enter", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (saveButton.isEnabled()) {
                    attemptSave();
                }
            }
        });

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
        JLabel title = Styles.label(editing == null ? "Add item" : "Edit item",
                20f, true, false);
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
                Theme.SPACE_16, Theme.SPACE_24, Theme.SPACE_24, Theme.SPACE_24));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        int rowIndex = 0;
        rowIndex = place(form, gbc, rowIndex, segmentedControl(), 0);
        rowIndex = place(form, gbc, rowIndex, labeledField("Name *", nameField, nameError),
                Theme.SPACE_16);
        usernameRow.setLayout(new GridBagLayout());
        usernameRow.setOpaque(false);
        rowIndex = place(form, gbc, rowIndex, wrap(usernameLabel, usernameField,
                usernameError, usernameRow), Theme.SPACE_16);
        passwordRow.setLayout(new GridBagLayout());
        passwordRow.setOpaque(false);
        rowIndex = place(form, gbc, rowIndex, wrapPasswordRow(), Theme.SPACE_8);
        rowIndex = place(form, gbc, rowIndex, strengthBarWithLabel(), 0);
        rowIndex = place(form, gbc, rowIndex, labeledField("Website", websiteField,
                websiteError), Theme.SPACE_16);
        rowIndex = place(form, gbc, rowIndex, wrap(Styles.fieldLabel("Folder"), folderCombo,
                null, folderRow), Theme.SPACE_16);
        rowIndex = place(form, gbc, rowIndex, favoriteRow(), Theme.SPACE_4);
        rowIndex = place(form, gbc, rowIndex, wrap(Styles.fieldLabel("Notes"), scroll(notesArea),
                null, notesRow), Theme.SPACE_16);

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

        form.add(new JPanel(), bottomStretcher(gbc));

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
    /*  Segmented control                                                   */
    /* ==================================================================== */

    /** Track with three segments; the selected one gets the surface fill. */
    private JPanel segmentedControl() {
        JPanel track = new JPanel(new GridLayout(1, 3, 4, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.rowHover());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                        Theme.RADIUS_FIELD_BUTTON, Theme.RADIUS_FIELD_BUTTON);
                g2.dispose();
            }
        };
        track.setOpaque(false);
        track.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        track.add(loginSeg);
        track.add(noteSeg);
        track.add(cardSeg);
        track.setPreferredSize(new Dimension(0, 40));

        loginSeg.addActionListener(e -> setType(EntryType.LOGIN));
        noteSeg.addActionListener(e -> setType(EntryType.SECURE_NOTE));
        cardSeg.addActionListener(e -> setType(EntryType.CARD));
        for (JButton seg : new JButton[]{loginSeg, noteSeg, cardSeg}) {
            seg.setFocusPainted(false);
            seg.setBorderPainted(false);
            seg.setContentAreaFilled(true);
            seg.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }
        return track;
    }

    private void refreshSegmentStyles() {
        styledSegment(loginSeg, currentType == EntryType.LOGIN);
        styledSegment(noteSeg, currentType == EntryType.SECURE_NOTE);
        styledSegment(cardSeg, currentType == EntryType.CARD);
    }

    /** Selected: surface fill + accent bold; unselected: muted. */
    private void styledSegment(JButton seg, boolean selected) {
        seg.setFont(seg.getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN,
                selected ? Theme.FONT_BODY : Theme.FONT_BODY));
        seg.setBackground(selected ? Theme.surface() : new Color(0, 0, 0, 0));
        seg.setForeground(selected ? Theme.accent() : Theme.muted());
        seg.setOpaque(true);
    }

    /* ==================================================================== */
    /*  Row wrappers                                                        */
    /* ==================================================================== */

    private JPanel labeledField(String labelText, JTextField field, JLabel error) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        g.gridy = 0;
        row.add(Styles.fieldLabel(labelText), g);
        g.gridy = 1;
        g.insets = new Insets(Theme.SPACE_8, 0, 0, 0);
        row.add(field, g);
        g.gridy = 2;
        g.insets = new Insets(Theme.SPACE_4, 0, 0, 0);
        row.add(error, g);
        return row;
    }

    /** Label + component (+ optional error) consolidated into one row panel. */
    private JPanel wrap(JLabel label, javax.swing.JComponent field, JLabel error,
                        JPanel target) {
        target.removeAll();
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        g.gridy = 0;
        target.add(label, g);
        g.gridy = 1;
        g.insets = new Insets(Theme.SPACE_8, 0, 0, 0);
        target.add(field, g);
        if (error != null) {
            g.gridy = 2;
            g.insets = new Insets(Theme.SPACE_4, 0, 0, 0);
            target.add(error, g);
        }
        return target;
    }

    /** Password row: label + field + reveal + Generate. */
    private JPanel wrapPasswordRow() {
        passwordRow.removeAll();
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        g.gridy = 0;
        passwordRow.add(passwordLabel, g);

        JPanel values = new JPanel(new GridBagLayout());
        values.setOpaque(false);
        GridBagConstraints v = new GridBagConstraints();
        v.fill = GridBagConstraints.HORIZONTAL;
        v.weightx = 1;
        v.gridy = 0;
        values.add(passwordField, v);

        JButton reveal = Styles.ghostButton("\u25CE", "Show/hide password");
        reveal.addActionListener(e -> {
            boolean revealed = passwordField.getEchoChar() == 0;
            passwordField.setEchoChar(revealed ? '\u25CF' : (char) 0);
        });
        v.weightx = 0;
        v.gridx = 1;
        v.insets = new Insets(0, Theme.SPACE_8, 0, 0);
        values.add(reveal, v);

        JButton generate = new JButton("Generate");
        generate.setFont(generate.getFont().deriveFont(Font.BOLD, Theme.FONT_LABEL));
        generate.setForeground(Theme.accent());
        generate.setFocusPainted(false);
        generate.setContentAreaFilled(false);
        generate.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        generate.setBorder(new Styles.HoverBorder(Theme::accent, Theme::accentHover,
                Theme::rowHover));
        generate.addActionListener(e -> generatePassword());
        GridBagConstraints g2 = new GridBagConstraints();
        g2.fill = GridBagConstraints.HORIZONTAL;
        g2.weightx = 0;
        g2.gridx = 1;
        g2.insets = new Insets(0, Theme.SPACE_8, 0, 0);
        values.add(generate, g2);

        g.gridy = 1;
        g.insets = new Insets(Theme.SPACE_8, 0, 0, 0);
        passwordRow.add(values, g);

        g.gridy = 2;
        g.insets = new Insets(Theme.SPACE_4, 0, 0, 0);
        passwordRow.add(passwordError, g);
        return passwordRow;
    }

    /** Bar with its label beside it. */
    private JPanel strengthBarWithLabel() {
        strengthRow.removeAll();
        strengthRow.setOpaque(false);
        strengthRow.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        strengthRow.add(strengthBar, g);
        strengthLabel.setFont(strengthLabel.getFont().deriveFont(Font.BOLD, Theme.FONT_LABEL));
        GridBagConstraints l = (GridBagConstraints) g.clone();
        l.gridx = 1;
        l.weightx = 0;
        l.fill = GridBagConstraints.NONE;
        l.anchor = GridBagConstraints.EAST;
        strengthRow.add(strengthLabel, l);
        return strengthRow;
    }

    private JPanel favoriteRow() {
        favoriteRow.removeAll();
        favoriteRow.setOpaque(false);
        favoriteRow.setLayout(new GridBagLayout());
        favoriteStar.setFont(favoriteStar.getFont().deriveFont(15f));
        favoriteStar.setForeground(Theme.star());
        java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
        g.gridx = 0;
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.NONE;
        favoriteRow.add(favoriteCheck, g);
        g.gridx = 1;
        favoriteRow.add(favoriteStar, g);
        g.gridx = 2;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        favoriteRow.add(new JPanel() {
            {
                setOpaque(false);
            }
        }, g);
        return favoriteRow;
    }

    private javax.swing.JScrollPane scroll(javax.swing.JTextArea area) {
        area.setRows(4);
        java.awt.Dimension size = area.getPreferredSize();
        area.setPreferredSize(new Dimension((int) size.getWidth(), 96));
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        return scroll;
    }

    private static JLabel errorLabel() {
        JLabel error = new JLabel("");
        error.setFont(error.getFont().deriveFont(Font.PLAIN, Theme.FONT_HINT));
        error.setForeground(Theme.danger());
        error.setVisible(false);
        return error;
    }

    private static JTextField outlinedField() {
        JTextField field = new JTextField();
        field.setFont(field.getFont().deriveFont(Theme.FONT_BODY));
        return field;
    }

    private static JTextArea outlinedNotes() {
        JTextArea area = new JTextArea();
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(area.getFont().deriveFont(Theme.FONT_BODY));
        area.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Optional notes");
        return area;
    }

    /* ==================================================================== */
    /*  Type switching / validation                                         */
    /* ==================================================================== */

    private void setType(EntryType type) {
        currentType = type;
        refreshSegmentStyles();
        refreshVisibility();
        evaluate();
    }

    /**
     * Login: name, username, password (+strength), website, folder,
     * favorite, notes. Secure note: name + notes (+folder/favorite).
     * Card: name, username ("Name on card"), password ("PIN"), folder,
     * favorite.
     */
    private void refreshVisibility() {
        boolean login = currentType == EntryType.LOGIN;
        boolean secure = currentType == EntryType.SECURE_NOTE;
        boolean card = currentType == EntryType.CARD;

        usernameLabel.setText(card ? "Name on card *" : "Username *");
        passwordLabel.setText(card ? "PIN *" : "Password *");
        passwordField.setEchoChar('\u25CF');

        usernameRow.setVisible(!secure);
        passwordRow.setVisible(login || card);
        strengthRow.setVisible(login || card);
        websiteRow.setVisible(login || card);
        strengthRowTop();
        loginSeg.setText("Login");
        noteSeg.setText("Secure note");
        cardSeg.setText("Card");
        revalidate();
        repaint();
    }

    private void strengthRowTop() {
        // no-op hook for future spacing tweaks
    }

    private class FieldListener implements javax.swing.event.DocumentListener {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { evaluate(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { evaluate(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { evaluate(); }
    }

    private void evaluate() {
        String name = nameField.getText().trim();
        String username = usernameField.getText().trim();
        char[] pw = passwordField.getPassword();
        String website = websiteField.getText().trim();
        try {
            StrengthChecker.Assessment assessment = StrengthChecker.assess(pw);
            strengthBar.setStrength(assessment.strength(), assessment.reason());
            strengthLabel.setText(strengthText(assessment.strength()));
            strengthLabel.setForeground(strengthColor(assessment.strength()));

            java.util.List<String> errors = new java.util.ArrayList<>();
            boolean login = currentType != EntryType.SECURE_NOTE;
            boolean secureNote = currentType == EntryType.SECURE_NOTE;

            if (name.isEmpty()) {
                errors.add("Label is required.");
            } else if (name.length() > Validator.LABEL_MAX) {
                errors.add("Label must be " + Validator.LABEL_MAX + " characters or fewer.");
            }
            if (login && username.isEmpty()) {
                errors.add((currentType == EntryType.CARD
                        ? "Name on card" : "Username") + " is required.");
            } else if (login && username.length() > Validator.USERNAME_MAX) {
                errors.add("Username must be " + Validator.USERNAME_MAX + " characters or fewer.");
            }

            String passwordString = new String(pw);
            if (!secureNote && passwordString.isEmpty()) {
                errors.add((currentType == EntryType.CARD ? "PIN" : "Password")
                        + " is required.");
            }
            if (website.length() > Validator.URL_MAX) {
                errors.add("Website must be " + Validator.URL_MAX + " characters or fewer.");
            }
            if (!Validator.validateEntry(name, login ? username : "x",
                    secureNote ? "x" : passwordString, website,
                    notesArea.getText(), java.util.List.of(), null).isEmpty())
            {
                // Fall back to the shared check for URL/note/limits; required
                // checks above handle per-type needs.
                java.util.List<String> shared = Validator.validateEntry(
                        name, login ? username : "x", secureNote ? "x" : "Shared",
                        website, notesArea.getText(), java.util.List.of(), null);
                for (String sharedError : shared) {
                    if (sharedError.startsWith("URL") || sharedError.startsWith("Notes")) {
                        errors.add(sharedError);
                    }
                }
            }

            clearAllErrors();
            boolean valid = true;
            for (String errorText : errors) {
                if (errorText.startsWith("Label")) {
                    show(nameField, nameError, errorText);
                    valid = false;
                } else if (errorText.startsWith("Username")) {
                    show(usernameField, usernameError, errorText);
                    valid = false;
                } else if (errorText.startsWith("Name on card")
                        || errorText.startsWith("Password")
                        || errorText.startsWith("PIN")) {
                    show(passwordField, passwordError, errorText);
                    valid = false;
                } else if (errorText.startsWith("URL") || errorText.startsWith("Website")) {
                    show(websiteField, websiteError, errorText);
                    valid = false;
                } else {
                    // Unexpected messages just keep the save disabled.
                    valid = false;
                }
            }
            saveButton.setEnabled(valid);
        } finally {
            java.util.Arrays.fill(pw, '\0');
        }
    }

    private void show(javax.swing.JComponent field, JLabel error, String message) {
        error.setText(message);
        error.setVisible(true);
        field.putClientProperty(FlatClientProperties.OUTLINE,
                FlatClientProperties.OUTLINE_ERROR);
    }

    private void clearAllErrors() {
        for (JLabel error : new JLabel[]{nameError, usernameError, passwordError,
                websiteError}) {
            error.setText("");
            error.setVisible(false);
        }
        for (JComponent field : new JComponent[]{nameField, usernameField,
                passwordField, websiteField}) {
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
    /*  Generate / load / save                                              */
    /* ==================================================================== */

    private void generatePassword() {
        String generated = PasswordGenerator.generate(16, true);
        passwordField.setText(generated);
        evaluate();
        new Toast(getOwner(), "Password generated").showAndFade();
    }

    private void loadEntry(Entry entry) {
        nameField.setText(entry.label());
        usernameField.setText(entry.username());
        passwordField.setText(entry.password());
        websiteField.setText(entry.url());
        notesArea.setText(entry.note());
        favoriteCheck.setSelected(entry.favorite());
        favoriteStar.setVisible(entry.favorite());
        folderCombo.setSelectedItem(entry.folder() == null ? "" : entry.folder());
        currentType = entry.type();
        refreshSegmentStyles();
        refreshVisibility();
        evaluate();
    }

    private void attemptSave() {
        char[] pw = passwordField.getPassword();
        try {
            final String label = nameField.getText().trim();
            final String username = usernameField.getText().trim();
            final String website = websiteField.getText().trim();
            final String folder = String.valueOf(folderCombo.getSelectedItem());
            final String notes = notesArea.getText();
            final EntryType type = currentType;
            final boolean favorite = favoriteCheck.isSelected();

            SwingWorker<Entry, Void> worker = new SwingWorker<>() {
                @Override
                protected Entry doInBackground() throws Exception {
                    if (editing == null) {
                        return service.addEntry(label, username, new String(pw.clone()),
                                website, notes, type, folder, favorite);
                    }
                    return service.updateEntry(editing.id(), label, username,
                            new String(pw.clone()), website, notes, type, folder,
                            favorite, editing.totpSecret());
                }

                @Override
                protected void done() {
                    try {
                        get();
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        if (cause instanceof DuplicateLabelException) {
                            show(nameField, nameError, cause.getMessage());
                            return;
                        }
                        if (cause instanceof VaultStorageException) {
                            passwordError.setText("Could not save the vault. Try again.");
                            passwordError.setVisible(true);
                            return;
                        }
                    }
                    dispose();
                }
            };
            worker.execute();
        } finally {
            java.util.Arrays.fill(pw, '\0');
        }
    }

    /* ==================================================================== */
    /*  Theme                                                               */
    /* ==================================================================== */

    private void refreshTheme() {
        refreshSegmentStyles();
        nameField.setForeground(Theme.text());
        usernameField.setForeground(Theme.text());
        websiteField.setForeground(Theme.text());
        notesArea.setForeground(Theme.text());
        strengthLabel.setForeground(strengthColor(strengthBar.strength()));
        favoriteStar.setForeground(Theme.star());
        favoriteCheck.setForeground(Theme.text());
        cancelButton.setBackground(Theme.surface());
        cancelButton.setForeground(Theme.text());
        saveButton.setBackground(Theme.accent());
        saveButton.setForeground(Theme.buttonText());
        repaint();
    }
}
