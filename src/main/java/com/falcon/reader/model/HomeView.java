package com.falcon.reader.model;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.falcon.reader.entity.NovelConfig;
import com.falcon.reader.entity.NovelRecord;
import com.falcon.reader.entity.novelItem.NovelItem;
import com.falcon.reader.entity.novelItem.NovelItemRenderer;
import com.falcon.reader.util.UIUtils;
import javafx.util.Pair;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 主页视图，显示小说列表和按钮
 *
 * @author zxy
 * @date 2026/2/11 16:16
 */
public class HomeView {
    private JFrame frame;
    private JButton openButton;
    private JButton closeButton;
    private JScrollPane scrollPane;
    private Consumer<String> openNovelCallback;
    private Pair<NovelConfig, Map<String, NovelRecord>> novelConfigAndRecordPair;

    /**
     * 构造函数，初始化主页视图
     * @param frame 主窗口
     * @param openNovelCallback 打开小说回调函数
     * @param pair 小说配置和记录对
     * @author zxy
     * @date 2024/10/21
     */
    public HomeView(JFrame frame, Consumer<String> openNovelCallback, Pair<NovelConfig, Map<String, NovelRecord>> pair) {
        this.frame = frame;
        this.openNovelCallback = openNovelCallback;
        this.novelConfigAndRecordPair = pair;
        initComponents();
    }

    /**
     * 初始化组件，包括添加打开按钮、关闭按钮，并更新小说列表
     * @author zxy
     * @date 2024/10/21
     */
    private void initComponents() {
        // 创建并配置打开新文件按钮
        openButton = UIUtils.createStyledButton("打开新文件", 6, 10, 80, 35);
        openButton.addActionListener(e -> {
            // 创建文件选择器，设置默认目录和文件过滤器
            JFileChooser fileChooser = new JFileChooser(".");
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件 (*.txt)", "txt"));
            // 显示文件选择对话框
            if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                String selectedPath = fileChooser.getSelectedFile().getAbsolutePath();
                // 检查文件路径是否有效且为txt格式
                if (StrUtil.isNotBlank(selectedPath) && selectedPath.toLowerCase().endsWith(".txt")) {
                    openNovelCallback.accept(selectedPath);
                } else {
                    // 显示错误消息
                    JOptionPane.showMessageDialog(frame, "无效的文件！", "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        frame.add(openButton);

        // 创建并配置关闭按钮
        closeButton = UIUtils.createStyledButton("×", frame.getWidth() - 35, 10, 25, 25);
        closeButton.setFont(new Font("Serif", Font.PLAIN, 18));
        closeButton.addActionListener(e -> System.exit(0));
        frame.add(closeButton);

        // 更新小说列表显示
        updateNovelList(novelConfigAndRecordPair);
    }

    /**
     * 更新小说列表，根据提供的配置和记录对刷新列表内容
     * @param pair 小说配置和记录对
     * @author zxy
     * @date 2024/10/21
     */
    public void updateNovelList(Pair<NovelConfig, Map<String, NovelRecord>> pair) {
        // 如果滚动面板已存在，先移除它
        if (scrollPane != null) {
            frame.remove(scrollPane);
        }
        novelConfigAndRecordPair = pair;

        // 获取小说路径列表
        List<String> novelList = CollectionUtil.isNotEmpty(pair.getValue()) ? new ArrayList<>(pair.getValue().keySet()) : new ArrayList<>();
        DefaultListModel<NovelItem> listModel = new DefaultListModel<>();
        // 将路径转换为NovelItem并添加到模型中
        novelList.forEach(item -> listModel.addElement(new NovelItem(item)));

        // 创建小说列表组件
        JList<NovelItem> list = new JList<>(listModel);
        list.setCellRenderer(new NovelItemRenderer());
        list.setFont(new Font("Serif", Font.PLAIN, 16));
        list.setBackground(new Color(0, 0, 0, 0));
        list.setForeground(Color.WHITE);
        list.setOpaque(false);
        list.setFixedCellHeight(50);  // 示例高度，根据 renderer 调整
        // 添加鼠标监听器处理点击事件
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index != -1 && list.getCellBounds(index, index).contains(e.getPoint())) {
                    NovelItem item = listModel.get(index);
                    String path = item.getFilePath() + item.getFileName();
                    if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                        // 左键点击打开小说
                        openNovelCallback.accept(path);
                    } else if (e.getButton() == java.awt.event.MouseEvent.BUTTON3) {
                        // 右键点击删除记录
                        int confirm = JOptionPane.showConfirmDialog(frame, "是否删除 “" + item.getFileName() + "” ？", "删除记录", JOptionPane.YES_NO_OPTION);
                        if (confirm == JOptionPane.YES_OPTION) {
                            listModel.remove(index);
                            pair.getValue().remove(path);
                            ReadingRecord.deleteRecord(frame, path);
                        }
                    }
                }
            }
        });

        // 创建滚动面板并配置样式
        scrollPane = new JScrollPane(list);
        scrollPane.setBounds(0, 50, frame.getWidth() - 10, frame.getHeight() - 60);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);

        // 设置滚动条透明度
        UIUtils.setScrollbarTransparency(scrollPane.getVerticalScrollBar(), 4, scrollPane.getVerticalScrollBar().getHeight());
        UIUtils.setScrollbarTransparency(scrollPane.getHorizontalScrollBar(), scrollPane.getHorizontalScrollBar().getWidth(), 4);

        frame.add(scrollPane);
        frame.revalidate();
        frame.repaint();
    }

    /**
     * 显示主页视图组件
     * @author zxy
     * @date 2024/10/21
     */
    public void show() {
        openButton.setVisible(true);
        closeButton.setVisible(true);
        scrollPane.setVisible(true);
    }

    /**
     * 隐藏主页视图组件
     * @author zxy
     * @date 2024/10/21
     */
    public void hide() {
        openButton.setVisible(false);
        closeButton.setVisible(false);
        scrollPane.setVisible(false);
    }
}