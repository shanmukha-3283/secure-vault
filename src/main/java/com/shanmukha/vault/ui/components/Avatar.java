package com.shanmukha.vault.ui.components;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Colored circle avatar showing the first letter of an entry's label.
 * Used in list rows (32) and the details panel header (44).
 */
public class Avatar extends JComponent {

    private static final int SIZE_32 = 32;

    private Color color;
    private String label;

    public Avatar(String label) {
        this.label = label == null || label.isBlank() ? "?" : label.substring(0, 1).toUpperCase();
        this.color = hashColor(label == null ? "" : label);
        setPreferredSize(new Dimension(SIZE_32, SIZE_32));
        setMinimumSize(new Dimension(SIZE_32, SIZE_32));
        setMaximumSize(new Dimension(SIZE_32, SIZE_32));
    }

    /** Overloaded to set a custom circle size (for the details header). */
    public Avatar(String label, int size) {
        this.label = label == null || label.isBlank() ? "?" : label.substring(0, 1).toUpperCase();
        this.color = hashColor(label == null ? "" : label);
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
    }

    /** Updates the displayed letter and repaints. */
    public void setLetter(String letter) {
        this.label = letter == null || letter.isBlank() ? "?" : letter.substring(0, 1).toUpperCase();
        repaint();
    }

    /** Updates the letter and the stable color derived from the full label. */
    public void setFrom(String fullLabel) {
        this.color = hashColor(fullLabel == null ? "" : fullLabel);
        setLetter(fullLabel == null || fullLabel.isBlank() ? "?" : fullLabel.substring(0, 1));
    }

    private static Color hashColor(String label) {
        int h = (label == null || label.isBlank()) ? 0 : label.hashCode();
        // Fixed palette of accent hues so every label gets a stable color.
        Color[] palette = {
                new Color(0x4F46E5), new Color(0xDC2626), new Color(0x16A34A),
                new Color(0xD97706), new Color(0x7C3AED), new Color(0x0891B2),
                new Color(0xBE185D), new Color(0x059669), new Color(0xC2410C)
        };
        return palette[Math.abs(h) % palette.length];
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int size = Math.min(w, h);
        int x = (w - size) / 2;
        int y = (h - size) / 2;

        g2.setColor(color);
        g2.fillOval(x, y, size, size);

        g2.setColor(Color.WHITE);
        float fontSize = Math.max(11f, size * 0.28f);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, fontSize));
        FontMetrics fm = g2.getFontMetrics();
        int textX = x + (size - fm.stringWidth(label)) / 2;
        int textY = y + (size + fm.getAscent() - fm.getDescent()) / 2 - 1;
        g2.drawString(label, textX, textY);

        g2.dispose();
    }
}
