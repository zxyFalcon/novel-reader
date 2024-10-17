package com.falcon.reader.util;

import javax.swing.*;
import java.awt.*;

/**
 * TODO
 *
 * @author zxy
 * @date 2024/5/16 16:12
 **/
public class AutoSizeLabel extends JLabel {
    public AutoSizeLabel(String text) {
        super(text);
        super.setFont(new Font("Serif", Font.PLAIN, 15)); // 设置字体大小
        super.setForeground(Color.WHITE); // 设置字体颜色
//        setOpaque(true); // 设置标签为不透明
        updateSize(); // 更新大小
    }

//    public AutoSizeLabel() {
//        super();
//        setFont(getFont().deriveFont(12f)); // 设置字体大小
//        setForeground(Color.BLACK); // 设置字体颜色
//        setOpaque(true); // 设置标签为不透明
//        updateSize(); // 更新大小
//    }

    public void updateSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        Dimension dim = new Dimension(metrics.stringWidth(getText()), metrics.getHeight());
        setPreferredSize(dim);
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        updateSize();
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        updateSize();
    }
}
