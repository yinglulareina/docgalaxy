package com.docgalaxy.ui.dialogs;

import com.docgalaxy.ui.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Welcome overlay shown on first launch (or when no knowledge base is open).
 *
 * Rendered as a modal JDialog centred over the main window.
 * The design calls for the star background to be visible through the overlay;
 * the JDialog approach achieves a clean "card on dark background" look which
 * matches the overall FlatLaf dark theme.
 *
 * Callbacks:
 *   setOnFolderSelected(Consumer<Path>) – called when the user picks a folder
 *   setOnDemoSelected(Runnable)         – called when "Open Demo KB" is clicked
 */
public class WelcomeOverlay extends JDialog {

    private Consumer<Path> onFolderSelected;
    private Runnable       onDemoSelected;

    public WelcomeOverlay(JFrame parent) {
        super(parent, true);   // modal
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));  // transparent root for rounded-corner card
        initComponents();
        pack();
        setLocationRelativeTo(parent);
    }

    // ----------------------------------------------------------------
    // Callback wiring
    // ----------------------------------------------------------------

    public void setOnFolderSelected(Consumer<Path> callback) { this.onFolderSelected = callback; }
    public void setOnDemoSelected(Runnable callback)         { this.onDemoSelected = callback; }

    // ----------------------------------------------------------------
    // UI construction
    // ----------------------------------------------------------------

    private void initComponents() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(ThemeManager.BG_SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x3A3A6A), 1),
            BorderFactory.createEmptyBorder(44, 56, 44, 56)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill  = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;

        // Title
        JLabel title = new JLabel("DocGalaxy");
        title.setFont(new Font("Inter", Font.BOLD, 30));
        title.setForeground(ThemeManager.TEXT_ACCENT);
        title.setHorizontalAlignment(JLabel.CENTER);
        gbc.gridy  = 0;
        gbc.insets = new Insets(0, 0, 6, 0);
        card.add(title, gbc);

        // Subtitle
        JLabel subtitle = new JLabel("Your knowledge, mapped as a semantic galaxy");
        subtitle.setFont(ThemeManager.FONT_BODY);
        subtitle.setForeground(ThemeManager.TEXT_SECONDARY);
        subtitle.setHorizontalAlignment(JLabel.CENTER);
        gbc.gridy  = 1;
        gbc.insets = new Insets(0, 0, 32, 0);
        card.add(subtitle, gbc);

        // Primary action: select folder
        JButton openBtn = primaryButton("Select Knowledge Base Folder");
        openBtn.addActionListener(e -> handleSelectFolder());
        gbc.gridy  = 2;
        gbc.insets = new Insets(0, 0, 12, 0);
        card.add(openBtn, gbc);

        // Secondary action: open demo
        JButton demoBtn = secondaryButton("Open Demo Knowledge Base");
        demoBtn.addActionListener(e -> handleOpenDemo());
        gbc.gridy  = 3;
        gbc.insets = new Insets(0, 0, 0, 0);
        card.add(demoBtn, gbc);

        setContentPane(card);
    }

    // ----------------------------------------------------------------
    // Button actions
    // ----------------------------------------------------------------

    private void handleSelectFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Knowledge Base Folder");
        chooser.setApproveButtonText("Open");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            dispose();
            if (onFolderSelected != null) {
                onFolderSelected.accept(chooser.getSelectedFile().toPath());
            }
        }
    }

    private void handleOpenDemo() {
        dispose();
        if (onDemoSelected != null) {
            onDemoSelected.run();
        }
    }

    // ----------------------------------------------------------------
    // Button factory helpers
    // ----------------------------------------------------------------

    private JButton primaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(ThemeManager.TEXT_ACCENT);
        btn.setForeground(ThemeManager.BG_PRIMARY);
        btn.setFont(ThemeManager.FONT_BODY.deriveFont(Font.BOLD));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setPreferredSize(new Dimension(300, 42));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton secondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(ThemeManager.BG_SECONDARY);
        btn.setForeground(ThemeManager.TEXT_PRIMARY);
        btn.setFont(ThemeManager.FONT_BODY);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x3A3A6A), 1),
            BorderFactory.createEmptyBorder(5, 12, 5, 12)
        ));
        btn.setPreferredSize(new Dimension(300, 42));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
