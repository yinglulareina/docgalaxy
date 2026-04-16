package com.docgalaxy.ui.dialogs;

import com.docgalaxy.ui.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Non-modal progress dialog for long-running background operations
 * (e.g. bulk embedding on first open of a large knowledge base).
 *
 * Usage (indeterminate):
 *   ProgressDialog dlg = ProgressDialog.showIndeterminate(frame, "Embedding notes…");
 *   // … do work …
 *   dlg.close();
 *
 * Usage (determinate):
 *   ProgressDialog dlg = ProgressDialog.showDeterminate(frame, "Indexing…", 100);
 *   for (int i = 0; i < 100; i++) { dlg.setProgress(i + 1); }
 *   dlg.close();
 */
public class ProgressDialog extends JDialog {

    private final JProgressBar progressBar;
    private final JLabel       messageLabel;

    // ----------------------------------------------------------------
    // Construction (private – use static factories)
    // ----------------------------------------------------------------

    private ProgressDialog(Frame owner, String message, boolean indeterminate, int max) {
        super(owner, "DocGalaxy – Working…", false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);

        progressBar  = new JProgressBar(0, max);
        messageLabel = new JLabel(message);

        progressBar.setIndeterminate(indeterminate);
        progressBar.setStringPainted(!indeterminate);
        progressBar.setForeground(ThemeManager.TEXT_ACCENT);
        progressBar.setBackground(ThemeManager.BG_SURFACE);

        messageLabel.setFont(ThemeManager.FONT_BODY);
        messageLabel.setForeground(ThemeManager.TEXT_PRIMARY);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(ThemeManager.BG_SECONDARY);
        content.setBorder(new EmptyBorder(20, 24, 20, 24));
        content.add(messageLabel, BorderLayout.NORTH);
        content.add(progressBar,  BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setMinimumSize(new Dimension(320, 100));
        setLocationRelativeTo(owner);
    }

    // ----------------------------------------------------------------
    // Static factories
    // ----------------------------------------------------------------

    /** Show an indeterminate (spinner) progress dialog immediately. */
    public static ProgressDialog showIndeterminate(Frame owner, String message) {
        ProgressDialog dlg = new ProgressDialog(owner, message, true, 100);
        dlg.setVisible(true);
        return dlg;
    }

    /** Show a determinate progress dialog with a fixed maximum. */
    public static ProgressDialog showDeterminate(Frame owner, String message, int max) {
        ProgressDialog dlg = new ProgressDialog(owner, message, false, max);
        dlg.setVisible(true);
        return dlg;
    }

    // ----------------------------------------------------------------
    // Control
    // ----------------------------------------------------------------

    /** Update the progress value (1 … max). Must be called on the EDT. */
    public void setProgress(int value) {
        progressBar.setValue(value);
        progressBar.setString(value + " / " + progressBar.getMaximum());
    }

    /** Update the message label text. */
    public void setMessage(String message) {
        messageLabel.setText(message);
    }

    /** Dismiss the dialog. Safe to call from any thread. */
    public void close() {
        SwingUtilities.invokeLater(this::dispose);
    }
}
