package com.shanmukha.vault.ui.components;

import com.shanmukha.vault.ui.theme.Theme;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Soft accent circle with a padlock glyph, drawn like the login screen badge.
 *
 * <p>The padlock is painted with Java2D instead of an emoji so both themes
 * render it in the accent color (fonts would colorize an emoji glyph).</p>
 */
public class LockBadge extends JComponent {

    private final int size;

    public LockBadge(int size) {
        this.size = size;
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int d = Math.min(getWidth(), getHeight());
        int x = (getWidth() - d) / 2;
        int y = (getHeight() - d) / 2;

        // Soft accent circle.
        g2.setColor(Theme.accentSoft());
        g2.fillOval(x, y, d, d);

        // Padlock body: rounded rect sitting on the circle center.
        int bodyW = Math.round(d * 0.34f);
        int bodyH = Math.round(d * 0.26f);
        int bodyX = x + (d - bodyW) / 2;
        int bodyY = y + Math.round(d * 0.46f);
        int radius = Math.max(3, bodyW / 5);

        g2.setColor(Theme.accent());
        g2.fillRoundRect(bodyX, bodyY, bodyW, bodyH, radius, radius);

        // Key hole (soft circle of the badge color again).
        g2.setColor(Theme.accentSoft());
        int dot = Math.max(2, bodyW / 5);
        g2.fillOval(bodyX + bodyW / 2 - dot / 2,
                bodyY + (bodyH - dot) / 2 - 1, dot, dot);

        // Shackle: an arc above the body.
        float stroke = Math.max(2f, d * 0.05f);
        g2.setStroke(new java.awt.BasicStroke(stroke, java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
        int shackleW = Math.round(bodyW * 0.62f);
        int shackleX = x + (d - shackleW) / 2;
        int shackleY = bodyY - Math.round(d * 0.16f);
        g2.drawArc(shackleX, shackleY, shackleW, bodyH, 0, 180);

        g2.dispose();
    }
}
