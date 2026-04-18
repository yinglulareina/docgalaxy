package com.docgalaxy.ui.components;

import com.docgalaxy.ai.navigator.LearningStyle;
import com.docgalaxy.ai.navigator.NavigationResult;
import com.docgalaxy.ai.navigator.NavigatorService;
import com.docgalaxy.ai.navigator.RouteStep;
import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.ui.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
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

    private static final Color USER_BG = ThemeManager.CHAT_USER_BG;
    private static final Color AI_BG   = ThemeManager.CHAT_AI_BG;

    private static final int HEADER_HEIGHT = 34;

    private final JPanel      chatBox;
    private final JScrollPane chatScroll;
    private       JTextArea   inputArea;
    private final JButton     collapseBtn;
    private final JPanel      bodyPanel;

    private boolean            collapsed       = false;
    private NavigatorService   navigatorService;
    private KnowledgeBase      knowledgeBase;
    private LearningStyle      learningStyle   = LearningStyle.OVERVIEW_FIRST;
    private Consumer<Set<String>>  onHighlight;   // callback → highlight note IDs on canvas
    private Consumer<List<String>> onShowRoute;   // callback → draw ordered route on canvas

    public NavigatorPanel() {
        setLayout(new BorderLayout());
        setBackground(ThemeManager.BG_SECONDARY);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

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

    public void setOnShowRoute(Consumer<List<String>> callback) {
        this.onShowRoute = callback;
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
        setMaximumSize(new Dimension(Integer.MAX_VALUE,
            collapsed ? HEADER_HEIGHT : Integer.MAX_VALUE));
        revalidate();
    }

    // ----------------------------------------------------------------
    // Input row
    // ----------------------------------------------------------------

    private JPanel buildInputRow() {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ThemeManager.BG_SECONDARY);
        row.setBorder(new EmptyBorder(4, 8, 8, 8));

        inputArea = new JTextArea(1, 0);
        inputArea.setFont(ThemeManager.FONT_BODY);
        inputArea.setForeground(ThemeManager.TEXT_PRIMARY);
        inputArea.setBackground(ThemeManager.BG_SURFACE);
        inputArea.setCaretColor(ThemeManager.TEXT_ACCENT);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(new EmptyBorder(4, 8, 4, 8));
        inputArea.putClientProperty("JTextField.placeholderText",
                "e.g. Help me plan a study path for...");

        // Enter submits; Shift+Enter inserts newline
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    submitQuery();
                }
            }
        });

        // Auto-grow 1→3 rows as content grows
        inputArea.getDocument().addDocumentListener(new DocumentListener() {
            private void adjustRows() {
                SwingUtilities.invokeLater(() -> {
                    int lines = Math.max(1, Math.min(3, inputArea.getLineCount()));
                    if (inputArea.getRows() != lines) {
                        inputArea.setRows(lines);
                        Container p = row.getParent();
                        while (p != null) { p.revalidate(); p = p.getParent(); }
                    }
                });
            }
            @Override public void insertUpdate(DocumentEvent e)  { adjustRows(); }
            @Override public void removeUpdate(DocumentEvent e)  { adjustRows(); }
            @Override public void changedUpdate(DocumentEvent e) { adjustRows(); }
        });

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createLineBorder(ThemeManager.BG_SURFACE));
        inputScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        inputScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        inputScroll.getViewport().setBackground(ThemeManager.BG_SURFACE);

        JButton askBtn = new JButton("Ask");
        askBtn.setFont(ThemeManager.FONT_BODY);
        askBtn.setForeground(ThemeManager.TEXT_PRIMARY);
        askBtn.setBackground(ThemeManager.BG_SURFACE);
        askBtn.setBorder(new EmptyBorder(4, 10, 4, 10));
        askBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        askBtn.addActionListener(e -> submitQuery());

        row.add(inputScroll, BorderLayout.CENTER);
        row.add(askBtn,      BorderLayout.EAST);

        return row;
    }

    // ----------------------------------------------------------------
    // Query handling
    // ----------------------------------------------------------------

    private void submitQuery() {
        String query = inputArea.getText().trim();
        if (query.isEmpty()) return;
        inputArea.setText("");

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
        SwingUtilities.invokeLater(() -> {
            List<RouteStep> sorted = result.getRoute().stream()
                .sorted(java.util.Comparator.comparingInt(RouteStep::getOrder))
                .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder(result.getSummary());

            if (!sorted.isEmpty()) {
                sb.append("\n\nStudy Path:");
                for (int i = 0; i < sorted.size(); i++) {
                    RouteStep step = sorted.get(i);
                    String fileName = step.getNoteId();
                    if (knowledgeBase != null) {
                        com.docgalaxy.model.Note note = knowledgeBase.getNote(step.getNoteId());
                        if (note != null) fileName = note.getFileName();
                    }
                    String reason = (step.getReason() != null && !step.getReason().isBlank())
                            ? step.getReason() : "Relevant to your query";
                    sb.append("\n").append(i + 1).append(". ").append(fileName)
                      .append(" \u2014 ").append(reason);
                }
            }

            if (result.getEstimatedTime() != null && !result.getEstimatedTime().isBlank()) {
                sb.append("\n\nEstimated time: ").append(result.getEstimatedTime());
            }

            addMessage(sb.toString(), false);

            List<String> routeIds = sorted.stream().map(RouteStep::getNoteId).collect(Collectors.toList());
            Set<String> ids = Set.copyOf(routeIds);
            if (onHighlight != null) onHighlight.accept(ids);
            if (onShowRoute != null) onShowRoute.accept(routeIds);
        });
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

        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        wrapper.add(area);
        return wrapper;
    }
}
