package com.shanmukha.vault.ui.components;

import com.shanmukha.vault.ui.theme.Theme;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Countdown ring for the verification code: a circular arc showing the time
 * left in the current 30-second TOTP window, with the remaining seconds in
 * the middle (UI_SPEC 2.3).
 */
public class TotpRing extends JComponent {

    private static final int SIZE = 30;

    /** Seconds remaining (0..period). */
    private int remaining = 30;
    private int period = 30;

    public TotpRing() {
        setPreferredSize(new Dimension(SIZE, SIZE));
        setMinimumSize(new Dimension(SIZE, SIZE));
        setMaximumSize(new Dimension(SIZE, SIZE));
    }

    /** Updates the countdown; the arc shrinks as {@code remaining} reaches 0. */
    public void setRemaining(int remaining, int period) {
        this.remaining = Math.max(0, Math.min(period, remaining));
        this.period = Math.max(1, period);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int d = Math.min(getWidth(), getHeight()) - 2;
        int x = (getWidth() - d) / 2;
        int y = (getHeight() - d) / 2;

        float stroke = 2.5f;

        // Track (full circle).
        g2.setColor(Theme.border());
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
        g2.drawOval(x + 1, y + 1, d - 2, d - 2);

        // Remaining arc, starting at 12 o'clock, clockwise.
        double fraction = period > 0 ? (double) remaining / period : 0;
        int sweep = (int) Math.round(360 * fraction);
        g2.setColor(Theme.accent());
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc(x + 1, y + 1, d - 2, d - 2, 90, -sweep);

        // Seconds remaining in the middle.
        g2.setColor(Theme.textSecondary());
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 9f));
        FontMetrics fm = g2.getFontMetrics();
        String text = String.valueOf(remaining);
        int tx = x + (d - 2 - fm.stringWidth(text)) / 2 + 1;
        int ty = y + (d + fm.getAscent() - fm.getDescent()) / 2 + 1;
        g2.drawString(text, tx, ty);

        g2.dispose();
    }
}
