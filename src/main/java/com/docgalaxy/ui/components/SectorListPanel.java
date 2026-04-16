package com.docgalaxy.ui.components;

import com.docgalaxy.model.KnowledgeBase;
import com.docgalaxy.model.Sector;
import com.docgalaxy.ui.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Panel that lists all sectors (topic clusters) in the knowledge base.
 * Each row shows a colored dot, the sector name, and its note count.
 * Clicking a sector fires the onSectorSelected callback.
 */
public class SectorListPanel extends JPanel {

    private final DefaultListModel<Sector> listModel = new DefaultListModel<>();
    private final JList<Sector>            list;
    private Consumer<Sector> onSectorSelected;

    public SectorListPanel() {
        setLayout(new BorderLayout());
        setBackground(ThemeManager.BG_SECONDARY);

        // Header label
        JLabel header = new JLabel("SECTORS");
        header.setFont(ThemeManager.FONT_SMALL);
        header.setForeground(ThemeManager.TEXT_SECONDARY);
        header.setBorder(new EmptyBorder(10, 10, 4, 8));
        add(header, BorderLayout.NORTH);

        // List
        list = new JList<>(listModel);
        list.setCellRenderer(new SectorCellRenderer());
        list.setBackground(ThemeManager.BG_SECONDARY);
        list.setSelectionBackground(ThemeManager.BG_SURFACE);
        list.setSelectionForeground(ThemeManager.TEXT_PRIMARY);
        list.setFixedCellHeight(32);

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && onSectorSelected != null) {
                Sector selected = list.getSelectedValue();
                if (selected != null) onSectorSelected.accept(selected);
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setBackground(ThemeManager.BG_SECONDARY);
        scroll.getViewport().setBackground(ThemeManager.BG_SECONDARY);
        scroll.setPreferredSize(new Dimension(0, 120));
        add(scroll, BorderLayout.CENTER);
    }

    // ----------------------------------------------------------------
    // Callback
    // ----------------------------------------------------------------

    public void setOnSectorSelected(Consumer<Sector> callback) {
        this.onSectorSelected = callback;
    }

    // ----------------------------------------------------------------
    // Data refresh
    // ----------------------------------------------------------------

    public void refresh(KnowledgeBase kb) {
        listModel.clear();
        if (kb == null) return;
        for (Sector s : kb.getSectors()) {
            listModel.addElement(s);
        }
    }

    // ----------------------------------------------------------------
    // Cell renderer
    // ----------------------------------------------------------------

    private static class SectorCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus);

            if (value instanceof Sector sector) {
                label.setText(sector.getLabel());
                label.setFont(ThemeManager.FONT_BODY);
                label.setForeground(isSelected ? ThemeManager.TEXT_PRIMARY : ThemeManager.TEXT_PRIMARY);
                label.setBackground(isSelected ? ThemeManager.BG_SURFACE : ThemeManager.BG_SECONDARY);
                label.setBorder(new EmptyBorder(4, 10, 4, 8));

                // Colored dot icon
                Color dot = ThemeManager.getSectorColor(index);
                label.setIcon(new DotIcon(dot));
            }
            return label;
        }
    }

    /** Small filled circle icon. */
    private static class DotIcon implements Icon {
        private final Color color;
        DotIcon(Color color) { this.color = color; }

        @Override public int getIconWidth()  { return 10; }
        @Override public int getIconHeight() { return 10; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x, y + 2, 8, 8);
            g2.dispose();
        }
    }
}
