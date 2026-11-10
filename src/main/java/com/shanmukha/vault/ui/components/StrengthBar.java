package com.shanmukha.vault.ui.components;

import com.shanmukha.vault.crypto.StrengthChecker;
import com.shanmukha.vault.ui.theme.Theme;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Four-segment strength bar (Weak / Okay / Good / Strong).
 *
 * <p>Mockup style: rounded 4 px-tall segments; segments filled in the color
 * for the current strength, the rest in the muted border color. The textual
 * label ("Strong") is rendered by the caller next to the bar.</p>
 */
public class StrengthBar extends JPanel {

    private static final int BAR_H = 4;
    private static final int SEGMENT_W = 24;
    private static final int GAP = 4;

    private StrengthChecker.Strength strength = StrengthChecker.Strength.WEAK;

    public StrengthBar() {
        setPreferredSize(new Dimension(4 * SEGMENT_W + 3 * GAP, BAR_H));
        setMinimumSize(new Dimension(4 * SEGMENT_W + 3 * GAP, BAR_H));
    }

    public void setStrength(StrengthChecker.Strength strength, String reason) {
        this.strength = strength;
        repaint();
    }

    public StrengthChecker.Strength strength() {
        return strength;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int y = (getHeight() - BAR_H) / 2;
        Color active = colorFor(strength);
        int activeCount = segmentsActive(strength);

        for (int i = 0; i < 4; i++) {
            g2.setColor(i < activeCount ? active : Theme.border());
            g2.fillRoundRect(i * (SEGMENT_W + GAP), y, SEGMENT_W, BAR_H, 2, 2);
        }

        g2.dispose();
    }

    private static int segmentsActive(StrengthChecker.Strength strength) {
        return switch (strength) {
            case WEAK -> 1;
            case MEDIUM -> 2;
            case STRONG -> 4;
        };
    }

    private static Color colorFor(StrengthChecker.Strength strength) {
        return switch (strength) {
            case WEAK -> Theme.danger();
            case MEDIUM -> Theme.warning();
            case STRONG -> Theme.success();
        };
    }
}
