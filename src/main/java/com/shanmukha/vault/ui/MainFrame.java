package com.shanmukha.vault.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.shanmukha.vault.model.Entry;
import com.shanmukha.vault.model.EntryType;
import com.shanmukha.vault.service.VaultService;
import com.shanmukha.vault.ui.theme.Styles;
import com.shanmukha.vault.ui.theme.Theme;
import com.shanmukha.vault.App;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Main vault window (UI_SPEC 2.3): 60 px top bar, 220 px sidebar, flexible
 * item list, 420 px details panel and 28 px status bar.
 *
 * <p>All UI on the EDT; vault work goes through {@link VaultService} only.
 * The auto-lock countdown lives in the status bar and locks the vault after
 * five idle minutes, returning to the unlock screen.</p>
 */
public final class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Idle time after which the vault locks itself (spec behavior table). */
    private static final long AUTO_LOCK_MS = 5 * 60 * 1000L;

    private final VaultService service;

    // Top bar.
    private final JPanel topBar = new JPanel(new BorderLayout(Theme.SPACE_16, 0));
    private final JLabel vaultTitle = new JLabel("Vault");
    private final JTextField searchField = new JTextField();
    private final JButton newItemButton = Styles.primaryButton("+ New item");
    private final JButton lockButton = Styles.outlineButton("Lock");

    // Sidebar, list, details.
    private final SidebarPanel sidebar;
    private DetailsPanel detailsPanel;
    private final javax.swing.DefaultListModel<Entry> listModel = new javax.swing.DefaultListModel<>();
    private final JList<Entry> itemList = new JList<>(listModel);
    private final JLabel listHeader = new JLabel("All items");
    private final JLabel sortHint = new JLabel("Name A-Z");

    // Status bar.
    private final JPanel statusBar = new JPanel(new BorderLayout(Theme.SPACE_16, 0));
    private final JLabel statusLeft = new JLabel("0 items");
    private final JLabel autoLockLabel = new JLabel();
    private final JButton themeToggle = Styles.ghostButton("\u00D3", "Toggle light/dark theme");
    /** Bonus feature: opens the change-master-password dialog. */
    private final JButton changePasswordButton = Styles.ghostButton("Password",
            "Change master password (Ctrl+Shift+P)");

    // View state.
    private SidebarPanel.Section section = SidebarPanel.Section.ALL;
    private String folderFilter = "";

    // Auto-lock.
    private long lastActivity = System.currentTimeMillis();
    private java.awt.event.AWTEventListener activityListener;
    private Timer idleTicker;

    public MainFrame(VaultService service) {
        this.service = service;
        this.sidebar = new SidebarPanel(service, this::onSectionClicked, this::onFolderClicked);

        setTitle("Secure Vault");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(new Dimension(Theme.WINDOW_W, Theme.WINDOW_H));
        setMinimumSize(new Dimension(Theme.WINDOW_MIN_W, Theme.WINDOW_MIN_H));
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.window());

        // DetailsPanel first: buildLayout places it in the body.
        detailsPanel = new DetailsPanel(service, this::reload);
        buildLayout();

        wireBehaviors();
        reload();

        Styles.registerThemeRefresh(this, this::refreshTheme);
        refreshTheme();
    }

    /* ==================================================================== */
    /*  Layout                                                              */
    /* ==================================================================== */

    private void buildLayout() {
        JPanel topRow = new JPanel(new BorderLayout(Theme.SPACE_16, 0));
        topRow.setBorder(BorderFactory.createCompoundBorder(
                new Hairline(false),
                BorderFactory.createEmptyBorder(0, Theme.SPACE_24, 0, Theme.SPACE_24)));
        topRow.setPreferredSize(new Dimension(0, Theme.TOP_BAR_H));
        topBar.add(topRow, BorderLayout.CENTER);
        topBar.setBorder(BorderFactory.createEmptyBorder());
        topBar.setOpaque(false);

        // Left: lock + "Vault".
        JPanel leftGroup = new JPanel(new BorderLayout());
        leftGroup.setOpaque(false);
        com.shanmukha.vault.ui.components.LockBadge lockBadge =
                new com.shanmukha.vault.ui.components.LockBadge(30);
        leftGroup.add(lockBadge, BorderLayout.WEST);
        vaultTitle.setFont(vaultTitle.getFont().deriveFont(Font.BOLD, 20f));
        vaultTitle.setBorder(BorderFactory.createEmptyBorder(0, Theme.SPACE_8, 0, 0));
        leftGroup.add(vaultTitle, BorderLayout.CENTER);

        // Middle: search field.
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                "Search name, username or URL");
        JPanel searchHolder = new JPanel(new BorderLayout());
        searchHolder.setOpaque(false);
        searchHolder.setBorder(BorderFactory.createEmptyBorder(0, Theme.SPACE_32, 0, Theme.SPACE_16));
        searchHolder.add(searchField, BorderLayout.CENTER);
        searchHolder.setPreferredSize(new Dimension(440, 36));
        searchField.putClientProperty("JTextField.leftElidePosition", 0);

        // Right: buttons.
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_16, 0));
        buttons.setOpaque(false);
        buttons.add(newItemButton);
        buttons.add(lockButton);

        topRow.add(leftGroup, BorderLayout.WEST);
        topRow.add(searchHolder, BorderLayout.CENTER);
        topRow.add(buttons, BorderLayout.EAST);

        // List header.
        JPanel listHeaderRow = new JPanel(new BorderLayout());
        listHeaderRow.setOpaque(false);
        listHeaderRow.add(listHeader, BorderLayout.WEST);
        listHeaderRow.add(sortHint, BorderLayout.EAST);
        listHeaderRow.setBorder(BorderFactory.createEmptyBorder(
                Theme.SPACE_16, Theme.SPACE_24, Theme.SPACE_8, Theme.SPACE_24));

        // Item list.
        itemList.setCellRenderer(new ItemListCellRenderer());
        itemList.setFixedCellHeight(Theme.LIST_ROW_H);
        itemList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemList.setOpaque(false);
        itemList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && itemList.getSelectedValue() != null) {
                detailsPanel.setEntry(itemList.getSelectedValue());
            }
        });

        JScrollPane listScroll = new JScrollPane(itemList);
        listScroll.setBorder(BorderFactory.createEmptyBorder(0, Theme.SPACE_8, 0, Theme.SPACE_16));
        listScroll.getViewport().setOpaque(false);
        listScroll.setOpaque(false);
        listScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel listZone = new JPanel(new BorderLayout());
        listZone.setOpaque(false);
        listZone.add(listHeaderRow, BorderLayout.NORTH);
        listZone.add(listScroll, BorderLayout.CENTER);

        // Assemble body.
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(sidebar, BorderLayout.WEST);
        body.add(listZone, BorderLayout.CENTER);
        body.add(detailsPanel, BorderLayout.EAST);

        // Status bar.
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                new Hairline(true),
                BorderFactory.createEmptyBorder(0, Theme.SPACE_24, 0, Theme.SPACE_24)));
        statusBar.setPreferredSize(new Dimension(0, Theme.STATUS_BAR_H));
        JPanel statusRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_8, 0));
        statusRight.setOpaque(false);
        statusRight.add(changePasswordButton);
        statusRight.add(themeToggle);
        statusRight.add(autoLockLabel);
        statusBar.add(statusLeft, BorderLayout.WEST);
        statusBar.add(statusRight, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(topBar, BorderLayout.NORTH);
        content.add(body, BorderLayout.CENTER);
        content.add(statusBar, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);
    }

    /* ==================================================================== */
    /*  Behavior wiring                                                     */
    /* ==================================================================== */

    private void wireBehaviors() {
        searchField.getDocument().addDocumentListener(
                new javax.swing.event.DocumentListener() {
                    public void insertUpdate(javax.swing.event.DocumentEvent e) { reloadEntries(); }
                    public void removeUpdate(javax.swing.event.DocumentEvent e) { reloadEntries(); }
                    public void changedUpdate(javax.swing.event.DocumentEvent e) { reloadEntries(); }
                });

        themeToggle.addActionListener(e -> App.setDark(!Theme.dark));

        newItemButton.addActionListener(e -> openEntryDialog(null));
        lockButton.addActionListener(e -> lockAndReturn());
        changePasswordButton.addActionListener(e -> openPasswordDialog());

        // Keyboard shortcuts (UI_SPEC section 3): Ctrl+F, Ctrl+N, Ctrl+L.
        bindKey(getRootPane(), "vault.focusSearch",
                KeyStroke.getKeyStroke(KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        searchField.requestFocusInWindow();
                    }
                });
        bindKey(getRootPane(), "vault.newItem",
                KeyStroke.getKeyStroke(KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (!isEntryDialogOpen()) {
                            openEntryDialog(null);
                        }
                    }
                });
        bindKey(getRootPane(), "vault.lock",
                KeyStroke.getKeyStroke(KeyEvent.VK_L, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        lockAndReturn();
                    }
                });
        // Bonus feature entry point: Ctrl+Shift+P changes the master password.
        bindKey(getRootPane(), "vault.changePassword",
                KeyStroke.getKeyStroke(KeyEvent.VK_P,
                        java.awt.event.InputEvent.CTRL_DOWN_MASK
                                | java.awt.event.InputEvent.SHIFT_DOWN_MASK),
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        openPasswordDialog();
                    }
                });

        // Auto-lock: track activity, tick every second.
        activityListener = new java.awt.event.AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                lastActivity = System.currentTimeMillis();
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(activityListener,
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK
                        | AWTEvent.KEY_EVENT_MASK);

        idleTicker = new Timer("auto-lock-ticker", true);
        idleTicker.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(MainFrame.this::tickIdle);
            }
        }, 1000, 1000);
    }

    private static void bindKey(JRootPane root, String name, KeyStroke stroke, AbstractAction action) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
        root.getActionMap().put(name, action);
    }

    private boolean isEntryDialogOpen() {
        for (Window w : Window.getWindows()) {
            if (w instanceof EntryDialog && w.isShowing()) {
                return true;
            }
        }
        return false;
    }

    /* ==================================================================== */
    /*  Views                                                               */
    /* ==================================================================== */

    private void onSectionClicked(SidebarPanel.Section clicked) {
        this.section = clicked;
        this.folderFilter = "";
        listHeader.setText(sectionTitle(clicked));
        reloadEntries();
    }

    private void onFolderClicked(String folder) {
        this.folderFilter = folder;
        listHeader.setText(folder);
        reloadEntries();
    }

    private static String sectionTitle(SidebarPanel.Section section) {
        return switch (section) {
            case ALL -> "All items";
            case FAVORITES -> "Favorites";
            case LOGINS -> "Logins";
            case NOTES -> "Secure notes";
            case CARDS -> "Cards";
            case TRASH -> "Trash";
        };
    }

    /** Rebuilds the visible list from service state + current filters. */
    private void reloadEntries() {
        List<Entry> visible;
        String query = searchField.getText();

        if (!folderFilter.isBlank()) {
            visible = service.entriesInFolder(folderFilter);
            if (!query.isBlank()) {
                List<Entry> found = service.search(query);
                visible = visible.stream().filter(found::contains).toList();
            }
        } else if (!query.isBlank()) {
            visible = service.search(query);
        } else {
            visible = switch (section) {
                case ALL -> service.entries();
                case FAVORITES -> service.favoriteEntries();
                case LOGINS -> service.entriesByType(EntryType.LOGIN);
                case NOTES -> service.entriesByType(EntryType.SECURE_NOTE);
                case CARDS -> service.entriesByType(EntryType.CARD);
                case TRASH -> service.trashEntries();
            };
        }

        visible = visible.stream()
                .sorted(Comparator.comparing(Entry::label, String.CASE_INSENSITIVE_ORDER))
                .toList();

        listModel.clear();
        visible.forEach(listModel::addElement);
        statusLeft.setText(listModel.size() + " item" + (listModel.size() == 1 ? "" : "s"));

        if (listModel.size() > 0) {
            itemList.setSelectedIndex(0);
        } else {
            detailsPanel.setEntry(null);
        }
    }

    /** Full refresh: counts + folders + visible list + themed repaint. */
    public void reload() {
        reloadEntries();
        sidebar.updateCounts(service.entries().size(),
                service.favoriteCount(),
                service.countByType(EntryType.LOGIN),
                service.countByType(EntryType.SECURE_NOTE),
                service.countByType(EntryType.CARD),
                service.trashCount());
        sidebar.refreshFolders();
    }

    private void openEntryDialog(Entry editing) {
        EntryDialog dialog = new EntryDialog(this, service, editing);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                reload();
            }
        });
        dialog.setVisible(true);
    }

    /** Wipes the session and returns to the unlock screen. */
    private void lockAndReturn() {
        service.lock();
        dispose();
        App.openLogin(service);
    }

    /** Bonus feature: change the master password while unlocked. */
    private void openPasswordDialog() {
        if (isEntryDialogOpen()) {
            return;
        }
        new ChangePasswordDialog(this, service).setVisible(true);
    }

    /* ==================================================================== */
    /*  Auto-lock                                                           */
    /* ==================================================================== */

    private void tickIdle() {
        long remainingMs = AUTO_LOCK_MS - (System.currentTimeMillis() - lastActivity);
        if (remainingMs <= 0) {
            lockAndReturn();
            return;
        }
        long totalSeconds = (remainingMs + 999) / 1000;
        autoLockLabel.setText("Auto-lock in " + formatClock(totalSeconds));
    }

    private static String formatClock(long totalSeconds) {
        return "%d:%02d".formatted(totalSeconds / 60, totalSeconds % 60);
    }

    /* ==================================================================== */
    /*  Theme switching                                                     */
    /* ==================================================================== */

    private void refreshTheme() {
        getContentPane().setBackground(Theme.window());
        vaultTitle.setForeground(Theme.text());
        listHeader.setForeground(Theme.text());
        sortHint.setForeground(Theme.muted());
        statusLeft.setForeground(Theme.muted());
        autoLockLabel.setForeground(Theme.muted());
        themeToggle.setForeground(Theme.muted());
        changePasswordButton.setForeground(Theme.muted());
        newItemButton.setBackground(Theme.accent());
        newItemButton.setForeground(Theme.buttonText());
        lockButton.setForeground(Theme.text());
        sidebar.refreshTheme();
        if (detailsPanel != null) {
            detailsPanel.refreshTheme();
        }
        repaint();
    }

    /* ==================================================================== */
    /*  Hairline border that follows the palette                            */
    /* ==================================================================== */

    /** 1 px separator border painted with Theme.border(). */
    private static final class Hairline extends javax.swing.border.AbstractBorder {

        private final boolean top;

        Hairline(boolean top) {
            this.top = top;
        }

        @Override
        public void paintBorder(java.awt.Component c, Graphics g, int x, int y, int w, int h) {
            g.setColor(Theme.border());
            if (top) {
                g.fillRect(0, 0, w, 1);
            } else {
                g.fillRect(0, h - 1, w, 1);
            }
        }

        @Override
        public Insets getBorderInsets(java.awt.Component c) {
            return new Insets(top ? 1 : 0, 0, top ? 0 : 1, 0);
        }
    }
}
