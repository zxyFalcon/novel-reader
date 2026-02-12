package com.falcon.reader.model;

import com.falcon.reader.util.NumericDocumentFilter;
import com.falcon.reader.util.SpringUtilities;
import javafx.util.Pair;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 设置对话框类，处理小说阅读器的设置界面
 *
 * @author zxy
 * @date 2026/2/11 16:22
 */
public class SettingsDialog {
    private final JFrame frame;
    private final JLabel label;
    private final int maxPages;
    private final int initialPage;
    private final Consumer<SettingChanges> applyCallback;

    /**
     * 构造函数，初始化设置对话框
     * @param frame 主窗口
     * @param label 显示标签
     * @param maxPages 最大页数
     * @param initialPage 初始页码
     * @param applyCallback 应用设置的回调函数
     * @author zxy
     * @date 2024/10/21
     */
    public SettingsDialog(JFrame frame, JLabel label, int maxPages, int initialPage, Consumer<SettingChanges> applyCallback) {
        this.frame = frame;
        this.label = label;
        this.maxPages = maxPages;
        this.initialPage = initialPage;
        this.applyCallback = applyCallback;
    }

    /**
     * 显示设置对话框
     * @author zxy
     * @date 2024/10/21
     */
    public void show() {
        // 创建颜色选择器
        JColorChooser colorChooser = new JColorChooser(label.getForeground());

        // 颜色选择按钮
        JButton colorButton = new JButton("选择颜色");
        final Color[] selectedColor = {label.getForeground()};  // 用数组模拟可变引用
        colorButton.addActionListener(e -> {
            JDialog colorDialog = JColorChooser.createDialog(frame, "选择颜色", true, colorChooser,
                    ok -> selectedColor[0] = colorChooser.getColor(), null);
            colorDialog.setVisible(true);
        });

        // 字体大小输入框
        JTextField textFontSize = new JTextField(String.valueOf(label.getFont().getSize()), 10);
        ((AbstractDocument) textFontSize.getDocument()).setDocumentFilter(new NumericDocumentFilter());

        // 字体样式下拉框
        JComboBox<Pair<String, Integer>> fontStyleComboBox = new JComboBox<>();
        fontStyleComboBox.addItem(new Pair<>("常规", Font.PLAIN));
        fontStyleComboBox.addItem(new Pair<>("加粗", Font.BOLD));
        fontStyleComboBox.addItem(new Pair<>("斜体", Font.ITALIC));
        fontStyleComboBox.setSelectedIndex(label.getFont().getStyle());

        // 字体名称下拉框
        JComboBox<Pair<String, String>> fontComboBox = new JComboBox<>();
        fontComboBox.addItem(new Pair<>("Serif", "Serif"));
        fontComboBox.addItem(new Pair<>("SansSerif", "SansSerif"));
        fontComboBox.addItem(new Pair<>("Monospaced", "Monospaced"));
        fontComboBox.addItem(new Pair<>("Dialog", "Dialog"));
        fontComboBox.addItem(new Pair<>("DialogInput", "DialogInput"));
        fontComboBox.addItem(new Pair<>("Arial", "Arial"));
        //        fontComboBox.addItem(new Pair<>("Times New Roman", "Times New Roman"));
        fontComboBox.addItem(new Pair<>("Courier New", "Courier New"));
        fontComboBox.addItem(new Pair<>("Verdana", "Verdana"));
        fontComboBox.addItem(new Pair<>("宋体", "SimSun"));
        fontComboBox.addItem(new Pair<>("微软雅黑", "Microsoft YaHei"));
        fontComboBox.addItem(new Pair<>("黑体", "SimHei"));
        fontComboBox.addItem(new Pair<>("楷体", "KaiTi"));
        fontComboBox.addItem(new Pair<>("仿宋", "FangSong"));
        fontComboBox.addItem(new Pair<>("隶书", "LiSu"));
        fontComboBox.addItem(new Pair<>("等线", "DengXian"));
        String currentFontName = label.getFont().getName();
        for (int i = 0; i < fontComboBox.getItemCount(); i++) {
            if (Objects.equals(fontComboBox.getItemAt(i).getValue(), currentFontName)) {
                fontComboBox.setSelectedIndex(i);
                break;
            }
        }

        // 窗口宽度输入框
        JTextField textWidth = new JTextField(String.valueOf(frame.getWidth()), 10);
        ((AbstractDocument) textWidth.getDocument()).setDocumentFilter(new NumericDocumentFilter());
        // 窗口高度输入框
        JTextField textHeight = new JTextField(String.valueOf(frame.getHeight()), 10);
        ((AbstractDocument) textHeight.getDocument()).setDocumentFilter(new NumericDocumentFilter());

        // 跳页输入框
        JTextField textJumpPage = new JTextField(String.valueOf(initialPage), 10);
        ((AbstractDocument) textJumpPage.getDocument()).setDocumentFilter(new NumericDocumentFilter());

        JButton okButton = new JButton("确认");
        JButton cancelButton = new JButton("取消");

        // 创建设置面板
        JPanel panel = new JPanel(new SpringLayout());
        panel.add(new JLabel("字体颜色:"));   panel.add(colorButton);
        panel.add(new JLabel("字体大小:"));   panel.add(textFontSize);
        panel.add(new JLabel("字体样式:"));   panel.add(fontStyleComboBox);
        panel.add(new JLabel("字体:"));       panel.add(fontComboBox);
        panel.add(new JLabel("窗口宽度:"));   panel.add(textWidth);
        panel.add(new JLabel("窗口高度:"));   panel.add(textHeight);
        panel.add(new JLabel("跳页(0/" + (maxPages - 1) + "):")); panel.add(textJumpPage);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        panel.add(new JLabel()); panel.add(buttonPanel);

        panel.setBorder(new EmptyBorder(10, 20, 10, 20));
        SpringUtilities.makeCompactGrid(panel, 8, 2, 5, 5, 5, 5);

        // 创建模态对话框
        JDialog dialog = new JDialog(frame, "设置", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setContentPane(panel);
        dialog.pack();                    // 自动适应内容大小
        dialog.setLocationRelativeTo(frame);

        // 确认按钮监听器
        okButton.addActionListener(e -> {
            // 检查输入是否完整
            if (textFontSize.getText().isEmpty() || textWidth.getText().isEmpty() || textHeight.getText().isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入完整信息", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            int newFontSize = Integer.parseInt(textFontSize.getText());
            int newFontStyle = ((Pair<String, Integer>) fontStyleComboBox.getSelectedItem()).getValue();
            String newFontName = ((Pair<String, String>) fontComboBox.getSelectedItem()).getValue();
            int newWidth  = Integer.parseInt(textWidth.getText());
            int newHeight = Integer.parseInt(textHeight.getText());
            int newJumpPage = Integer.parseInt(textJumpPage.getText());

            // 验证窗口大小
            if (newWidth < 100 || newHeight < 100) {
                JOptionPane.showMessageDialog(dialog, "窗口宽高不能小于100", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            // 验证页码
            if (newJumpPage < 0 || newJumpPage >= maxPages) {
                JOptionPane.showMessageDialog(dialog, "页码无效", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // 通过回调应用设置
            applyCallback.accept(new SettingChanges(newFontName, newFontStyle, newFontSize, selectedColor[0], newWidth, newHeight, newJumpPage));

            dialog.dispose();
        });
        // 取消按钮监听器
        cancelButton.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
    }

    /**
     * 设置变更对象，用于回调传递变更值
     */
    public static class SettingChanges {
        public final String fontName;
        public final int fontStyle;
        public final int fontSize;
        public final Color color;
        public final int width;
        public final int height;
        public final int jumpPage;

        public SettingChanges(String fontName, int fontStyle, int fontSize, Color color, int width, int height, int jumpPage) {
            this.fontName = fontName;
            this.fontStyle = fontStyle;
            this.fontSize = fontSize;
            this.color = color;
            this.width = width;
            this.height = height;
            this.jumpPage = jumpPage;
        }
    }
}