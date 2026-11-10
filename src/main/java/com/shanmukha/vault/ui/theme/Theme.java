package com.shanmukha.vault.ui.theme;

import java.awt.Color;

/**
 * Design tokens from {@code docs/ui/UI_SPEC.md} section 1.
 *
 * <p>Light and dark palettes exactly as specified. Layout spacing uses
 * 8/16/24/32 px (plus 4 px micro-gaps inside rows). Corner radii: 8 for
 * fields and buttons, 10 for list rows, 16 for cards and dialogs.</p>
 */
public final class Theme {

    private Theme() {
        // Utility class: never instantiated.
    }

    /* ------------------------------------------------------------------ */
    /*  Layout metrics                                                     */
    /* ------------------------------------------------------------------ */

    /** Main frame size and minimum (UI_SPEC 2.3). */
    public static final int WINDOW_W = 1200;
    public static final int WINDOW_H = 760;
    public static final int WINDOW_MIN_W = 900;
    public static final int WINDOW_MIN_H = 600;

    /** Zone sizes. */
    public static final int TOP_BAR_H = 60;
    public static final int STATUS_BAR_H = 28;
    public static final int SIDEBAR_W = 220;
    public static final int DETAILS_W = 420;
    public static final int LIST_ROW_H = 62;
    public static final int LIST_HEADER_H = 48;

    /** Spacing scale: 8, 16, 24, 32 only (spec). */
    public static final int SPACE_8 = 8;
    public static final int SPACE_16 = 16;
    public static final int SPACE_24 = 24;
    public static final int SPACE_32 = 32;

    /** Micro-gaps inside composite rows (avatar + text, icon groups). */
    public static final int SPACE_4 = 4;

    public static final int RADIUS_FIELD_BUTTON = 8;
    public static final int RADIUS_LIST_ROW = 10;
    public static final int RADIUS_CARD_DIALOG = 16;

    /* ------------------------------------------------------------------ */
    /*  Font sizes (pt)                                                    */
    /* ------------------------------------------------------------------ */

    public static final float FONT_TITLE = 22f;      // screen/dialog titles
    public static final float FONT_SECTION = 16f;    // list header "All items"
    public static final float FONT_BODY = 14f;       // row names, field values
    public static final float FONT_LABEL = 12f;      // field labels, buttons
    public static final float FONT_HINT = 11f;       // timestamps, counts, hints
    public static final float FONT_TOTP = 26f;       // verification code digits

    /* ------------------------------------------------------------------ */
    /*  Light theme (spec section 1)                                       */
    /* ------------------------------------------------------------------ */

    public static final Color LIGHT_WINDOW = new Color(0xF5F6FA);
    public static final Color LIGHT_SURFACE = new Color(0xFFFFFF);
    public static final Color LIGHT_SIDEBAR = new Color(0xFFFFFF);
    public static final Color LIGHT_TEXT = new Color(0x1F2937);
    public static final Color LIGHT_TEXT_SECONDARY = new Color(0x374151);
    public static final Color LIGHT_MUTED = new Color(0x6B7280);
    public static final Color LIGHT_BORDER = new Color(0xE5E7EB);
    public static final Color LIGHT_ACCENT = new Color(0x4F46E5);
    public static final Color LIGHT_ACCENT_SOFT = new Color(0xEEF2FF);
    public static final Color LIGHT_DANGER = new Color(0xDC2626);
    public static final Color LIGHT_SUCCESS = new Color(0x16A34A);
    public static final Color LIGHT_WARNING = new Color(0xD97706);
    public static final Color LIGHT_STAR = new Color(0xF59E0B);

    /** Derived light states (hover/active, inputs, soft fills). */
    public static final Color LIGHT_ACCENT_HOVER = new Color(0x4338CA);
    public static final Color LIGHT_ACCENT_ACTIVE = new Color(0x3730A3);
    public static final Color LIGHT_BORDER_HOVER = new Color(0xD1D5DB);
    public static final Color LIGHT_ROW_HOVER = new Color(0xF3F4F6);
    public static final Color LIGHT_INPUT_BG = new Color(0xFFFFFF);
    public static final Color LIGHT_DISABLED = new Color(0x9CA3AF);
    public static final Color LIGHT_WARNING_BG = new Color(0xFDF3E3);
    public static final Color LIGHT_WARNING_BORDER = new Color(0xF2C48D);
    public static final Color LIGHT_WARNING_TITLE = new Color(0xB45309);

    /* ------------------------------------------------------------------ */
    /*  Dark theme (spec section 1)                                        */
    /* ------------------------------------------------------------------ */

    public static final Color DARK_WINDOW = new Color(0x0F172A);
    public static final Color DARK_SURFACE = new Color(0x1E293B);
    public static final Color DARK_SIDEBAR = new Color(0x111827);
    public static final Color DARK_TEXT = new Color(0xF1F5F9);
    public static final Color DARK_TEXT_SECONDARY = new Color(0xCBD5E1);
    public static final Color DARK_MUTED = new Color(0x94A3B8);
    public static final Color DARK_BORDER = new Color(0x334155);
    public static final Color DARK_ACCENT = new Color(0x818CF8);
    public static final Color DARK_ACCENT_SOFT = new Color(0x2E2A6B);
    public static final Color DARK_DANGER = new Color(0xF87171);
    public static final Color DARK_SUCCESS = new Color(0x4ADE80);
    public static final Color DARK_WARNING = new Color(0xFBBF24);
    public static final Color DARK_STAR = new Color(0xF59E0B);

    /** Derived dark states. */
    public static final Color DARK_ACCENT_HOVER = new Color(0xA5B4FC);
    public static final Color DARK_ACCENT_ACTIVE = new Color(0x6366F1);
    public static final Color DARK_BORDER_HOVER = new Color(0x475569);
    public static final Color DARK_ROW_HOVER = new Color(0x1A2537);
    public static final Color DARK_INPUT_BG = new Color(0x0F172A);
    public static final Color DARK_DISABLED = new Color(0x64748B);
    public static final Color DARK_WARNING_BG = new Color(0x2B2508);
    public static final Color DARK_WARNING_BORDER = new Color(0x92630F);
    public static final Color DARK_WARNING_TITLE = new Color(0xFBBF24);

    /* ------------------------------------------------------------------ */
    /*  Active theme switch                                                */
    /* ------------------------------------------------------------------ */

    public static boolean dark = false;

    /* ------------------------------------------------------------------ */
    /*  Accessors                                                          */
    /* ------------------------------------------------------------------ */

    public static Color window() {
        return dark ? DARK_WINDOW : LIGHT_WINDOW;
    }

    public static Color surface() {
        return dark ? DARK_SURFACE : LIGHT_SURFACE;
    }

    public static Color sidebar() {
        return dark ? DARK_SIDEBAR : LIGHT_SIDEBAR;
    }

    public static Color text() {
        return dark ? DARK_TEXT : LIGHT_TEXT;
    }

    public static Color textSecondary() {
        return dark ? DARK_TEXT_SECONDARY : LIGHT_TEXT_SECONDARY;
    }

    public static Color muted() {
        return dark ? DARK_MUTED : LIGHT_MUTED;
    }

    public static Color border() {
        return dark ? DARK_BORDER : LIGHT_BORDER;
    }

    public static Color accent() {
        return dark ? DARK_ACCENT : LIGHT_ACCENT;
    }

    public static Color accentSoft() {
        return dark ? DARK_ACCENT_SOFT : LIGHT_ACCENT_SOFT;
    }

    public static Color accentHover() {
        return dark ? DARK_ACCENT_HOVER : LIGHT_ACCENT_HOVER;
    }

    public static Color accentActive() {
        return dark ? DARK_ACCENT_ACTIVE : LIGHT_ACCENT_ACTIVE;
    }

    public static Color borderHover() {
        return dark ? DARK_BORDER_HOVER : LIGHT_BORDER_HOVER;
    }

    public static Color rowHover() {
        return dark ? DARK_ROW_HOVER : LIGHT_ROW_HOVER;
    }

    public static Color inputBg() {
        return dark ? DARK_INPUT_BG : LIGHT_INPUT_BG;
    }

    public static Color disabled() {
        return dark ? DARK_DISABLED : LIGHT_DISABLED;
    }

    public static Color danger() {
        return dark ? DARK_DANGER : LIGHT_DANGER;
    }

    public static Color success() {
        return dark ? DARK_SUCCESS : LIGHT_SUCCESS;
    }

    public static Color warning() {
        return dark ? DARK_WARNING : LIGHT_WARNING;
    }

    public static Color warningBg() {
        return dark ? DARK_WARNING_BG : LIGHT_WARNING_BG;
    }

    public static Color warningBorder() {
        return dark ? DARK_WARNING_BORDER : LIGHT_WARNING_BORDER;
    }

    public static Color warningTitle() {
        return dark ? DARK_WARNING_TITLE : LIGHT_WARNING_TITLE;
    }

    public static Color star() {
        return DARK_STAR;
    }

    /**
     * Text color for filled accent buttons: white on the saturated light-mode
     * indigo, near-black on the brighter dark-mode indigo (contrast rule).
     */
    public static Color buttonText() {
        return dark ? DARK_WINDOW : Color.WHITE;
    }
}
