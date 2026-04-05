package com.docgalaxy.ui;

import java.awt.Color;
import java.awt.Font;

public final class ThemeManager {
    private ThemeManager() {}

    // Backgrounds
    public static final Color BG_PRIMARY = new Color(0x1A, 0x1A, 0x2E);
    public static final Color BG_SECONDARY = new Color(0x16, 0x21, 0x3E);
    public static final Color BG_SURFACE = new Color(0x1F, 0x2B, 0x4D);

    // Text
    public static final Color TEXT_PRIMARY = new Color(0xE0, 0xE0, 0xE0);
    public static final Color TEXT_SECONDARY = new Color(0x80, 0x80, 0x99);
    public static final Color TEXT_ACCENT = new Color(0x82, 0xAA, 0xFF);

    // Edges
    public static final Color EDGE_DEFAULT = new Color(255, 255, 255, 20);
    public static final Color EDGE_HIGHLIGHT = new Color(130, 170, 255, 96);

    // Sector palette (8 colors for different star regions)
    public static final Color[] SECTOR_PALETTE = {
        new Color(0x64, 0xB5, 0xF6),   // ice blue
        new Color(0xBA, 0x68, 0xC8),   // violet
        new Color(0xFF, 0xB7, 0x4D),   // amber
        new Color(0x4D, 0xD0, 0xE1),   // cyan
        new Color(0xE0, 0x7C, 0x7C),   // coral
        new Color(0x81, 0xC7, 0x84),   // mint
        new Color(0xFF, 0xA7, 0x26),   // orange
        new Color(0x7D, 0x84, 0xB2),   // gray-blue
    };

    // Fonts (with fallback)
    public static final Font FONT_TITLE = getFont(Font.BOLD, 14);
    public static final Font FONT_BODY = getFont(Font.PLAIN, 12);
    public static final Font FONT_SMALL = getFont(Font.PLAIN, 10);
    public static final Font FONT_SECTOR_LABEL = getFont(Font.BOLD, 16);

    private static Font getFont(int style, int size) {
        Font inter = new Font("Inter", style, size);
        if (inter.getFamily().equals("Inter")) return inter;
        return new Font(Font.SANS_SERIF, style, size);
    }

    /** Get a sector color by index (wraps around). */
    public static Color getSectorColor(int index) {
        return SECTOR_PALETTE[index % SECTOR_PALETTE.length];
    }
}
