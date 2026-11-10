package com.shanmukha.vault.ui;

import com.shanmukha.vault.App;
import com.shanmukha.vault.model.Entry;
import com.shanmukha.vault.model.EntryType;
import com.shanmukha.vault.service.VaultService;
import com.shanmukha.vault.storage.VaultRepository;
import com.shanmukha.vault.ui.theme.Styles;
import com.shanmukha.vault.ui.theme.Theme;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Insets;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Test-scope screenshot utility — <strong>not</strong> a JUnit test
 * (surefire only picks up classes ending in {@code Test}).
 *
 * <p>Captures the five README screenshots from the real UI classes:</p>
 * <ul>
 *   <li>{@code login.png} — unlock card</li>
 *   <li>{@code create-vault.png} — create card</li>
 *   <li>{@code main-light.png} — main window, light</li>
 *   <li>{@code main-dark.png} — main window, dark</li>
 *   <li>{@code add-item-dialog.png} — add item modal</li>
 * </ul>
 *
 * <p>Safety: a throwaway demo vault lives under the system temp directory
 * (e.g. {@code /tmp/secure-vault-demo-shots}) and contains only fake data.
 * The developer's real {@code vault.dat} at the project root is never opened here, because every repository is created
 * with an explicit path (verified against the project root in
 * {@link #cleanDemoDir()}).</p>
 *
 * <p>Run on demand (needs a display, e.g. {@code DISPLAY=:0}); the classpath
 * is easiest via the dependency plugin which writes it to a file:</p>
 * <pre>
 * export PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH
 * mvn -q test-compile
 * mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 * java -cp "target/test-classes:target/classes:$(cat /tmp/cp.txt)" \
 *      com.shanmukha.vault.ui.UiCapture
 * </pre>
 *
 * <p>Net effect: {@code docs/screenshots/*.png} are regenerated straight
 * from the app's own Swing paint code path (client area only, no window
 * chrome).</p>
 */
public final class UiCapture {

    private static final Path DEMO_DIR =
            Path.of(System.getProperty("java.io.tmpdir"), "secure-vault-demo-shots");
    /** Vault exists: LoginFrame shows the unlock card. */
    private static final Path EXISTING = DEMO_DIR.resolve("unlock/vault.dat");
    /** Vault does not exist: LoginFrame shows the create card. */
    private static final Path FRESH = DEMO_DIR.resolve("create/vault.dat");
    /** Seeded vault for the main-window captures. */
    private static final Path MAIN = DEMO_DIR.resolve("main/vault.dat");

    /** Fake demo password for throwaway data only; not a real secret. */
    private static final char[] DEMO_PASSWORD =
            "demo vault password 2026!".toCharArray();

    private static final Path OUT_DIR = Path.of("docs", "screenshots");

    /** Time for the EDT to finish layout and paint before a capture. */
    private static final long SETTLE_MS = 900;

    private UiCapture() {
        // Utility class: never instantiated.
    }

    public static void main(String[] args) throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.err.println("No display available; screenshots need one.");
            System.exit(0);
        }
        Theme.dark = false;
        App.initLookAndFeel();


        cleanDemoDir();


        // --- Unlock card: a vault exists, so LoginFrame shows unlock. ---
        VaultService lockedService = new VaultService(new VaultRepository(EXISTING));
        lockedService.createVault(DEMO_PASSWORD);
        seedDemoData(lockedService);
        lockedService.lock();

        capture("login.png", () -> {
            LoginFrame frame = new LoginFrame(lockedService);
            frame.setLocation(80, 60);
            return frame;
        }, 720, 660);


        // --- Create card: no vault exists at the fresh path. ---
        VaultService emptyService = new VaultService(new VaultRepository(FRESH));
        capture("create-vault.png", () -> {
            LoginFrame frame = new LoginFrame(emptyService);
            frame.setLocation(80, 60);
            return frame;
        }, 720, 660);

        // --- Main window light + the add-item dialog + main window dark. ---
        VaultService unlockedService = new VaultService(new VaultRepository(MAIN));
        SwingUtilities.invokeAndWait(() -> {
            try {
                unlockedService.createVault(DEMO_PASSWORD);
                seedDemoData(unlockedService);
            } catch (Exception e) {
                throw new IllegalStateException("Demo data could not be seeded.", e);
            }
        });

        MainFrame main = newOnEdt(() -> {
            MainFrame frame = new MainFrame(unlockedService);
            frame.setLocation(40, 40);
            return frame;
        });

        try {
            showOnEdt(main);
            sleep(SETTLE_MS);
            fitClientAreaFrame(main, Theme.WINDOW_W, Theme.WINDOW_H);
            sleep(300);
            writeCapture(main, "main-light.png", Theme.WINDOW_W, Theme.WINDOW_H);

            // Modal dialog: shown through a fire-and-forget EDT task because
            // setVisible on a modal dialog would block the EDT run that
            // spawned it. The prefilled variant avoids the fresh-form
            // required-field outlines.
            java.util.concurrent.atomic.AtomicReference<EntryDialog> ref =
                    new java.util.concurrent.atomic.AtomicReference<>();
            SwingUtilities.invokeLater(() -> {
                Entry github = unlockedService.entries().stream()
                        .filter(e -> e.label().equals("GitHub"))
                        .findFirst()
                        .orElseThrow();
                EntryDialog d = new EntryDialog(main, unlockedService, github);
                ref.set(d);
                d.setVisible(true);
            });
            sleep(SETTLE_MS);
            writeCaptureDialog(ref.get(), "add-item-dialog.png");
            SwingUtilities.invokeLater(() -> ref.get().dispose());
            sleep(300);

            // Main window dark uses the same runtime-switch logic as
            // App.setDark, minus the Preferences write (the choice on disk
            // belongs to the real app, not this throwaway capture run).
            setupDarkLaf();
            for (Window window : java.awt.Window.getWindows()) {
                if (window.isDisplayable() && window.isShowing()) {
                    SwingUtilities.updateComponentTreeUI(window);
                    Styles.refreshWindow(window);
                }
            }
            sleep(SETTLE_MS);
            fitClientAreaFrame(main, Theme.WINDOW_W, Theme.WINDOW_H);
            sleep(300);
            writeCapture(main, "main-dark.png", Theme.WINDOW_W, Theme.WINDOW_H);
        } finally {
            hideOnEdt(main);
            SwingUtilities.invokeAndWait(unlockedService::lock);
        }

        System.out.println("Screenshots written to " + OUT_DIR.toAbsolutePath().normalize());
        System.exit(0);
    }

    /* ==================================================================== */
    /*  Fake demo data                                                      */
    /* ==================================================================== */

    /** Fake entries with the mockup sample names; nothing here is real. */
    private static void seedDemoData(VaultService service) throws Exception {
        Entry github = service.addEntry("GitHub", "shanmukha",
                "fake-strong-password-1", "https://github.com",
                "Personal account. 2FA enabled.", EntryType.LOGIN, "Work", true);
        service.updateEntry(github.id(), github.label(), github.username(),
                github.password(), github.url(), github.note(), github.type(),
                github.folder(), github.favorite(), "JBSWY3DPEHPK3PXP");

        service.addEntry("Gmail", "shan.demo@mail.com", "fake-strong-password-2",
                "https://mail.google.com", null, EntryType.LOGIN, "Personal", true);
        service.addEntry("Netflix", "family-demo", "fake-strong-password-3",
                "https://netflix.com", null, EntryType.LOGIN, "Personal", false);
        service.addEntry("Wi-Fi Home", "guests", "fake-wifi-phrase",
                null, "WPA3 passphrase is on the fridge.",
                EntryType.SECURE_NOTE, "", false);
        service.addEntry("LinkedIn", "shan.demo", "fake-strong-password-4",
                "https://linkedin.com", null, EntryType.LOGIN, "Work", true);
        service.addEntry("AWS Console", "dev-user", "fake-strong-password-5",
                "https://console.aws.amazon.com", null, EntryType.LOGIN, "Work", false);
        service.addEntry("Spotify", "shan.music", "fake-strong-password-6",
                "https://open.spotify.com", null, EntryType.LOGIN, "Personal", false);
        service.addEntry("Dropbox", "shan.demo", "fake-strong-password-7",
                "https://dropbox.com", null, EntryType.LOGIN, "Work", false);
        service.addEntry("Test card", "Visa \u2022\u2022\u2022\u2022 1111",
                "fake-pin-1234", null, null, EntryType.CARD, "", false);

        // Two soft-deleted entries for the Trash count.
        Entry old = service.addEntry("Old team server", "ops", "fake-old-password",
                null, null, EntryType.LOGIN, "", false);
        service.deleteEntry(old.id());
        Entry legacy = service.addEntry("Legacy VPN", "ops", "fake-legacy-password",
                null, null, EntryType.SECURE_NOTE, "", false);
        service.deleteEntry(legacy.id());
    }

    /* ==================================================================== */
    /*  Capture helpers                                                     */
    /* ==================================================================== */

    /** Shows a frame, fits its client area and captures it to PNG. */
    private static void capture(String fileName, java.util.function.Supplier<JFrame> frame,
                                int clientW, int clientH) throws Exception {
        JFrame f = newOnEdt(frame);
        try {
            showOnEdt(f);
            sleep(SETTLE_MS);
            fitClientAreaFrame(f, clientW, clientH);
            sleep(300);
            writeCapture(f, fileName, clientW, clientH);
        } finally {
            hideOnEdt(f);
        }
    }

    /** Constructs a window on the EDT and returns it. */
    private static <T extends JFrame> T newOnEdt(java.util.function.Supplier<T> supplier)
            throws Exception {
        java.util.concurrent.atomic.AtomicReference<T> holder =
                new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> holder.set(supplier.get()));
        return holder.get();
    }

    private static void showOnEdt(JFrame frame) throws Exception {
        SwingUtilities.invokeAndWait(() -> frame.setVisible(true));
    }

    private static void hideOnEdt(JFrame frame) throws Exception {
        if (!frame.isDisplayable()) {
            return;
        }
        SwingUtilities.invokeAndWait(frame::dispose);
    }

    /**
     * Resizes so the *client area* (content pane) is exactly the request
     * size; window decorations are then excluded from the capture, exactly
     * like the mockup canvases.
     */
    private static void fitClientAreaFrame(JFrame frame, int clientW, int clientH) {
        Insets insets = frame.getInsets();
        frame.setSize(clientW + insets.left + insets.right,
                clientH + insets.top + insets.bottom);
        frame.revalidate();
    }

    /** Paints the current content pane into a PNG on the EDT. */
    private static void writeCapture(JFrame frame, String fileName,
                                     int clientW, int clientH) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                paintContentPane(frame.getContentPane(), OUT_DIR.resolve(fileName),
                        clientW, clientH);
            } catch (IOException e) {
                throw new IllegalStateException("Capture failed for " + fileName, e);
            }
        });
    }

    /** Same as the frame version but pins the dialog (undecorated) size. */
    private static void writeCaptureDialog(javax.swing.JDialog dialog,
                                           String fileName)
            throws Exception {
        if (dialog == null) {
            throw new IllegalStateException("Dialog never became visible.");
        }
        SwingUtilities.invokeAndWait(() -> {
            try {
                dialog.revalidate();
                paintContentPane(dialog.getContentPane(),
                        OUT_DIR.resolve(fileName),
                        dialog.getContentPane().getWidth(),
                        dialog.getContentPane().getHeight());
            } catch (IOException e) {
                throw new IllegalStateException("Capture failed for " + fileName, e);
            }
        });
    }

    private static void paintContentPane(java.awt.Container pane, Path out,
                                         int w, int h) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        try {
            pane.paint(g);
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "png", out.toFile());
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Mirrors {@code App.setDark(true)} without the Preferences write: the
     * persisted theme belongs to the real app, not to a throwaway capture.
     */
    private static void setupDarkLaf() {
        Theme.dark = true;
        com.formdev.flatlaf.FlatDarkLaf.setup();
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

    /* ==================================================================== */
    /*  Demo directory                                                      */
    /* ==================================================================== */

    /** Recreates the throwaway demo directories and asserts the safety net. */
    private static void cleanDemoDir() throws IOException {
        if (Files.exists(DEMO_DIR)) {
            try (var paths = Files.walk(DEMO_DIR)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                                // Best effort: stale files get overwritten below.
                            }
                        });
            }
        }
        Files.createDirectories(DEMO_DIR.resolve("unlock"));
        Files.createDirectories(DEMO_DIR.resolve("create"));
        Files.createDirectories(DEMO_DIR.resolve("main"));
        Files.createDirectories(OUT_DIR);

        // Safety net: the harness must never touch the developer's vault.
        Path realVault = Path.of("vault.dat").toAbsolutePath().normalize();
        for (Path demoPath : new Path[]{EXISTING, FRESH, MAIN}) {
            if (demoPath.toAbsolutePath().normalize().equals(realVault)) {
                throw new IllegalStateException(
                        "Refusing to run: demo path overlaps the real vault.dat.");
            }
        }
    }
}
