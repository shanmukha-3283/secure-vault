package com.shanmukha.vault;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.shanmukha.vault.service.VaultService;
import com.shanmukha.vault.storage.VaultRepository;
import com.shanmukha.vault.ui.EntryDialog;
import com.shanmukha.vault.ui.LoginFrame;
import com.shanmukha.vault.ui.MainFrame;
import com.shanmukha.vault.ui.theme.Styles;
import com.shanmukha.vault.ui.theme.Theme;

import java.awt.Window;
import java.util.prefs.Preferences;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Application bootstrap and FlatLaf setup (UI_SPEC section 1 tokens).
 *
 * <p>Launches {@link LoginFrame}. On successful unlock or vault creation it
 * opens {@link MainFrame}; from there {@link EntryDialog} adds/edits entries.
 * The light/dark choice is remembered between runs; switching updates every
 * open window and re-applies each window's registered theme-refresh hook so
 * custom-painted components re-read the palette.</p>
 */
public final class App {

    private static final String PREF_DARK = "ui.theme.dark";

    private App() {
        // Utility class: never instantiated.
    }

    /**
     * Initializes FlatLaf with the theme tokens from UI_SPEC.md sections 1
     * and 4. Call once before creating any window.
     */
    public static void initLookAndFeel() {
        applyLookAndFeel();
        UIManager.put("Component.arc", Theme.RADIUS_FIELD_BUTTON);
        UIManager.put("Button.arc", Theme.RADIUS_FIELD_BUTTON);
        UIManager.put("TextComponent.arc", Theme.RADIUS_FIELD_BUTTON);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.focusStyle", "arc");
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("defaultFont", UIManager.getFont("defaultFont")
                .deriveFont(Theme.FONT_BODY));
    }

    private static void applyLookAndFeel() {
        if (Theme.dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
    }

    /**
     * Switches light/dark, retunes the UI defaults, refreshes every open
     * window (FlatLaf update + the per-window repaint hooks) and remembers
     * the choice. Must be called on the EDT.
     */
    public static void setDark(boolean value) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setDark(value));
            return;
        }
        Theme.dark = value;
        try {
            applyLookAndFeel();
            UIManager.put("Component.arc", Theme.RADIUS_FIELD_BUTTON);
            UIManager.put("Button.arc", Theme.RADIUS_FIELD_BUTTON);
            UIManager.put("TextComponent.arc", Theme.RADIUS_FIELD_BUTTON);
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("Component.focusStyle", "arc");
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.trackArc", 999);
        } catch (Exception e) {
            throw new RuntimeException("Could not switch look and feel.", e);
        }

        for (Window window : Window.getWindows()) {
            if (window.isDisplayable()) {
                SwingUtilities.updateComponentTreeUI(window);
                Styles.refreshWindow(window);
            }
        }
        rememberChoice();
    }

    private static void rememberChoice() {
        Preferences.userNodeForPackage(App.class).putBoolean(PREF_DARK, Theme.dark);
    }

    private static boolean loadRememberedChoice() {
        return Preferences.userNodeForPackage(App.class).getBoolean(PREF_DARK, false);
    }

    /**
     * Entry point: reads the remembered theme, shows the unlock / create
     * screen. On success {@link LoginFrame} hands over to
     * {@link #openMain(VaultService)}. The {@code vault.dat} never contains
     * plaintext; it is fine to construct the service eagerly.
     */
    public static void main(String[] args) {
        Theme.dark = loadRememberedChoice();
        SwingUtilities.invokeLater(() -> {
            initLookAndFeel();
            VaultService service = new VaultService(new VaultRepository());
            new LoginFrame(service).setVisible(true);
        });
    }

    /** Opens the unlock / create window for the given service (EDT helper). */
    public static void openLogin(VaultService service) {
        SwingUtilities.invokeLater(() -> {
            initLookAndFeel();
            new LoginFrame(service).setVisible(true);
        });
    }

    /** Opens the main window after a successful unlock or vault creation. */
    public static void openMain(VaultService service) {
        if (!service.isUnlocked()) {
            openLogin(service);
            return;
        }
        SwingUtilities.invokeLater(() -> new MainFrame(service).setVisible(true));
    }
}
