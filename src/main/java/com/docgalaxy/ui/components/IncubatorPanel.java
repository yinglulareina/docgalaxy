package com.docgalaxy.ui.components;

import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.model.Note;
import com.docgalaxy.model.NoteStatus;
import com.docgalaxy.ui.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Panel showing INCUBATOR notes – files that exist on disk but whose content
 * is too short (< MIN_CONTENT_LENGTH chars) to be indexed and embedded.
 *
 * These notes are tracked in the KnowledgeBase with NoteStatus.INCUBATOR.
 * Clicking a note fires the onNoteSelected callback.
 */
public class IncubatorPanel extends JPanel {

    private final DefaultListModel<Note> listModel = new DefaultListModel<>();
    private final JList<Note>            list;
    private final JScrollPane            scroll;
    private       Consumer<Note>         onNoteSelected;

    public IncubatorPanel() {
        setLayout(new BorderLayout());
        setBackground(ThemeManager.BG_SECONDARY);

        // Header
        JLabel header = new JLabel("INCUBATOR");
        header.setFont(ThemeManager.FONT_SMALL);
        header.setForeground(ThemeManager.TEXT_SECONDARY);
        header.setBorder(new EmptyBorder(10, 10, 4, 8));
        add(header, BorderLayout.NORTH);

        // List
        list = new JList<>(listModel);
        list.setCellRenderer(new NoteCellRenderer());
        list.setBackground(ThemeManager.BG_SECONDARY);
        list.setSelectionBackground(ThemeManager.BG_SURFACE);
        list.setSelectionForeground(ThemeManager.TEXT_PRIMARY);
        list.setFixedCellHeight(28);

        list.setVisibleRowCount(0);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && onNoteSelected != null) {
                Note selected = list.getSelectedValue();
                if (selected != null) onNoteSelected.accept(selected);
            }
        });

        scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setBackground(ThemeManager.BG_SECONDARY);
        scroll.getViewport().setBackground(ThemeManager.BG_SECONDARY);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scroll.setVisible(false);   // hidden until there are incubator notes
        add(scroll, BorderLayout.CENTER);
    }

    // ----------------------------------------------------------------
    // Callback
    // ----------------------------------------------------------------

    public void setOnNoteSelected(Consumer<Note> callback) {
        this.onNoteSelected = callback;
    }

    // ----------------------------------------------------------------
    // Data refresh
    // ----------------------------------------------------------------

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }

    public void refresh(KnowledgeBase kb) {
        listModel.clear();
        if (kb != null) {
            kb.getAllNotes().stream()
                .filter(n -> n.getStatus() == NoteStatus.INCUBATOR)
                .forEach(listModel::addElement);
        }
        int count = listModel.size();
        list.setVisibleRowCount(count);
        scroll.setVisible(count > 0);
        revalidate();
    }

    /** Count of incubator notes currently shown. */
    public int getIncubatorCount() { return listModel.size(); }

    // ----------------------------------------------------------------
    // Cell renderer
    // ----------------------------------------------------------------

    private static class NoteCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus);

            if (value instanceof Note note) {
                label.setText(note.getFileName());
                label.setFont(ThemeManager.FONT_BODY);
                label.setForeground(isSelected ? ThemeManager.TEXT_PRIMARY : ThemeManager.TEXT_SECONDARY);
                label.setBackground(isSelected ? ThemeManager.BG_SURFACE : ThemeManager.BG_SECONDARY);
                label.setBorder(new EmptyBorder(2, 14, 2, 8));
                label.setToolTipText("Too short to index – add more content to embed this note");
            }
            return label;
        }
    }
}
