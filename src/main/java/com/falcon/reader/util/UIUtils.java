package com.falcon.reader.util;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

/**
 * UI 工具类，提供按钮和滚动条的样式设置
 *
 * @author zxy
 * @date 2026/2/11 16:20
 **/
public class UIUtils {
    /**
     * 创建样式化的按钮，用于打开或关闭
     * @param text 按钮文本
     * @param x x坐标
     * @param y y坐标
     * @param w 宽度
     * @param h 高度
     * @return 按钮实例
     * @author zxy
     * @date 2024/10/21
     */
    public static JButton createStyledButton(String text, int x, int y, int w, int h) {
        JButton button = new JButton(text);
        button.setBounds(x, y, w, h);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Serif", Font.PLAIN, text.equals("×") ? 18 : 13));
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorder(new LineBorder(Color.GRAY, 1));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /**
     * 设置滚动条的透明度和样式
     * @param scrollBar 滚动条组件
     * @param width 宽度
     * @param height 高度
     * @author zxy
     * @date 2024/10/21
     */
    public static void setScrollbarTransparency(JScrollBar scrollBar, int width, int height) {
        scrollBar.setOpaque(false);
        scrollBar.setBackground(new Color(0, 0, 0, 0));
        scrollBar.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = Color.LIGHT_GRAY;
                this.trackColor = new Color(0, 0, 0, 0);
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createTransparentButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createTransparentButton();
            }

            /**
             * 创建透明按钮
             * @return 透明按钮实例
             * @author zxy
             * @date 2024/10/21
             */
            private JButton createTransparentButton() {
                JButton button = new JButton() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        g.setColor(new Color(0, 0, 0, 0));
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
                button.setPreferredSize(new Dimension(0, 0));
                button.setBorder(null);
                button.setOpaque(false);
                return button;
            }

            @Override
            public Dimension getPreferredSize(JComponent c) {
                return new Dimension(width, height);
            }
        });
    }
}