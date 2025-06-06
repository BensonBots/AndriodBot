package newgame;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Map;

public class ActionCellRenderer extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
    private final Main main;
    private final JTable table;
    private final Map<Integer, JPanel> panelCache = new HashMap<>();

    public ActionCellRenderer(Main main, JTable table) {
        this.main = main;
        this.table = table;
    }

    private JPanel createPanel(int row) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        panel.add(createActionButton("Start", row, e -> main.startInstance(getInstanceIndex(row))));
        panel.add(createActionButton("Stop", row, e -> main.stopInstance(getInstanceIndex(row))));
        panel.add(createActionButton("Modules", row, e -> {
            MemuInstance inst = main.getInstanceByIndex(getInstanceIndex(row));
            if (inst != null) main.showModulesDialog(inst);
        }));

        return panel;
    }

    private JButton createActionButton(String text, int row, ActionListener action) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setMargin(new Insets(2, 6, 2, 6));
        btn.setPreferredSize(new Dimension(75, 28));
        btn.addActionListener(action);
        return btn;
    }

    private int getInstanceIndex(int row) {
        return (int) table.getModel().getValueAt(table.convertRowIndexToModel(row), 0);
    }

    @Override public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        return panelCache.computeIfAbsent(row, this::createPanel);
    }

    @Override public Component getTableCellEditorComponent(JTable table, Object value,
            boolean isSelected, int row, int column) {
        return panelCache.computeIfAbsent(row, this::createPanel);
    }

    @Override public Object getCellEditorValue() { return null; }
}