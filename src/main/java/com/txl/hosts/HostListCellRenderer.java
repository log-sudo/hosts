package com.txl.hosts;
import javax.swing.*;
import java.awt.*;

public class HostListCellRenderer extends DefaultListCellRenderer {
    private final String activeHostName;

    public HostListCellRenderer(String activeHostName) {
        this.activeHostName = activeHostName;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        // Maintain default foreground color
        component.setForeground(list.getForeground());

        // Apply custom background color for the active item
        if (value != null && value.equals(activeHostName)) {
            if (isSelected) {
                component.setBackground(list.getSelectionBackground()); // Custom color for selected active item
            } else {
                component.setBackground(new Color(0x4F4B41));  // Custom color for active item when not selected
            }
        } else {
            if (isSelected) {
                component.setBackground(list.getSelectionBackground()); // Default selection background
            } else {
                component.setBackground(list.getBackground()); // Default background for non-active items
            }
        }

        return component;
    }
}