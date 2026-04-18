package com.docgalaxy.ui.components;

import com.docgalaxy.ui.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Search bar with "Search notes…" placeholder text.
 * Fires the onSearch callback 300 ms after the last keystroke (debounced).
 * Fires onClear when the field is cleared.
 */
public class SearchPanel extends JPanel {

    private final JTextField    searchField;
    private       Consumer<String> onSearch;
    private       Runnable         onClear;
    private       Timer            debounce;

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, 45);
    }

    public SearchPanel() {
        setLayout(new BorderLayout());
        setBackground(ThemeManager.BG_SECONDARY);
        setBorder(new EmptyBorder(6, 8, 6, 8));

        searchField = buildField();
        add(searchField, BorderLayout.CENTER);

        debounce = new Timer(300, e -> fireSearch());
        debounce.setRepeats(false);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { resetDebounce(); }
            @Override public void removeUpdate(DocumentEvent e)  { resetDebounce(); }
            @Override public void changedUpdate(DocumentEvent e) { resetDebounce(); }
        });
    }

    // ----------------------------------------------------------------
    // Callbacks
    // ----------------------------------------------------------------

    public void setOnSearch(Consumer<String> callback) { this.onSearch = callback; }
    public void setOnClear(Runnable callback)          { this.onClear  = callback; }

    // ----------------------------------------------------------------
    // Programmatic control
    // ----------------------------------------------------------------

    public String getQuery() { return searchField.getText().trim(); }

    public void clear() {
        searchField.setText("");
    }

    // ----------------------------------------------------------------
    // Internal
    // ----------------------------------------------------------------

    private void resetDebounce() {
        debounce.restart();
    }

    private void fireSearch() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            if (onClear != null) onClear.run();
        } else {
            if (onSearch != null) onSearch.accept(text);
        }
    }

    private JTextField buildField() {
        // Placeholder via a text field that shows hint when empty
        JTextField field = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(ThemeManager.TEXT_SECONDARY);
                    g2.setFont(ThemeManager.FONT_BODY);
                    FontMetrics fm = g2.getFontMetrics();
                    int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString("Search notes…", getInsets().left + 2, y);
                }
            }
        };

        field.setFont(ThemeManager.FONT_BODY);
        field.setForeground(ThemeManager.TEXT_PRIMARY);
        field.setBackground(ThemeManager.BG_SURFACE);
        field.setCaretColor(ThemeManager.TEXT_ACCENT);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeManager.BG_SURFACE, 1),
            new EmptyBorder(5, 8, 5, 8)
        ));
        return field;
    }
}
