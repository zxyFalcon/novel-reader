package com.falcon.reader.model;

import com.falcon.reader.entity.NovelConfig;
import javax.swing.*;
import java.awt.FlowLayout;

/**
 * 小说阅读视图，管理小说内容的显示
 *
 * @author zxy
 * @date 2026/2/11 16:18
 **/
public class NovelView {
    private JFrame frame;
    private JLabel label;
    private boolean visible = false;

    /**
     * 构造函数，初始化小说视图
     * @param frame 主窗口
     * @param config 小说配置
     * @author zxy
     * @date 2024/10/21
     */
    public NovelView(JFrame frame, NovelConfig config) {
        this.frame = frame;
        label = new JLabel();
        label.setLayout(new FlowLayout());
        label.setBounds(0, 0, frame.getWidth(), frame.getHeight());
        label.setFont(config.getFont());
        label.setForeground(config.getForeground());
        label.setVerticalAlignment(JLabel.TOP);
        label.setVerticalTextPosition(JLabel.TOP);
        frame.add(label);
    }

    /**
     * 获取显示标签
     * @return JLabel
     * @author zxy
     * @date 2024/10/21
     */
    public JLabel getLabel() {
        return label;
    }

    /**
     * 显示小说视图
     * @author zxy
     * @date 2024/10/21
     */
    public void show() {
        label.setVisible(true);
        visible = true;
    }

    /**
     * 隐藏小说视图
     * @author zxy
     * @date 2024/10/21
     */
    public void hide() {
        label.setVisible(false);
        visible = false;
    }

    /**
     * 检查视图是否可见
     * @return boolean
     * @author zxy
     * @date 2024/10/21
     */
    public boolean isVisible() {
        return visible;
    }
}