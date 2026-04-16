package com.docgalaxy.ui.components;

import com.docgalaxy.ui.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JToolBar;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Application toolbar (NORTH region of MainFrame).
 *
 * Buttons: Open KB | Refresh | Layout dropdown | Settings
 *
 * Callbacks are injected via setters so MainFrame can wire them to
 * KnowledgeBaseManager and LayoutManager once those are available.
 */
public class ToolBar extends JToolBar {

    private final JComboBox<String> layoutCombo;

    // Callbacks – set by MainFrame after wiring everything together
    private Consumer<Path>   onOpenKnowledgeBase;
    private Runnable         onRefresh;
    private Consumer<String> onLayoutSwitch;
    private Runnable         onSettings;

    public ToolBar() {
        setFloatable(false);
        setBackground(ThemeManager.BG_SECONDARY);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x2A2A4A)));

        JButton openBtn = createButton("Open KB");
        openBtn.addActionListener(e -> handleOpenKB());
        add(openBtn);

        addSeparator(new Dimension(8, 0));

        JButton refreshBtn = createButton("Refresh");
        refreshBtn.addActionListener(e -> { if (onRefresh != null) onRefresh.run(); });
        add(refreshBtn);

        addSeparator(new Dimension(16, 0));

        JLabel layoutLabel = new JLabel("Layout: ");
        layoutLabel.setForeground(ThemeManager.TEXT_SECONDARY);
        layoutLabel.setFont(ThemeManager.FONT_BODY);
        add(layoutLabel);

        layoutCombo = new JComboBox<>(new String[]{"Galaxy", "Tree", "Radial"});
        layoutCombo.setBackground(ThemeManager.BG_SURFACE);
        layoutCombo.setForeground(ThemeManager.TEXT_PRIMARY);
        layoutCombo.setFont(ThemeManager.FONT_BODY);
        layoutCombo.setMaximumSize(new Dimension(130, 28));
        layoutCombo.setPreferredSize(new Dimension(130, 28));
        layoutCombo.addActionListener(e -> {
            if (onLayoutSwitch != null) {
                onLayoutSwitch.accept((String) layoutCombo.getSelectedItem());
            }
        });
        add(layoutCombo);

        addSeparator(new Dimension(16, 0));

        JButton settingsBtn = createButton("Settings");
        settingsBtn.addActionListener(e -> { if (onSettings != null) onSettings.run(); });
        add(settingsBtn);
    }

    // ----------------------------------------------------------------
    // Callback wiring
    // ----------------------------------------------------------------

    public void setOnOpenKnowledgeBase(Consumer<Path> callback) { this.onOpenKnowledgeBase = callback; }
    public void setOnRefresh(Runnable callback)                 { this.onRefresh = callback; }
    public void setOnLayoutSwitch(Consumer<String> callback)    { this.onLayoutSwitch = callback; }
    public void setOnSettings(Runnable callback)                { this.onSettings = callback; }

    public String getSelectedLayout() { return (String) layoutCombo.getSelectedItem(); }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private void handleOpenKB() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Knowledge Base Folder");
        chooser.setApproveButtonText("Open");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && onOpenKnowledgeBase != null) {
            onOpenKnowledgeBase.accept(chooser.getSelectedFile().toPath());
        }
    }

    private JButton createButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(ThemeManager.BG_SECONDARY);
        btn.setForeground(ThemeManager.TEXT_PRIMARY);
        btn.setFont(ThemeManager.FONT_BODY);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
