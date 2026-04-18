package com.docgalaxy.ui.canvas;

import com.docgalaxy.model.Edge;
import com.docgalaxy.model.celestial.CelestialBody;
import com.docgalaxy.model.celestial.Nebula;
import com.docgalaxy.model.celestial.Star;
import com.docgalaxy.ui.ThemeManager;
import com.docgalaxy.util.AppConstants;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.util.ArrayList;
import java.util.List;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * Floating overlay panel that displays a summary of a clicked {@link CelestialBody}.
 *
 * <h3>Visual design</h3>
 * <ul>
 *   <li>Background — {@link ThemeManager#BG_SURFACE}, rounded corners (rx = 16).</li>
 *   <li>Glow ring — sector colour at alpha 40, drawn as a slightly enlarged
 *       rounded rect behind the card.</li>
 *   <li>Border — 1.5 px stroke in the sector colour.</li>
 *   <li>Title — {@link ThemeManager#FONT_TITLE} + {@link ThemeManager#TEXT_PRIMARY}.</li>
 *   <li>Summary — {@link ThemeManager#FONT_BODY} + {@link ThemeManager#TEXT_SECONDARY}.</li>
 *   <li>Sector tag — {@link ThemeManager#FONT_SMALL} + {@link ThemeManager#TEXT_ACCENT},
 *       pinned to the bottom-left.</li>
 * </ul>
 *
 * <p>The card is added as a null-layout child of {@link GalaxyCanvas} by
 * {@link CanvasInteractionHandler}; its position is updated on every
 * {@link #showFor} call.  Call {@link #hide()} to make it invisible.
 */
final class PreviewCard extends JPanel {

    /** Pixel offset from the click point so the card does not cover the star. */
    private static final int CARD_OFFSET = 12;

    /** Corner arc radius for the rounded rectangle. */
    private static final int ARC = 16;

    /** Extra padding around the card used for the glow ring. */
    private static final int GLOW_PADDING = 6;

    /** Alpha (0-255) of the glow ring. */
    private static final int GLOW_ALPHA = 40;

    /** Border stroke width in px. */
    private static final float BORDER_WIDTH = 1.5f;

    /** Inner padding between the card edge and text. */
    private static final int INSET = 12;

    /** Vertical gap between text rows. */
    private static final int LINE_GAP = 4;

    // Content fields — updated in showFor()
    private String title       = "";
    private String summary     = "";
    private String sectorLabel = "";
    private Color  accentColor = ThemeManager.TEXT_ACCENT;

    /** Max lines used when wrapping summary text. Edge cards use 6; others use 3. */
    private int maxSummaryLines = 3;

    /** True while showing an edge card — allows updateDescription to resize. */
    private boolean isEdgeCard = false;

    /** Stored edge positioning args so updateDescription can recompute bounds. */
    private int edgeMidX, edgeMidY, edgeCanvasW, edgeCanvasH;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    PreviewCard() {
        setOpaque(false);   // we paint everything ourselves
        setPreferredSize(new Dimension(AppConstants.PREVIEW_CARD_WIDTH,
                                       AppConstants.PREVIEW_CARD_HEIGHT));
        setVisible(false);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Positions the card near {@code canvasPoint}, fills in content from
     * {@code body}, and makes it visible.
     *
     * @param body        the celestial body to preview
     * @param canvasPoint click location in canvas-relative pixels
     * @param canvasW     canvas width for right-edge clamping (0 = unclamped)
     * @param canvasH     canvas height for bottom-edge clamping (0 = unclamped)
     */
    void showFor(CelestialBody body, Point canvasPoint, int canvasW, int canvasH) {
        maxSummaryLines = 5;
        isEdgeCard = false;

        // ── content ──────────────────────────────────────────────────────────
        if (body instanceof Star star) {
            title       = star.getNote().getFileName();
            summary     = star.getNote().getFilePath();
            sectorLabel = star.getSector().getLabel();
            accentColor = star.getSector().getColor();
        } else if (body instanceof Nebula nebula) {
            title       = nebula.getSector().getLabel();
            summary     = nebula.getTooltipText();
            sectorLabel = nebula.getSector().getLabel();
            accentColor = nebula.getSector().getColor();
        } else {
            title       = body.getTooltipText();
            summary     = body.getId();
            sectorLabel = "";
            accentColor = ThemeManager.TEXT_ACCENT;
        }

        // ── position (dynamic height based on wrapped summary lines) ─────────
        int  w     = AppConstants.PREVIEW_CARD_WIDTH + GLOW_PADDING * 2;
        int  textW = AppConstants.PREVIEW_CARD_WIDTH - INSET * 2;
        FontMetrics fm    = getFontMetrics(ThemeManager.FONT_BODY);
        int  lines = (fm != null)
                ? wrapText(summary, textW, fm, maxSummaryLines).size()
                : maxSummaryLines;
        int contentH = 25 + lines * 18 + 30;
        contentH = Math.max(120, Math.min(280, contentH));
        int  h    = contentH + GLOW_PADDING * 2;

        int maxX = (canvasW > 0) ? canvasW - w - 2 : 10_000;
        int maxY = (canvasH > 0) ? canvasH - h - 2 : 10_000;
        int x    = Math.max(2, Math.min(canvasPoint.x + CARD_OFFSET, maxX));
        int y    = Math.max(2, Math.min(canvasPoint.y + CARD_OFFSET, maxY));
        setBounds(x, y, w, h);
        setVisible(true);
        repaint();
    }

    /**
     * Variant of {@link #showFor} optimised for hover use: shows
     * {@code star}'s filename as title, a pre-read content {@code snippet}
     * as summary, and the sector label at the bottom.
     *
     * @param star        the hovered star
     * @param snippet     first ~100 chars of the note's file content
     * @param canvasPoint cursor position in canvas-relative pixels
     * @param canvasW     canvas width for right-edge clamping (0 = unclamped)
     * @param canvasH     canvas height for bottom-edge clamping (0 = unclamped)
     */
    void showHover(Star star, String snippet, Point canvasPoint, int canvasW, int canvasH) {
        maxSummaryLines = 5;
        isEdgeCard = false;
        title       = star.getNote().getFileName();
        summary     = snippet.isBlank() ? star.getNote().getFilePath() : snippet;
        sectorLabel = star.getSector().getLabel();
        accentColor = star.getSector().getColor();

        int  w     = AppConstants.PREVIEW_CARD_WIDTH + GLOW_PADDING * 2;
        int  textW = AppConstants.PREVIEW_CARD_WIDTH - INSET * 2;
        FontMetrics fm    = getFontMetrics(ThemeManager.FONT_BODY);
        int  lines = (fm != null)
                ? wrapText(summary, textW, fm, maxSummaryLines).size()
                : maxSummaryLines;
        int contentH = 25 + lines * 18 + 30;
        contentH = Math.max(120, Math.min(280, contentH));
        int h    = contentH + GLOW_PADDING * 2;

        int maxX = (canvasW > 0) ? canvasW - w - 2 : 10_000;
        int maxY = (canvasH > 0) ? canvasH - h - 2 : 10_000;
        int x    = Math.max(2, Math.min(canvasPoint.x + CARD_OFFSET, maxX));
        int y    = Math.max(2, Math.min(canvasPoint.y + CARD_OFFSET, maxY));
        setBounds(x, y, w, h);
        setVisible(true);
        repaint();
    }

    /**
     * Shows the card anchored above the edge midpoint to describe a
     * clicked edge's relationship.
     *
     * <p>Title line: "fileA.md ↔ fileB.md"<br>
     * Body: {@code description} (may be "Analyzing relationship…" while the
     * LLM call is in-flight).<br>
     * Sector tag: empty (edge cards don't belong to a single sector).
     *
     * @param edge        the clicked edge (used for similarity display)
     * @param nameA       filename of the from-note
     * @param nameB       filename of the to-note
     * @param description relationship sentence (or placeholder text)
     * @param midX        screen X of the edge midpoint
     * @param midY        screen Y of the edge midpoint
     * @param canvasW     canvas width for right-edge clamping (0 = unclamped)
     * @param canvasH     canvas height for bottom-edge clamping (0 = unclamped)
     */
    void showEdge(Edge edge, String nameA, String nameB,
                  String description, int midX, int midY,
                  int canvasW, int canvasH) {
        maxSummaryLines = 6;
        isEdgeCard      = true;
        edgeMidX        = midX;
        edgeMidY        = midY;
        edgeCanvasW     = canvasW;
        edgeCanvasH     = canvasH;

        title       = nameA + " ↔ " + nameB;
        summary     = description;
        sectorLabel = "";
        accentColor = ThemeManager.EDGE_HIGHLIGHT;

        applyEdgeBounds(description, midX, midY, canvasW, canvasH);
        setVisible(true);
        repaint();
    }

    /**
     * Updates only the body text of a currently-visible edge card
     * (called on the EDT when the LLM completes).  For edge cards the card
     * is also resized to fit the new (typically longer) description.
     *
     * @param description the finalized relationship description
     */
    void updateDescription(String description) {
        summary = description;
        if (isEdgeCard) {
            applyEdgeBounds(description, edgeMidX, edgeMidY, edgeCanvasW, edgeCanvasH);
        }
        repaint();
    }

    // -------------------------------------------------------------------------
    // Private — edge card sizing
    // -------------------------------------------------------------------------

    /**
     * Computes a dynamic card height for edge cards and calls {@link #setBounds}.
     *
     * <p>Height formula (content area):
     * {@code 25 (title) + lines × 18 (body) + 30 (padding)},
     * clamped to [{@code 120}, {@code 280}].
     * The actual {@code setBounds} height adds {@code GLOW_PADDING × 2}.
     */
    private void applyEdgeBounds(String description, int midX, int midY,
                                  int canvasW, int canvasH) {
        int w       = AppConstants.PREVIEW_CARD_WIDTH + GLOW_PADDING * 2;
        int textW   = AppConstants.PREVIEW_CARD_WIDTH - INSET * 2;
        FontMetrics fm    = getFontMetrics(ThemeManager.FONT_BODY);
        int         lines = (fm != null)
                ? wrapText(description, textW, fm, maxSummaryLines).size()
                : Math.min(maxSummaryLines, 3);   // safe fallback if no graphics yet

        int contentH = 25 + lines * 18 + 30;
        contentH = Math.max(120, Math.min(280, contentH));
        int h = contentH + GLOW_PADDING * 2;

        int maxX = (canvasW > 0) ? canvasW - w - 2 : 10_000;
        int maxY = (canvasH > 0) ? canvasH - h - 2 : 10_000;
        int x    = Math.max(2, Math.min(midX - w / 2, maxX));
        int y    = Math.max(2, Math.min(midY - h - 20, maxY));
        setBounds(x, y, w, h);
    }

    /** Hides the card. */
    public void dismiss() {
        setVisible(false);
    }

    // -------------------------------------------------------------------------
    // Custom painting
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                               RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            int w = getWidth();
            int h = getHeight();

            // ── Glow ring (behind card) ───────────────────────────────────────
            Color glowColor = new Color(accentColor.getRed(),
                                        accentColor.getGreen(),
                                        accentColor.getBlue(),
                                        GLOW_ALPHA);
            g.setColor(glowColor);
            g.fill(new RoundRectangle2D.Double(
                    0, 0, w, h, ARC + GLOW_PADDING, ARC + GLOW_PADDING));

            // ── Card background ───────────────────────────────────────────────
            g.setColor(ThemeManager.BG_SURFACE);
            g.fill(new RoundRectangle2D.Double(
                    GLOW_PADDING, GLOW_PADDING,
                    w - GLOW_PADDING * 2, h - GLOW_PADDING * 2,
                    ARC, ARC));

            // ── Border ────────────────────────────────────────────────────────
            g.setColor(accentColor);
            g.setStroke(new BasicStroke(BORDER_WIDTH));
            g.draw(new RoundRectangle2D.Double(
                    GLOW_PADDING + BORDER_WIDTH / 2,
                    GLOW_PADDING + BORDER_WIDTH / 2,
                    w - GLOW_PADDING * 2 - BORDER_WIDTH,
                    h - GLOW_PADDING * 2 - BORDER_WIDTH,
                    ARC, ARC));

            // ── Text area origin ──────────────────────────────────────────────
            int tx = GLOW_PADDING + INSET;
            int ty = GLOW_PADDING + INSET;
            int textW = w - GLOW_PADDING * 2 - INSET * 2;

            // Title
            g.setFont(ThemeManager.FONT_TITLE);
            g.setColor(ThemeManager.TEXT_PRIMARY);
            FontMetrics fmTitle = g.getFontMetrics();
            String clippedTitle = clipText(title, textW, fmTitle);
            g.drawString(clippedTitle, tx, ty + fmTitle.getAscent());
            ty += fmTitle.getHeight() + LINE_GAP;

            // Summary (up to maxSummaryLines lines)
            g.setFont(ThemeManager.FONT_BODY);
            g.setColor(ThemeManager.TEXT_SECONDARY);
            FontMetrics fmBody = g.getFontMetrics();
            List<String> summaryLines = wrapText(summary, textW, fmBody, maxSummaryLines);
            for (String line : summaryLines) {
                g.drawString(line, tx, ty + fmBody.getAscent());
                ty += fmBody.getHeight() + LINE_GAP;
            }

            // Sector label — pinned to bottom of the card interior
            if (!sectorLabel.isBlank()) {
                g.setFont(ThemeManager.FONT_SMALL);
                g.setColor(ThemeManager.TEXT_ACCENT);
                FontMetrics fmSmall = g.getFontMetrics();
                int labelY = GLOW_PADDING + (h - GLOW_PADDING * 2) - INSET;
                g.drawString(sectorLabel, tx, labelY);
            }
        } finally {
            g.dispose();
        }
    }

    // -------------------------------------------------------------------------
    // Text helpers
    // -------------------------------------------------------------------------

    /** Clips {@code text} to fit within {@code maxW} pixels, appending "…" if needed. */
    private static String clipText(String text, int maxW, FontMetrics fm) {
        if (fm.stringWidth(text) <= maxW) return text;
        String ellipsis = "…";
        int ellW = fm.stringWidth(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (fm.stringWidth(sb.toString()) + fm.charWidth(c) + ellW > maxW) break;
            sb.append(c);
        }
        return sb + ellipsis;
    }

    /**
     * Wraps {@code text} into at most {@code maxLines} lines, each fitting
     * within {@code maxW} pixels.
     *
     * <p>When the text overflows {@code maxLines}, the visible content is
     * searched backward for the last sentence-ending character
     * ({@code . ! ? 。！？}).  If found, the text is cut there and "…" is
     * appended.  If no sentence boundary exists, the last space (word
     * boundary) is used.  The result is then re-wrapped and returned.
     */
    private static List<String> wrapText(String text, int maxW, FontMetrics fm, int maxLines) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        boolean overflow = false;

        for (String word : words) {
            if (word.isEmpty()) continue;
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (fm.stringWidth(candidate) <= maxW) {
                current = new StringBuilder(candidate);
            } else {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    if (lines.size() >= maxLines) { overflow = true; break; }
                }
                current = new StringBuilder(word);
            }
        }
        if (!overflow && !current.isEmpty()) lines.add(current.toString());
        if (!overflow) return lines;

        // ── Smart truncation ─────────────────────────────────────────────────
        // Join all visible lines, then search backward for a sentence boundary.
        String allVisible = String.join(" ", lines);
        int cutAt = -1;
        for (int i = allVisible.length() - 1; i >= 0; i--) {
            char c = allVisible.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '。' || c == '！' || c == '？') {
                cutAt = i + 1;   // include the punctuation character
                break;
            }
        }
        if (cutAt < 0) {
            // No sentence boundary — fall back to last word boundary.
            cutAt = allVisible.lastIndexOf(' ');
            if (cutAt < 0) cutAt = allVisible.length();  // single long token
        }

        String truncated = allVisible.substring(0, cutAt).stripTrailing() + "…";

        // Re-wrap the shorter truncated string (no recursive smart-truncation).
        List<String> result = new ArrayList<>();
        String[] tw = truncated.split("\\s+");
        StringBuilder cur = new StringBuilder();
        for (String word : tw) {
            if (word.isEmpty()) continue;
            String cand = cur.isEmpty() ? word : cur + " " + word;
            if (fm.stringWidth(cand) <= maxW) {
                cur = new StringBuilder(cand);
            } else {
                if (!cur.isEmpty()) result.add(cur.toString());
                cur = new StringBuilder(word);
            }
        }
        if (!cur.isEmpty()) result.add(cur.toString());
        return result;
    }
}
