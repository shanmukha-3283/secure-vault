package com.shanmukha.vault.ui;

import com.shanmukha.vault.model.Entry;
import com.shanmukha.vault.ui.components.Avatar;
import com.shanmukha.vault.ui.theme.Theme;

import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Item list row renderer (UI_SPEC 2.3): 62 px rows with a colored circle
 * avatar, bold name, muted username below, favorite star and quick-copy
 * glyphs on the right.
 *
 * <p>Selected rows get the soft accent background plus a 4 px accent bar on
 * the left edge, both painted with the current palette. All colors are read
 * through {@link Theme} at paint time, so rows follow theme switches.</p>
 *
 * <p>The glyphs are decorative: copy actions live in the details panel.</p>
 */
public final class ItemListCellRenderer implements ListCellRenderer<Entry> {

    private static final int MARGIN = 8;       // gap around the selection pill
    private static final int PAD_LEFT = 12;    // space between pill edge and avatar

    private final Avatar avatar = new Avatar("?");
    private final javax.swing.JLabel nameLabel = new javax.swing.JLabel();
    private final javax.swing.JLabel usernameLabel = new javax.swing.JLabel();
    private final javax.swing.JLabel starLabel = new javax.swing.JLabel("\u2605");
    private final JButton copyUser = ghostGlyph("\u29C9");
    private final JButton copyPass = ghostGlyph("\u26BF");

    /* ------------------------------------------------------------------ */

    /**
     * A transparent row holding the avatar and the two text lines; the
     * selection pill is painted behind its content.
     */
    private static final class RowPanel extends javax.swing.JPanel {
        boolean selected;
        boolean hover;

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            if (selected || hover) {
                Color pill = selected ? Theme.accentSoft() : Theme.rowHover();
                g2.setColor(pill);
                g2.fillRoundRect(MARGIN, MARGIN, getWidth() - 2 * MARGIN - 8,
                        getHeight() - 2 * MARGIN,
                        Theme.RADIUS_LIST_ROW, Theme.RADIUS_LIST_ROW);

                if (selected) {
                    // 4 px accent bar on the left edge of the pill.
                    g2.setColor(Theme.accent());
                    g2.fillRoundRect(MARGIN, MARGIN, 4, getHeight() - 2 * MARGIN, 2, 2);
                }
            }
            g2.dispose();
        }
    }

    /* ------------------------------------------------------------------ */

    @Override
    public Component getListCellRendererComponent(JList<? extends Entry> list,
                                                  Entry entry, int index,
                                                  boolean selected, boolean focused) {
        RowPanel row = new RowPanel();
        row.selected = selected;
        row.setOpaque(false);

        BorderLayout layout = new BorderLayout(Theme.SPACE_4, 0);
        row.setLayout(layout);
        row.setBorder(javax.swing.BorderFactory.createEmptyBorder(MARGIN, 0,
                MARGIN, MARGIN));

        // Icon column: avatar with 12 px breathing room after the accent bar.
        avatar.setFrom(entry.label());
        javax.swing.JPanel avatarHolder = new javax.swing.JPanel(new java.awt.GridBagLayout());
        avatarHolder.setOpaque(false);
        avatarHolder.setPreferredSize(new Dimension(48, Theme.LIST_ROW_H - 2 * MARGIN));
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        avatarHolder.add(avatar, gbc);
        row.add(avatarHolder, java.awt.BorderLayout.WEST);

        // Text lines.
        javax.swing.JPanel names = new javax.swing.JPanel(new java.awt.GridLayout(2, 1));
        names.setOpaque(false);
        nameLabel.setText(entry.label());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, Theme.FONT_BODY));
        nameLabel.setForeground(selected ? Theme.accent() : Theme.text());
        usernameLabel.setText(entry.username());
        usernameLabel.setFont(usernameLabel.getFont().deriveFont(Font.PLAIN, Theme.FONT_HINT));
        usernameLabel.setForeground(Theme.muted());
        names.add(nameLabel);
        names.add(usernameLabel);
        row.add(names, java.awt.BorderLayout.CENTER);

        // Right side: star (favorite), quick-copy glyphs.
        javax.swing.JPanel right = new javax.swing.JPanel(new FlowLayout(FlowLayout.RIGHT,
                Theme.SPACE_4, 0));
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(96, Theme.LIST_ROW_H - 2 * MARGIN));
        starLabel.setFont(starLabel.getFont().deriveFont(15f));
        starLabel.setForeground(Theme.star());
        starLabel.setVisible(entry.favorite());
        copyUser.setForeground(Theme.muted());
        copyPass.setForeground(Theme.muted());
        right.add(starLabel);
        right.add(copyUser);
        right.add(copyPass);
        row.add(right, java.awt.BorderLayout.EAST);

        return row;
    }

    private static JButton ghostGlyph(String glyph) {
        JButton b = new JButton(glyph);
        b.setFont(b.getFont().deriveFont(12f));
        b.setBorder(null);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusable(false);
        b.setOpaque(false);
        return b;
    }
}
