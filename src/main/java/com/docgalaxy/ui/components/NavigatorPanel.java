package com.docgalaxy.ui.components;

import com.docgalaxy.ai.navigator.LearningStyle;
import com.docgalaxy.ai.navigator.NavigationResult;
import com.docgalaxy.ai.navigator.NavigatorService;
import com.docgalaxy.ai.navigator.RouteStep;
import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.ui.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Collapsible AI navigator panel (bottom of the sidebar).
 *
 * ┌──────────────────────┐
 * │ ▾ AI NAVIGATOR       │  ← toggle header
 * ├──────────────────────┤
 * │  [chat message list] │  (scrollable)
 * ├──────────────────────┤
 * │ [input field] [Ask]  │
 * └──────────────────────┘
 *
 * Requires a NavigatorService to be set before queries can be made.
 * If no service is set, queries fail gracefully with a message.
 */
public class NavigatorPanel extends JPanel {

    private static final Color USER_BG = new Color(0x1F, 0x3B, 0x5A);
    private static final Color AI_BG   = new Color(0x2A, 0x1F, 0x3B);

    private final JPanel     chatBox;
    private final JScrollPane chatScroll;
    private JTextField  inputField;
    private final JButton     collapseBtn;
    private final JPanel      bodyPanel;

    private boolean            collapsed       = false;
    private NavigatorService   navigatorService;
    private KnowledgeBase      knowledgeBase;
    private LearningStyle      learningStyle   = LearningStyle.OVERVIEW_FIRST;
    private Consumer<Set<String>> onHighlight;   // callback → highlight note IDs on canvas

    public NavigatorPanel() {
        setLayout(new BorderLayout());
        setBackground(ThemeManager.BG_SECONDARY);

        // ---- Header ----
        JPanel header = buildHeader();
        add(header, BorderLayout.NORTH);

        // ---- Body ----
        bodyPanel = new JPanel(new BorderLayout());
        bodyPanel.setBackground(ThemeManager.BG_SECONDARY);

        chatBox = new JPanel();
        chatBox.setLayout(new BoxLayout(chatBox, BoxLayout.Y_AXIS));
        chatBox.setBackground(ThemeManager.BG_SECONDARY);
        chatBox.setBorder(new EmptyBorder(4, 0, 4, 0));

        chatScroll = new JScrollPane(chatBox);
        chatScroll.setBorder(null);
        chatScroll.setPreferredSize(new Dimension(0, 160));
        chatScroll.getViewport().setBackground(ThemeManager.BG_SECONDARY);
        chatScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel inputRow = buildInputRow();

        bodyPanel.add(chatScroll, BorderLayout.CENTER);
        bodyPanel.add(inputRow,   BorderLayout.SOUTH);

        add(bodyPanel, BorderLayout.CENTER);

        collapseBtn = header.getComponent(header.getComponentCount() - 1) instanceof JButton b ? b : null;
    }

    // ----------------------------------------------------------------
    // Wiring
    // ----------------------------------------------------------------

    public void setNavigatorService(NavigatorService service) {
        this.navigatorService = service;
    }

    public void setKnowledgeBase(KnowledgeBase kb) {
        this.knowledgeBase = kb;
    }

    public void setLearningStyle(LearningStyle style) {
        this.learningStyle = style;
    }

    public void setOnHighlight(Consumer<Set<String>> callback) {
        this.onHighlight = callback;
    }

    // ----------------------------------------------------------------
    // Header (collapse toggle)
    // ----------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ThemeManager.BG_SURFACE);
        header.setBorder(new EmptyBorder(6, 10, 6, 8));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel title = new JLabel("▾ AI NAVIGATOR");
        title.setFont(ThemeManager.FONT_SMALL);
        title.setForeground(ThemeManager.TEXT_SECONDARY);

        JButton toggle = new JButton("−");
        toggle.setFont(ThemeManager.FONT_SMALL);
        toggle.setForeground(ThemeManager.TEXT_SECONDARY);
        toggle.setBackground(null);
        toggle.setOpaque(false);
        toggle.setBorder(null);
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.addActionListener(e -> toggleCollapse(title, toggle));

        header.add(title,  BorderLayout.CENTER);
        header.add(toggle, BorderLayout.EAST);

        // Click the whole header to toggle
        header.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                toggleCollapse(title, toggle);
            }
        });

        return header;
    }

    private void toggleCollapse(JLabel title, JButton toggle) {
        collapsed = !collapsed;
        bodyPanel.setVisible(!collapsed);
        title.setText(collapsed ? "▸ AI NAVIGATOR" : "▾ AI NAVIGATOR");
        toggle.setText(collapsed ? "+" : "−");
        revalidate();
    }

    // ----------------------------------------------------------------
    // Input row
    // ----------------------------------------------------------------

    private JPanel buildInputRow() {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ThemeManager.BG_SECONDARY);
        row.setBorder(new EmptyBorder(4, 8, 8, 8));

        inputField = new JTextField();
        inputField.setFont(ThemeManager.FONT_BODY);
        inputField.setForeground(ThemeManager.TEXT_PRIMARY);
        inputField.setBackground(ThemeManager.BG_SURFACE);
        inputField.setCaretColor(ThemeManager.TEXT_ACCENT);
        inputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeManager.BG_SURFACE),
            new EmptyBorder(4, 8, 4, 8)
        ));
        inputField.addActionListener(e -> submitQuery());

        JButton askBtn = new JButton("Ask");
        askBtn.setFont(ThemeManager.FONT_BODY);
        askBtn.setForeground(ThemeManager.TEXT_PRIMARY);
        askBtn.setBackground(ThemeManager.BG_SURFACE);
        askBtn.setBorder(new EmptyBorder(4, 10, 4, 10));
        askBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        askBtn.addActionListener(e -> submitQuery());

        row.add(inputField, BorderLayout.CENTER);
        row.add(askBtn,     BorderLayout.EAST);

        return row;
    }

    // ----------------------------------------------------------------
    // Query handling
    // ----------------------------------------------------------------

    private void submitQuery() {
        String query = inputField.getText().trim();
        if (query.isEmpty()) return;
        inputField.setText("");

        addMessage(query, true);

        if (navigatorService == null) {
            addMessage("AI navigator is not configured yet. " +
                       "Please set an API key in Settings.", false);
            return;
        }

        // Run query on a background thread
        SwingWorker<NavigationResult, Void> worker = new SwingWorker<>() {
            @Override
            protected NavigationResult doInBackground() throws Exception {
                return navigatorService.navigate(query, learningStyle);
            }

            @Override
            protected void done() {
                try {
                    NavigationResult result = get();
                    displayResult(result);
                } catch (Exception ex) {
                    addMessage("Navigation failed: " + ex.getMessage(), false);
                }
            }
        };
        worker.execute();
    }

    private void displayResult(NavigationResult result) {
        addMessage(result.getSummary(), false);

        // Highlight notes on canvas
        Set<String> ids = result.getRoute().stream()
            .map(RouteStep::getNoteId)
            .collect(Collectors.toSet());
        if (onHighlight != null) onHighlight.accept(ids);
    }

    // ----------------------------------------------------------------
    // Chat message rendering
    // ----------------------------------------------------------------

    private void addMessage(String text, boolean isUser) {
        JPanel bubble = buildBubble(text, isUser);
        chatBox.add(bubble);
        chatBox.add(Box.createVerticalStrut(4));
        chatBox.revalidate();

        // Scroll to bottom
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = chatScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private JPanel buildBubble(String text, boolean isUser) {
        JTextArea area = new JTextArea(text);
        area.setFont(ThemeManager.FONT_BODY);
        area.setForeground(ThemeManager.TEXT_PRIMARY);
        area.setBackground(isUser ? USER_BG : AI_BG);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(5, 8, 5, 8));

        JPanel wrapper = new JPanel(new FlowLayout(isUser ? FlowLayout.RIGHT : FlowLayout.LEFT, 4, 2));
        wrapper.setBackground(ThemeManager.BG_SECONDARY);
        wrapper.setOpaque(false);

        area.setMaximumSize(new Dimension(210, Integer.MAX_VALUE));
        wrapper.add(area);
        return wrapper;
    }
}
