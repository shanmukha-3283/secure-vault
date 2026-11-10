package com.shanmukha.vault.ui;

import com.shanmukha.vault.service.VaultService;
import com.shanmukha.vault.ui.theme.Styles;
import com.shanmukha.vault.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Left sidebar (UI_SPEC 2.3): All items, Favorites, Logins, Secure notes and
 * Cards with counts, a FOLDERS list, and Trash pinned to the bottom.
 *
 * <p>Rows are painted as rounded pills — selected = soft accent with accent
 * text, hover = row tint — with colors read live from {@link Theme}.
 * Clicks are reported through the callbacks so MainFrame decides what to
 * display. Width 220 px.</p>
 */
public class SidebarPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Selectable sidebar sections. */
    public enum Section { ALL, FAVORITES, LOGINS, NOTES, CARDS, TRASH }

    private final VaultService service;
    private final Consumer<Section> onSection;
    private final Consumer<String> onFolder;

    private final Map<Section, Row> sectionRows = new LinkedHashMap<>();
    private final JPanel folderRows = new JPanel(new GridBagLayout());

    public SidebarPanel(VaultService service, Consumer<Section> onSection,
                        Consumer<String> onFolder) {
        this.service = service;
        this.onSection = onSection;
        this.onFolder = onFolder;

        setBackground(Theme.sidebar());
        setLayout(new BorderLayout());

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_16, 0, Theme.SPACE_16, 0));

        // ---- Sections ----
        JPanel sections = new JPanel(new GridBagLayout());
        sections.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 2, 0);

        sectionRows.put(Section.ALL, new Row("\u2630", "All items", Section.ALL));
        sectionRows.put(Section.FAVORITES, new Row("\u2606", "Favorites", Section.FAVORITES));
        sectionRows.put(Section.LOGINS, new Row("\u26BF", "Logins", Section.LOGINS));
        sectionRows.put(Section.NOTES, new Row("\u25A4", "Secure notes", Section.NOTES));
        sectionRows.put(Section.CARDS, new Row("\u25AD", "Cards", Section.CARDS));

        for (Row row : sectionRows.values()) {
            sections.add(row, gbc);
            gbc.gridy++;
        }

        // ---- FOLDERS header (aligned with row text at 20 px) ----
        JLabel foldersHeader = new JLabel("FOLDERS");
        foldersHeader.setFont(foldersHeader.getFont().deriveFont(Font.BOLD, Theme.FONT_HINT));
        foldersHeader.setForeground(Theme.muted());
        foldersHeader.setBorder(BorderFactory.createEmptyBorder(
                Theme.SPACE_24, 20, Theme.SPACE_8, Theme.SPACE_16));
        sections.add(foldersHeader, gbc);
        gbc.gridy++;

        folderRows.setOpaque(false);
        folderRows.setBackground(null);
        sections.add(folderRows, gbc);

        content.add(sections, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);

        // ---- Trash pinned at the bottom with a hairline above ----
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_8, 0, Theme.SPACE_8, 0));
        bottom.add(Styles.hline(), BorderLayout.NORTH);

        JPanel trashHolder = new JPanel(new BorderLayout());
        trashHolder.setOpaque(false);
        trashHolder.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_8, 0, 0, 0));
        sectionRows.put(Section.TRASH, new Row("\uD83D\uDDD1", "Trash", Section.TRASH));
        trashHolder.add(sectionRows.get(Section.TRASH), BorderLayout.CENTER);

        bottom.add(trashHolder, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        setActiveSection(Section.ALL);
    }

    /* ------------------------------------------------------------------ */
    /*  Data updates                                                      */
    /* ------------------------------------------------------------------ */

    /** Updates all sidebar counts after each data change. */
    public void updateCounts(int all, int favorites, int logins, int notes,
                             int cards, int trash) {
        row(Section.ALL).setCount(String.valueOf(all));
        row(Section.FAVORITES).setCount(String.valueOf(favorites));
        row(Section.LOGINS).setCount(String.valueOf(logins));
        row(Section.NOTES).setCount(String.valueOf(notes));
        row(Section.CARDS).setCount(String.valueOf(cards));
        row(Section.TRASH).setCount(String.valueOf(trash));
        repaint();
    }

    /** Rebuilds the folder rows from the service (icon + name + count). */
    public void refreshFolders() {
        folderRows.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 2, 0);
        for (String folder : service.folders()) {
            Row row = new Row("\uD83D\uDCC1", folder, null);
            row.setCount(String.valueOf(service.entriesInFolder(folder).size()));
            folderRows.add(row, gbc);
            gbc.gridy++;
        }
        folderRows.revalidate();
        folderRows.repaint();
    }

    private Row row(Section section) {
        return sectionRows.get(section);
    }

    /** Highlights the given section without firing callbacks. */
    public void setActiveSection(Section section) {
        for (Map.Entry<Section, Row> entry : sectionRows.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == section);
        }
        repaint();
    }

    /** Called by MainFrame after a palette switch. */
    public void refreshTheme() {
        setBackground(Theme.sidebar());
        for (Row r : sectionRows.values()) {
            r.refreshTheme();
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Sidebar row                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * One painted pill row: icon, label, right-aligned count. Rows are
     * non-opaque and fully owner-painted so their colors follow the theme.
     */
    private final class Row extends JPanel {

        private final String glyph;
        private final String title;
        private final Section section;
        private String count = "";
        private boolean selected;
        private boolean hovered;

        Row(String glyph, String title, Section section) {
            this.glyph = glyph;
            this.title = title;
            this.section = section;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            setMinimumSize(new Dimension(0, 36));
            setPreferredSize(new Dimension(Theme.SIDEBAR_W - 12, 36));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (section != null) {
                        setActiveSection(section);
                        onSection.accept(section);
                    } else {
                        onFolder.accept(title);
                    }
                }
            });
        }

        void setSelected(boolean selected) {
            this.selected = selected;
            repaint();
        }

        void setCount(String value) {
            this.count = value == null ? "" : value;
            repaint();
        }

        void refreshTheme() {
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (selected) {
                g2.setColor(Theme.accentSoft());
                g2.fillRoundRect(8, 0, w - 16, h,
                        Theme.RADIUS_FIELD_BUTTON, Theme.RADIUS_FIELD_BUTTON);
            } else if (hovered) {
                g2.setColor(Theme.rowHover());
                g2.fillRoundRect(8, 0, w - 16, h,
                        Theme.RADIUS_FIELD_BUTTON, Theme.RADIUS_FIELD_BUTTON);
            }

            // Icon glyph.
            Font iconFont = getFont().deriveFont(Font.PLAIN, 14f);
            g2.setFont(iconFont);
            FontMetrics ifm = g2.getFontMetrics(iconFont);
            g2.setColor(selected ? Theme.accent() : Theme.muted());
            int iconX = 20;
            int textY = (h - ifm.getAscent()) / 2 + ifm.getAscent();
            g2.drawString(glyph, iconX, textY);

            // Label.
            Font textFont = getFont().deriveFont(
                    selected ? Font.BOLD : Font.PLAIN, Theme.FONT_BODY);
            g2.setFont(textFont);
            FontMetrics tfm = g2.getFontMetrics(textFont);
            g2.setColor(selected ? Theme.accent() : Theme.textSecondary());
            g2.drawString(title, iconX + Math.max(14, ifm.stringWidth(glyph)) + 8, textY);

            // Right-aligned count.
            if (!count.isEmpty()) {
                Font countFont = getFont().deriveFont(Font.PLAIN, Theme.FONT_HINT);
                g2.setFont(countFont);
                FontMetrics cfm = g2.getFontMetrics(countFont);
                g2.setColor(Theme.muted());
                g2.drawString(count, w - 20 - cfm.stringWidth(count), textY);
            }

            g2.dispose();
        }
    }
}
