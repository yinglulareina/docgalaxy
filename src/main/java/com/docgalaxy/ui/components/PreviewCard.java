package com.docgalaxy.ui.components;

import com.docgalaxy.model.Note;
import com.docgalaxy.ui.ThemeManager;
import com.docgalaxy.util.AppConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Floating 280×160 card that shows a note summary.
 *
 * Shown when the user clicks a star node on the canvas.
 * Dismissed by clicking the ✕ button or clicking elsewhere.
 *
 * Usage:
 *   PreviewCard card = new PreviewCard(ownerFrame, rootPath);
 *   card.showFor(note, screenX, screenY);
 */
public class PreviewCard extends JDialog {

    private static final int ARC       = 12;
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final Path    kbRoot;
    private JLabel  titleLabel;
    private JLabel  metaLabel;
    private JTextArea summaryArea;

    public PreviewCard(Frame owner, Path kbRoot) {
        super(owner, false);
        this.kbRoot = kbRoot;

        setUndecorated(true);
        setSize(AppConstants.PREVIEW_CARD_WIDTH, AppConstants.PREVIEW_CARD_HEIGHT);
        setBackground(new Color(0, 0, 0, 0));

        JPanel content = buildContent();
        setContentPane(content);
    }

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    /** Display the card near the given screen point, populated with note data. */
    public void showFor(Note note, int screenX, int screenY) {
        titleLabel.setText(note.getFileName());
        metaLabel.setText(buildMeta(note));
        summaryArea.setText(readPreview(note));

        // Offset slightly so the cursor doesn't sit on the card
        int x = screenX + 12;
        int y = screenY - AppConstants.PREVIEW_CARD_HEIGHT / 2;

        // Keep on screen
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        x = Math.min(x, screen.width  - AppConstants.PREVIEW_CARD_WIDTH  - 8);
        y = Math.max(y, 8);
        y = Math.min(y, screen.height - AppConstants.PREVIEW_CARD_HEIGHT - 8);

        setLocation(x, y);
        setVisible(true);
    }

    // ----------------------------------------------------------------
    // Construction helpers
    // ----------------------------------------------------------------

    private JPanel buildContent() {
        JPanel panel = new JPanel(new BorderLayout(0, 4)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ThemeManager.BG_SURFACE);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC));
                g2.setColor(ThemeManager.BG_PRIMARY.darker());
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC));
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(10, 12, 10, 12));

        // Title row
        titleLabel = new JLabel("Note title");
        titleLabel.setFont(ThemeManager.FONT_TITLE);
        titleLabel.setForeground(ThemeManager.TEXT_ACCENT);

        JButton closeBtn = new JButton("✕");
        closeBtn.setFont(ThemeManager.FONT_SMALL);
        closeBtn.setForeground(ThemeManager.TEXT_SECONDARY);
        closeBtn.setBackground(null);
        closeBtn.setOpaque(false);
        closeBtn.setBorder(null);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> setVisible(false));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(titleLabel, BorderLayout.CENTER);
        titleRow.add(closeBtn,   BorderLayout.EAST);

        // Meta (size + date)
        metaLabel = new JLabel(" ");
        metaLabel.setFont(ThemeManager.FONT_SMALL);
        metaLabel.setForeground(ThemeManager.TEXT_SECONDARY);

        // Summary
        summaryArea = new JTextArea();
        summaryArea.setFont(ThemeManager.FONT_BODY);
        summaryArea.setForeground(ThemeManager.TEXT_PRIMARY);
        summaryArea.setBackground(null);
        summaryArea.setOpaque(false);
        summaryArea.setEditable(false);
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        summaryArea.setBorder(null);

        JPanel top = new JPanel(new BorderLayout(0, 2));
        top.setOpaque(false);
        top.add(titleRow,  BorderLayout.NORTH);
        top.add(metaLabel, BorderLayout.SOUTH);

        panel.add(top,         BorderLayout.NORTH);
        panel.add(summaryArea, BorderLayout.CENTER);

        return panel;
    }

    private String buildMeta(Note note) {
        long bytes = note.getFileSize();
        String size = bytes < 1024
            ? bytes + " B"
            : (bytes / 1024) + " KB";

        String date = note.getCreatedAt() != null
            ? DATE_FMT.format(note.getCreatedAt())
            : "–";

        return size + "  ·  " + date;
    }

    private String readPreview(Note note) {
        try {
            Path file = kbRoot.resolve(note.getFilePath());
            if (!Files.exists(file)) return "(file not found)";
            String content = Files.readString(file);
            // Return first ~200 chars, stripped of markdown headings
            String stripped = content.replaceAll("#+\\s+", "").strip();
            return stripped.length() <= 200
                ? stripped
                : stripped.substring(0, 200) + "…";
        } catch (IOException e) {
            return "(could not read file)";
        }
    }
}
