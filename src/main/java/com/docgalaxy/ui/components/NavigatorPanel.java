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
 */
public class NavigatorPanel extends JPanel {

    private static final int    BUBBLE_CORNER  = 8;
    private static final int    BUBBLE_INDENT  = 36; // px indent on the non-aligned side
    private static final String PLACEHOLDER    = "e.g. Help me plan a study path for...";
    private static final int    HEADER_HEIGHT  = 34;

    private final JPanel      chatBox;
    private final JScrollPane chatScroll;
    private       JTextArea   inputArea;
    private final JPanel      bodyPanel;

    private boolean             collapsed     = false;
    private NavigatorService    navigatorService;
    private KnowledgeBase       knowledgeBase;
    private LearningStyle       learningStyle = LearningStyle.OVERVIEW_FIRST;
    private Consumer<Set<String>>   onHighlight;
    private Consumer<List<String>>  onShowRoute;

    public NavigatorPanel() {
        setLayout(new BorderLayout());
        setBackground(ThemeManager.BG_SECONDARY);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel header = buildHeader();
        add(header, BorderLayout.NORTH);

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

        bodyPanel.add(chatScroll,     BorderLayout.CENTER);
        bodyPanel.add(buildInputRow(), BorderLayout.SOUTH);
        add(bodyPanel, BorderLayout.CENTER);
    }

    // ----------------------------------------------------------------
    // Wiring
    // ----------------------------------------------------------------

    public void setNavigatorService(NavigatorService service) { this.navigatorService = service; }
    public void setKnowledgeBase(KnowledgeBase kb)            { this.knowledgeBase = kb; }
    public void setLearningStyle(LearningStyle style)         { this.learningStyle = style; }
    public void setOnHighlight(Consumer<Set<String>> cb)      { this.onHighlight = cb; }
    public void setOnShowRoute(Consumer<List<String>> cb)     { this.onShowRoute = cb; }

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
        header.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
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

        // JTextArea with manual placeholder (FlatLaf doesn't support JTextArea placeholders)
        inputArea = new JTextArea(1, 0) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(ThemeManager.TEXT_SECONDARY);
                    g2.setFont(getFont());
                    Insets ins = getInsets();
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(PLACEHOLDER, ins.left, ins.top + fm.getAscent());
                    g2.dispose();
                }
            }
        };
        inputArea.setFont(ThemeManager.FONT_BODY);
        inputArea.setForeground(ThemeManager.TEXT_PRIMARY);
        inputArea.setBackground(ThemeManager.BG_SURFACE);
        inputArea.setCaretColor(ThemeManager.TEXT_ACCENT);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(new EmptyBorder(4, 8, 4, 8));
        inputArea.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { inputArea.repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent e)   { inputArea.repaint(); }
        });

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

        // Auto-grow 1→3 rows
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

        // inputScroll fills CENTER → stretches with sidebar width automatically
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

        // Add thinking bubble and start animation
        JTextArea thinkingArea = addAiBubble("Thinking.");
        String[] frames = {"Thinking.", "Thinking..", "Thinking..."};
        int[] idx = {0};
        Timer thinkTimer = new Timer(500, null);
        thinkTimer.addActionListener(e -> {
            idx[0] = (idx[0] + 1) % frames.length;
            thinkingArea.setText(frames[idx[0]]);
        });
        thinkTimer.start();

        SwingWorker<NavigationResult, Void> worker = new SwingWorker<>() {
            @Override
            protected NavigationResult doInBackground() throws Exception {
                return navigatorService.navigate(query, learningStyle);
            }

            @Override
            protected void done() {
                thinkTimer.stop();
                try {
                    NavigationResult result = get();
                    SwingUtilities.invokeLater(() -> {
                        thinkingArea.setText(buildFormattedMessage(result));
                        scrollToBottom();

                        List<String> routeIds = result.getRoute().stream()
                            .sorted(java.util.Comparator.comparingInt(RouteStep::getOrder))
                            .map(RouteStep::getNoteId)
                            .collect(Collectors.toList());
                        Set<String> ids = Set.copyOf(routeIds);
                        System.out.println("[NAV] highlighting " + ids.size() + " stars, route " + routeIds.size() + " steps");
                        if (onHighlight != null) onHighlight.accept(ids);
                        if (onShowRoute  != null) onShowRoute.accept(routeIds);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> thinkingArea.setText(
                            "Navigation failed: " + ex.getMessage()));
                }
            }
        };
        worker.execute();
    }

    private String buildFormattedMessage(NavigationResult result) {
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

        return sb.toString();
    }

    // ----------------------------------------------------------------
    // Chat message rendering
    // ----------------------------------------------------------------

    private void addMessage(String text, boolean isUser) {
        addAiBubbleOrUser(text, isUser);
    }

    /**
     * Adds a user or static AI bubble; returns the JTextArea for static content.
     */
    private JTextArea addAiBubbleOrUser(String text, boolean isUser) {
        JTextArea area = createBubbleArea(isUser);
        area.setText(text);
        chatBox.add(buildBubbleWrapper(area, isUser));
        chatBox.add(Box.createVerticalStrut(4));
        chatBox.revalidate();
        scrollToBottom();
        return area;
    }

    /**
     * Adds an AI bubble with initial text; returns the JTextArea so the caller
     * can update it later (e.g. replace "Thinking..." with the real reply).
     */
    private JTextArea addAiBubble(String initialText) {
        return addAiBubbleOrUser(initialText, false);
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = chatScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    // ----------------------------------------------------------------
    // Bubble construction
    // ----------------------------------------------------------------

    private JTextArea createBubbleArea(boolean isUser) {
        JTextArea area = new JTextArea();
        area.setFont(ThemeManager.FONT_BODY);
        area.setForeground(isUser ? ThemeManager.CHAT_USER_TEXT : ThemeManager.CHAT_AI_TEXT);
        area.setBackground(isUser ? ThemeManager.CHAT_USER_BG  : ThemeManager.CHAT_AI_BG);
        area.setOpaque(false);   // let rounded panel paint background
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(6, 10, 6, 10));
        return area;
    }

    /** Rounded colored panel containing the text area. */
    private JPanel buildRoundedBubble(JTextArea area, boolean isUser) {
        Color bg = isUser ? ThemeManager.CHAT_USER_BG : ThemeManager.CHAT_AI_BG;
        JPanel bubble = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), BUBBLE_CORNER, BUBBLE_CORNER);
                g2.dispose();
            }
        };
        bubble.setOpaque(false);
        bubble.setBackground(bg);
        bubble.add(area, BorderLayout.CENTER);
        return bubble;
    }

    /**
     * Full-width wrapper that:
     * – fills the chatBox width (BoxLayout child, aligns LEFT)
     * – indents user bubbles from the left / AI bubbles from the right
     * – lets text wrap properly as the sidebar resizes
     */
    private JPanel buildBubbleWrapper(JTextArea area, boolean isUser) {
        JPanel bubble = buildRoundedBubble(area, isUser);

        // Fixed-width spacer on the non-primary side
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        spacer.setPreferredSize(new Dimension(BUBBLE_INDENT, 0));

        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setBorder(new EmptyBorder(0, 8, 0, 8));

        if (isUser) {
            wrapper.add(spacer, BorderLayout.WEST);   // indent from left → bubble on right
            wrapper.add(bubble, BorderLayout.CENTER);
        } else {
            wrapper.add(bubble, BorderLayout.CENTER);
            wrapper.add(spacer, BorderLayout.EAST);   // indent from right → bubble on left
        }
        return wrapper;
    }
}
