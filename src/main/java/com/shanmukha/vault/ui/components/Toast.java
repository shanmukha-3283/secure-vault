package com.shanmukha.vault.ui.components;

import com.shanmukha.vault.ui.theme.Theme;

import javax.swing.JWindow;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Window;

/**
 * Short-lived toast ("Copied") shown near the bottom center of the owner window.
 * Auto-disposes after 2 seconds.
 */
public class Toast extends JWindow {

    private static final int DISPLAY_MS = 2000;

    public Toast(Window owner, String message) {
        super(owner);
        setAlwaysOnTop(true);

        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, Theme.FONT_HINT));
        label.setForeground(Color.WHITE);
        label.setHorizontalAlignment(SwingConstants.CENTER);

        setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
        add(label);

        // Rounded card background (alpha channel gives the dim effect).
        setBackground(new Color(0, 0, 0, 180));

        pack();

        Dimension d = getSize();
        setSize(d.width + 24, d.height + 12);
        label.setPreferredSize(getSize());

        // Position bottom-center of the owner window.
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = env.getDefaultScreenDevice();
        int screenW = gd.getDisplayMode().getWidth();
        int screenH = gd.getDisplayMode().getHeight();

        int x = owner.getLocationOnScreen().x + (owner.getWidth() - getWidth()) / 2;
        int y = owner.getLocationOnScreen().y + owner.getHeight() - getHeight() - 40;

        x = Math.max(0, Math.min(x, screenW - getWidth()));
        y = Math.max(0, Math.min(y, screenH - getHeight()));

        setLocation(x, y);
    }

    public void showAndFade() {
        setVisible(true);
        new Thread(() -> {
            try {
                Thread.sleep(DISPLAY_MS);
            } catch (InterruptedException ignored) {
                // fall through
            }
            java.awt.EventQueue.invokeLater(this::dispose);
        }, "toast-fader").start();
    }
}
