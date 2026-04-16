package com.docgalaxy.ui.dialogs;

import com.docgalaxy.ai.navigator.LearningStyle;
import com.docgalaxy.persistence.AppConfig;
import com.docgalaxy.persistence.ConfigStore;
import com.docgalaxy.ui.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Modal settings dialog.
 *
 * Sections:
 *   • AI Provider – provider dropdown, API key, model, dimension
 *   • Appearance  – learning style
 *
 * On Save:
 *   Validates the API key field (encrypts if not already encrypted),
 *   writes config.json, and fires onSaved with the new AppConfig.
 */
public class SettingsDialog extends JDialog {

    // AI Provider tab
    private final JComboBox<String>   providerBox;
    private final JPasswordField      apiKeyField;
    private final JTextField          modelField;
    private final JSpinner            dimensionSpinner;

    // Appearance tab
    private final JComboBox<LearningStyle> styleBox;

    private final ConfigStore configStore;
    private       AppConfig   current;
    private Consumer<AppConfig> onSaved;

    // ----------------------------------------------------------------
    // Construction
    // ----------------------------------------------------------------

    public SettingsDialog(Frame owner, Path storeDir) {
        super(owner, "Settings", true);
        this.configStore = new ConfigStore(storeDir);
        this.current     = configStore.loadOrDefault();

        providerBox      = new JComboBox<>(new String[]{"openai", "ollama"});
        apiKeyField      = new JPasswordField(30);
        modelField       = new JTextField(20);
        dimensionSpinner = new JSpinner(new SpinnerNumberModel(1536, 64, 4096, 64));
        styleBox         = new JComboBox<>(LearningStyle.values());

        populateFromConfig(current);

        JPanel content = buildContent();
        setContentPane(content);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    // ----------------------------------------------------------------
    // Callback
    // ----------------------------------------------------------------

    public void setOnSaved(Consumer<AppConfig> callback) { this.onSaved = callback; }

    // ----------------------------------------------------------------
    // Layout
    // ----------------------------------------------------------------

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(ThemeManager.BG_SECONDARY);
        root.setBorder(new EmptyBorder(16, 20, 16, 20));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(ThemeManager.BG_SECONDARY);
        tabs.setForeground(ThemeManager.TEXT_PRIMARY);
        tabs.addTab("AI Provider", buildAITab());
        tabs.addTab("Appearance",  buildAppearanceTab());

        root.add(tabs, BorderLayout.CENTER);
        root.add(buildButtons(), BorderLayout.SOUTH);

        return root;
    }

    private JPanel buildAITab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(ThemeManager.BG_SECONDARY);
        p.setBorder(new EmptyBorder(12, 8, 12, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 4, 5, 4);
        c.anchor = GridBagConstraints.WEST;

        addRow(p, c, 0, "Provider:",  providerBox);
        addRow(p, c, 1, "API Key:",   apiKeyField);
        addRow(p, c, 2, "Model:",     modelField);
        addRow(p, c, 3, "Dimension:", dimensionSpinner);

        // Style all inputs
        for (JComponent comp : new JComponent[]{apiKeyField, modelField}) {
            comp.setBackground(ThemeManager.BG_SURFACE);
            comp.setForeground(ThemeManager.TEXT_PRIMARY);
        }
        providerBox.setBackground(ThemeManager.BG_SURFACE);

        return p;
    }

    private JPanel buildAppearanceTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(ThemeManager.BG_SECONDARY);
        p.setBorder(new EmptyBorder(12, 8, 12, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 4, 5, 4);
        c.anchor = GridBagConstraints.WEST;

        addRow(p, c, 0, "Learning style:", styleBox);

        // Description label
        c.gridx = 0; c.gridy = 1; c.gridwidth = 2;
        JLabel desc = new JLabel("<html><i>" + getStyleDesc() + "</i></html>");
        desc.setFont(ThemeManager.FONT_SMALL);
        desc.setForeground(ThemeManager.TEXT_SECONDARY);
        p.add(desc, c);

        styleBox.addActionListener(e ->
            desc.setText("<html><i>" + getStyleDesc() + "</i></html>")
        );

        return p;
    }

    private String getStyleDesc() {
        Object sel = styleBox.getSelectedItem();
        return sel instanceof LearningStyle ls ? ls.getDescription() : "";
    }

    private JPanel buildButtons() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        row.setBackground(ThemeManager.BG_SECONDARY);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());

        JButton save = new JButton("Save");
        save.addActionListener(e -> save());

        row.add(cancel);
        row.add(save);
        return row;
    }

    private static void addRow(JPanel p, GridBagConstraints c, int row,
                               String label, JComponent comp) {
        c.gridx = 0; c.gridy = row; c.gridwidth = 1;
        JLabel lbl = new JLabel(label);
        lbl.setFont(ThemeManager.FONT_BODY);
        lbl.setForeground(ThemeManager.TEXT_SECONDARY);
        p.add(lbl, c);
        c.gridx = 1;
        p.add(comp, c);
    }

    // ----------------------------------------------------------------
    // Data binding
    // ----------------------------------------------------------------

    private void populateFromConfig(AppConfig cfg) {
        providerBox.setSelectedItem(cfg.getEmbedding().provider);
        // Show decrypted key (or empty if null)
        String decrypted = ConfigStore.decryptApiKey(cfg.getEmbedding().apiKey);
        apiKeyField.setText(decrypted != null ? decrypted : "");
        modelField.setText(cfg.getEmbedding().model);
        dimensionSpinner.setValue(cfg.getEmbedding().dimension);

        try {
            LearningStyle style = LearningStyle.valueOf(cfg.getLearningStyle());
            styleBox.setSelectedItem(style);
        } catch (IllegalArgumentException ignored) {
            styleBox.setSelectedIndex(0);
        }
    }

    private void save() {
        // Update embedding config
        current.getEmbedding().provider  = (String) providerBox.getSelectedItem();
        current.getEmbedding().model     = modelField.getText().trim();
        current.getEmbedding().dimension = (Integer) dimensionSpinner.getValue();

        // Encrypt API key if non-empty
        String rawKey = new String(apiKeyField.getPassword()).trim();
        if (!rawKey.isEmpty()) {
            current.getEmbedding().apiKey = ConfigStore.encryptApiKey(rawKey);
        }

        // Learning style
        LearningStyle style = (LearningStyle) styleBox.getSelectedItem();
        if (style != null) current.setLearningStyle(style.name());

        try {
            configStore.save(current);
            if (onSaved != null) onSaved.accept(current);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to save settings: " + ex.getMessage(),
                "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
