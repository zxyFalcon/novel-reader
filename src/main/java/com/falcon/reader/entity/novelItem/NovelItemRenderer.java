package com.falcon.reader.entity.novelItem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 项列表配置
 * @author zxy
 * @date 2024/10/21
 */
public class NovelItemRenderer extends DefaultListCellRenderer {
    private static final Color NORMAL_NAME_COLOR = Color.WHITE;
    private static final Color NORMAL_PROGRESS_COLOR = new Color(180, 220, 180);
    private static final Color MISSING_NAME_COLOR = new Color(150, 150, 150);
    private static final Color MISSING_PROGRESS_COLOR = new Color(135, 135, 135);

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
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.setBorder(new EmptyBorder(6, 10, 6, 10));
        panel.setOpaque(false); // 设置透明
        JLabel nameLabel = new JLabel(novelItem.getFileName());
        JPanel progressPanel = createProgressPanel(novelItem);
        nameLabel.setForeground(novelItem.isFileExists() ? NORMAL_NAME_COLOR : MISSING_NAME_COLOR);
        nameLabel.setFont(new Font("Serif", Font.PLAIN, 15));
        progressPanel.setPreferredSize(new Dimension(48, 16));
        panel.add(progressPanel, BorderLayout.WEST);
        panel.add(nameLabel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createProgressPanel(NovelItem novelItem) {
        JPanel progressPanel = new JPanel(new GridBagLayout());
        progressPanel.setOpaque(false);
        progressPanel.setBorder(new EmptyBorder(1, 0, 0, 0));

        JLabel progressLabel = new JLabel(novelItem.getProgressPercentText());
        progressLabel.setForeground(novelItem.isFileExists() ? NORMAL_PROGRESS_COLOR : MISSING_PROGRESS_COLOR);
        progressLabel.setFont(new Font("Serif", Font.PLAIN, 12));
        progressLabel.setBorder(new EmptyBorder(0, 3, 0, 0));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, 0);
        progressPanel.add(new ProgressCircle(novelItem.getProgressPercent(),
                novelItem.isFileExists() ? NORMAL_PROGRESS_COLOR : MISSING_PROGRESS_COLOR), constraints);

        constraints.gridx = 1;
        progressPanel.add(progressLabel, constraints);

        constraints.gridx = 2;
        constraints.weightx = 1;
        progressPanel.add(Box.createHorizontalGlue(), constraints);
        return progressPanel;
    }

    public Dimension getPreferredSizeForItem(NovelItem novelItem) {
        JPanel panel = getPanelForItem(novelItem);
        return panel.getPreferredSize(); // 获取 JPanel 的首选尺寸
    }

    private static class ProgressCircle extends JComponent {
        private static final int SIZE = 12;
        private final Integer percent;
        private final Color progressColor;

        private ProgressCircle(Integer percent, Color progressColor) {
            this.percent = percent;
            this.progressColor = progressColor;
            setPreferredSize(new Dimension(SIZE, SIZE));
            setMinimumSize(new Dimension(SIZE, SIZE));
            setMaximumSize(new Dimension(SIZE, SIZE));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int stroke = 2;
            int size = SIZE - stroke;
            int offset = stroke / 2;
            g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(85, 95, 85));
            g2.drawOval(offset, offset, size, size);

            if (percent != null) {
                int safePercent = Math.max(0, Math.min(100, percent));
                g2.setColor(progressColor);
                g2.drawArc(offset, offset, size, size, 90, -Math.round(safePercent * 360f / 100f));
            }

            g2.dispose();
        }
    }
}
