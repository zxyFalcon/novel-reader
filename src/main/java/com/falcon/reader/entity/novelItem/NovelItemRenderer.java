package com.falcon.reader.entity.novelItem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class NovelItemRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
            boolean cellHasFocus) {
        NovelItem novelItem = (NovelItem)value;

        JPanel panel = getPanelForItem(novelItem);
        // 设置选中状态的背景颜色
        if (isSelected) {
            panel.setOpaque(true); // 设置透明
            panel.setBackground(Color.GRAY); // 选中时的背景色
        }

        return panel;
    }

    public JPanel getPanelForItem(NovelItem novelItem) {
        JPanel panel = new JPanel();
        panel.setBorder(new EmptyBorder(3, 10, 3, 10));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false); // 设置透明
        JLabel nameLabel = new JLabel(novelItem.getFileName());
        JLabel noteLabel = new JLabel(novelItem.getFilePath());
        nameLabel.setForeground(Color.WHITE);
        noteLabel.setForeground(Color.LIGHT_GRAY);
        nameLabel.setFont(new Font("Serif", Font.PLAIN, 15));
        noteLabel.setFont(new Font("Serif", Font.ITALIC, 11)); // 设置备注字体
        panel.add(nameLabel);
        panel.add(noteLabel);
        return panel;
    }

    public Dimension getPreferredSizeForItem(NovelItem novelItem) {
        JPanel panel = getPanelForItem(novelItem);
        return panel.getPreferredSize(); // 获取 JPanel 的首选尺寸
    }
}
