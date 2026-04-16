package com.docgalaxy.ui.canvas;

import com.docgalaxy.model.celestial.CelestialBody;
import com.docgalaxy.model.celestial.Star;
import com.docgalaxy.ui.ThemeManager;
import com.docgalaxy.util.AppConstants;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;

/**
 * Floating overlay panel that displays a summary of a clicked {@link CelestialBody}.
 *
 * <p>The card is added as a null-layout child of {@link GalaxyCanvas} by
 * {@link CanvasInteractionHandler}; its position is updated on every
 * {@link #showFor} call.  Call {@link #hide()} to make it invisible.
 *
 * <p>Dimensions come from {@link AppConstants#PREVIEW_CARD_WIDTH} ×
 * {@link AppConstants#PREVIEW_CARD_HEIGHT}.
 */
final class PreviewCard extends JPanel {

    /** Pixel offset from the click point so the card does not cover the star. */
    private static final int CARD_OFFSET = 12;

    private final JLabel    titleLabel;
    private final JTextArea bodyText;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    PreviewCard() {
        setLayout(new BorderLayout(4, 4));
        setBackground(ThemeManager.BG_SURFACE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.EDGE_HIGHLIGHT, 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        setPreferredSize(new Dimension(AppConstants.PREVIEW_CARD_WIDTH,
                                       AppConstants.PREVIEW_CARD_HEIGHT));
        setVisible(false);

        titleLabel = new JLabel();
        titleLabel.setFont(ThemeManager.FONT_TITLE);
        titleLabel.setForeground(ThemeManager.TEXT_ACCENT);

        bodyText = new JTextArea();
        bodyText.setFont(ThemeManager.FONT_SMALL);
        bodyText.setForeground(ThemeManager.TEXT_PRIMARY);
        bodyText.setBackground(ThemeManager.BG_SURFACE);
        bodyText.setEditable(false);
        bodyText.setFocusable(false);
        bodyText.setLineWrap(true);
        bodyText.setWrapStyleWord(true);
        bodyText.setMargin(new Insets(4, 0, 0, 0));

        add(titleLabel, BorderLayout.NORTH);
        add(bodyText,   BorderLayout.CENTER);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Positions the card near {@code canvasPoint}, clamps it to stay within
     * the canvas bounds, fills in the body's details, and makes it visible.
     *
     * @param body        the celestial body to preview
     * @param canvasPoint click location in canvas-relative pixels
     * @param canvasW     canvas width (used for right-edge clamping; 0 = unclamped)
     * @param canvasH     canvas height (used for bottom-edge clamping; 0 = unclamped)
     */
    void showFor(CelestialBody body, Point canvasPoint, int canvasW, int canvasH) {
        // ── content ──────────────────────────────────────────────────────────
        titleLabel.setText(body.getTooltipText());
        if (body instanceof Star star) {
            bodyText.setText(star.getNote().getFilePath());
        } else {
            bodyText.setText(body.getId());
        }

        // ── position ─────────────────────────────────────────────────────────
        int maxX = (canvasW > 0) ? canvasW  - AppConstants.PREVIEW_CARD_WIDTH  - 2 : 10_000;
        int maxY = (canvasH > 0) ? canvasH  - AppConstants.PREVIEW_CARD_HEIGHT - 2 : 10_000;
        int x = Math.max(2, Math.min(canvasPoint.x + CARD_OFFSET, maxX));
        int y = Math.max(2, Math.min(canvasPoint.y + CARD_OFFSET, maxY));
        setBounds(x, y, AppConstants.PREVIEW_CARD_WIDTH, AppConstants.PREVIEW_CARD_HEIGHT);
        setVisible(true);
    }

    /**
     * Hides the card.
     */
    public void dismiss() {
        setVisible(false);
    }
}
