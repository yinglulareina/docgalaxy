package com.docgalaxy.ui.dialogs;

import com.docgalaxy.ui.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Modal dialog asking the user to confirm whether a file was renamed/moved.
 *
 * Shown during startup reconciliation when a disk file matches an orphaned
 * index record by content hash but at a different path.
 *
 * Usage:
 *   boolean wasRenamed = ReconciliationDialog.showDialog(
 *       frame, "notes/old-name.md", "notes/new-name.md");
 *   // true  → update the record's path (rename confirmed)
 *   // false → treat the new file as brand new (create a new Note)
 */
public class ReconciliationDialog extends JDialog {

    private boolean confirmed = false;

    // ----------------------------------------------------------------
    // Construction (private – use static factory)
    // ----------------------------------------------------------------

    private ReconciliationDialog(Frame owner, String oldPath, String newPath) {
        super(owner, "File Reconciliation", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel content = buildContent(oldPath, newPath);
        setContentPane(content);
        pack();
        setMinimumSize(new Dimension(420, 160));
        setLocationRelativeTo(owner);
    }

    // ----------------------------------------------------------------
    // Static factory
    // ----------------------------------------------------------------

    /**
     * Show the dialog and return the user's choice.
     *
     * @return true if the user confirms it was a rename; false if new file.
     */
    public static boolean showDialog(Frame owner, String oldPath, String newPath) {
        ReconciliationDialog dlg = new ReconciliationDialog(owner, oldPath, newPath);
        dlg.setVisible(true);
        return dlg.confirmed;
    }

    // ----------------------------------------------------------------
    // Layout
    // ----------------------------------------------------------------

    private JPanel buildContent(String oldPath, String newPath) {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(ThemeManager.BG_SECONDARY);
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        // Question
        JLabel question = new JLabel(
            "<html>A file in your knowledge base may have been renamed:<br>" +
            "<b>Was:</b> <tt>" + oldPath + "</tt><br>" +
            "<b>Now:</b> <tt>" + newPath + "</tt></html>");
        question.setFont(ThemeManager.FONT_BODY);
        question.setForeground(ThemeManager.TEXT_PRIMARY);

        // Buttons
        JButton yesBtn = new JButton("Yes, it was renamed");
        yesBtn.setToolTipText("Update the record – keep existing embedding");
        yesBtn.addActionListener(e -> { confirmed = true;  dispose(); });

        JButton noBtn = new JButton("No, treat as new file");
        noBtn.setToolTipText("Keep old record as ORPHANED, create a new Note");
        noBtn.addActionListener(e -> { confirmed = false; dispose(); });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(ThemeManager.BG_SECONDARY);
        buttons.add(noBtn);
        buttons.add(yesBtn);

        root.add(question, BorderLayout.CENTER);
        root.add(buttons,  BorderLayout.SOUTH);

        return root;
    }
}
