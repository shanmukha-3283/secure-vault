package com.shanmukha.vault.ui.theme;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.AbstractBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

/**
 * Shared screen-building helpers: buttons with the spec's visual hierarchy
 * (primary = filled accent, secondary = outline, danger = red outline, ghost =
 * icon-only hover pill), text labels, hairline separators and rounded cards.
 *
 * <p>Colors are read through {@link Theme} accessors at paint/event time, so
 * components created here automatically follow a light/dark switch. Rounded
 * outlines are painted by {@link HoverBorder} which re-reads the palette on
 * every paint.</p>
 */
public final class Styles {

    private Styles() {
        // Utility class: never instantiated.
    }

    /** Client property key holding the per-window theme refresh hook. */
    public static final String WINDOW_REFRESH_PROP = "theme.refresh";

    /** Client property tracking hover state for {@link HoverBorder} buttons. */
    static final String HOVER_PROP = "styles.hover";

    /* ================================================================== */
    /*  Buttons                                                           */
    /* ================================================================== */

    /**
     * Filled primary button ("+ New item", "Edit", "Save", "Unlock").
     * The 8 px arc comes from FlatLaf's {@code Button.arc}.
     */
    public static JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.setFont(b.getFont().deriveFont(Font.BOLD, Theme.FONT_LABEL));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new HoverStyle() {
            @Override
            void apply(AbstractButton btn, boolean hover) {
                btn.setBackground(hover ? Theme.accentHover() : Theme.accent());
            }
        });
        applyPrimary(b);
        return b;
    }

    private static void applyPrimary(JButton b) {
        b.setBackground(Theme.accent());
        b.setForeground(Theme.buttonText());
        b.setContentAreaFilled(true);
        b.setOpaque(false);
    }

    /** Outline ("secondary") button: 1 px border on the surface color. */
    public static JButton outlineButton(String text) {
        return outlineButton(text, Theme::border, Theme::borderHover, Theme.text());
    }

    /** Danger outline button: red border and text. */
    public static JButton dangerButton(String text) {
        return outlineButton(text, Theme::danger, Theme::danger, Theme.danger());
    }

    /** Outline button core with overridable line and hover colors. */
    private static JButton outlineButton(String text, Supplier<Color> lineColor,
                                         Supplier<Color> hoverLineColor,
                                         Color foreground) {
        JButton button = new JButton(text);
        button.setFont(button.getFont().deriveFont(Font.PLAIN, Theme.FONT_LABEL));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setForeground(foreground);
        button.setBorder(new HoverBorder(lineColor, hoverLineColor, Theme::rowHover));
        attachHover(button);
        return button;
    }

    /**
     * Ghost icon button (no border, subtle hover pill): theme toggle,
     * reveal, copy icons. FlatLaf paints the rounded hover for the
     * {@code toolBarButton} type automatically.
     */
    public static JButton ghostButton(String text, String tooltip) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 13f));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setForeground(Theme.muted());
        return b;
    }

    /** Marks a button hoverable; borders/some styles read the hover flag. */
    private static void attachHover(JComponent c) {
        c.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                c.putClientProperty(HOVER_PROP, Boolean.TRUE);
                c.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                c.putClientProperty(HOVER_PROP, Boolean.FALSE);
                c.repaint();
            }
        });
    }

    /* ================================================================== */
    /*  Labels                                                            */
    /* ================================================================== */

    public static JLabel label(String text, float size, boolean bold, boolean muted) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(muted ? Theme.muted() : Theme.text());
        return l;
    }

    /** 12 pt bold muted label shown above a field. */
    public static JLabel fieldLabel(String text) {
        return label(text, Theme.FONT_LABEL, true, true);
    }

    /* ================================================================== */
    /*  Separators / cards                                                */
    /* ================================================================== */

    /** 1 px full-width hairline whose color follows the active theme. */
    public static JPanel hline() {
        JPanel line = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(1, 1);
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(1, 1);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, 1);
            }

            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.border());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        line.setOpaque(false);
        return line;
    }

    /** Rounded 16 px card surface (login/create cards). */
    public static JPanel card() {
        JPanel card = new JPanel() {
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
        return card;
    }

    /* ================================================================== */
    /*  Theme switching plumbing                                          */
    /* ================================================================== */

    /**
     * Registers {@code refresh} on a frame's root pane.
     * {@link com.shanmukha.vault.App#setDark} invokes every registered hook
     * after the look-and-feel update so per-component colors re-read the new
     * palette.
     */
    public static void registerThemeRefresh(JFrame frame, Runnable refresh) {
        frame.getRootPane().putClientProperty(WINDOW_REFRESH_PROP, refresh);
    }

    public static void registerThemeRefresh(JDialog dialog, Runnable refresh) {
        dialog.getRootPane().putClientProperty(WINDOW_REFRESH_PROP, refresh);
    }

    /** Invokes the refresh hook registered on the given window, if any. */
    public static void refreshWindow(Window window) {
        if (window instanceof JFrame frame
                && frame.getRootPane().getClientProperty(WINDOW_REFRESH_PROP)
                        instanceof Runnable r) {
            r.run();
        } else if (window instanceof JDialog dialog
                && dialog.getRootPane().getClientProperty(WINDOW_REFRESH_PROP)
                        instanceof Runnable r) {
            r.run();
        }
    }

    /* ================================================================== */
    /*  Internal helpers                                                  */
    /* ================================================================== */

    /** Hover adapter for filled buttons which restyles via apply(). */
    private abstract static class HoverStyle extends MouseAdapter {

        @Override
        public void mouseEntered(MouseEvent e) {
            if (e.getSource() instanceof AbstractButton btn) {
                apply(btn, true);
            }
        }

        @Override
        public void mouseExited(MouseEvent e) {
            if (e.getSource() instanceof AbstractButton btn) {
                apply(btn, false);
            }
        }

        abstract void apply(AbstractButton btn, boolean hover);
    }

    /**
     * Rounded 1 px border. Line and fill suppliers are consulted at paint
     * time so the border follows palette changes; fills on hover. Also used
     * for accent-tinted buttons such as the dialog's "Generate".
     */
    public static final class HoverBorder extends AbstractBorder {

        private final Supplier<Color> lineColor;
        private final Supplier<Color> hoverLineColor;
        private final Supplier<Color> fillColor;

        public HoverBorder(Supplier<Color> lineColor, Supplier<Color> hoverLineColor,
                           Supplier<Color> fillColor) {
            this.lineColor = lineColor;
            this.hoverLineColor = hoverLineColor;
            this.fillColor = fillColor;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            boolean hover = c instanceof JComponent jc
                    && Boolean.TRUE.equals(jc.getClientProperty(HOVER_PROP))
                    && c.isEnabled();

            if (hover) {
                g2.setColor(fillColor.get());
                g2.fillRoundRect(x, y, w - 1, h - 1,
                        Theme.RADIUS_FIELD_BUTTON, Theme.RADIUS_FIELD_BUTTON);
            }
            g2.setColor(hover ? hoverLineColor.get() : lineColor.get());
            g2.drawRoundRect(x, y, w - 1, h - 1,
                    Theme.RADIUS_FIELD_BUTTON, Theme.RADIUS_FIELD_BUTTON);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(10, 20, 10, 20);
        }
    }
}
