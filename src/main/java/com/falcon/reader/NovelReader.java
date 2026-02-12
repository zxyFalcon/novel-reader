package com.falcon.reader;

import com.falcon.reader.entity.NovelConfig;
import com.falcon.reader.entity.NovelRecord;
import com.falcon.reader.model.HomeView;
import com.falcon.reader.model.NovelView;
import com.falcon.reader.model.PageCalculator;
import com.falcon.reader.model.ReadingRecord;
import com.falcon.reader.model.SettingsDialog;
import javafx.util.Pair;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 主控制器类，处理窗口初始化和事件
 *
 * @author zxy
 * @date 2026/2/11 16:16
 */
public class NovelReader implements MouseListener, MouseMotionListener, MouseWheelListener {
    private int x, y;
    private JFrame frame;
    private String filePath;
    private int currentPage = 0;
    private List<String> pages = new ArrayList<>();
    private Pair<NovelConfig, Map<String, NovelRecord>> novelConfigAndRecordPair;
    private HomeView homeView;
    private NovelView novelView;

    /**
     * 构造函数，初始化主窗口和视图
     * @author zxy
     * @date 2024/10/17
     */
    public NovelReader() {
        try {
            // 设置UI样式为系统默认样式
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        frame = new JFrame();
        frame.setSize(900, 600);
        frame.setUndecorated(true);//设置jframe取消顶部标题栏
        frame.addMouseListener(this);//窗口添加鼠标监听器
        frame.addMouseMotionListener(this);//窗口添加鼠标姿势动作监听器
        frame.addMouseWheelListener(this);
        frame.setBackground(new Color(0, 0, 0, 1));

        frame.setLocation(800, 500);//设置窗口的显示位置
        frame.setLayout(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);//创建并关闭窗口时的默认操作

        // 加载阅读记录
        novelConfigAndRecordPair = ReadingRecord.loadRecord(frame);
        // 初始化主页视图
        homeView = new HomeView(frame, this::openNovel, novelConfigAndRecordPair);
        homeView.show();

        frame.setVisible(true);
    }

    /**
     * 打开小说，切换到小说视图并计算页内容
     * @param selectedFilePath 小说文件路径
     * @author zxy
     * @date 2024/10/21
     */
    public void openNovel(String selectedFilePath) {
        filePath = selectedFilePath;
        if (novelView == null) {
            novelView = new NovelView(frame, novelConfigAndRecordPair.getKey());
        }
        homeView.hide();
        novelView.show();

        // 检查是否已有阅读记录，若有则恢复当前页
        if (novelConfigAndRecordPair.getValue().containsKey(filePath)) {
            currentPage = novelConfigAndRecordPair.getValue().get(filePath).getCurrentPage();
        } else {
            currentPage = 0;
        }

        // 计算分页
        pages = PageCalculator.calculatePages(filePath, novelView.getLabel());
        showPage();
    }

    /**
     * 展示当前页内容
     * @author zxy
     * @date 2024/10/21
     */
    private void showPage() {
        if (currentPage >= 0 && currentPage < pages.size()) {
            novelView.getLabel().setText(pages.get(currentPage));
        }
    }

    /**
     * 展示设置对话框
     * @author zxy
     * @date 2024/10/21
     */
    private void showSettings() {
        new SettingsDialog(frame, novelView.getLabel(), pages.size(), currentPage,
                changes -> {
                    // 应用设置
                    frame.setSize(changes.width, changes.height);
                    novelView.getLabel().setBounds(0, 0, changes.width, changes.height);
                    novelView.getLabel().setFont(new Font(changes.fontName, changes.fontStyle, changes.fontSize));
                    novelView.getLabel().setForeground(changes.color);
                    currentPage = changes.jumpPage;
                    pages = PageCalculator.calculatePages(filePath, novelView.getLabel());
                    showPage();
                }).show();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        // 记录鼠标按下位置
        x = e.getX();
        y = e.getY();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        // 拖动窗口
        frame.setLocation(e.getXOnScreen() - x, e.getYOnScreen() - y);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // 如果小说视图可见，处理点击事件
        if (novelView != null && novelView.isVisible()) {
            if (e.getButton() == MouseEvent.BUTTON3) {
                // 右键保存记录并返回主页
                ReadingRecord.saveRecord(frame, novelView.getLabel(), filePath, currentPage);
                novelConfigAndRecordPair = ReadingRecord.loadRecord(frame);
                novelView.hide();
                homeView.updateNovelList(novelConfigAndRecordPair);  // 更新列表
                homeView.show();
            } else if (e.getButton() == MouseEvent.BUTTON1) {
                // 左键显示设置
                showSettings();
            }
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        // 处理鼠标滚轮翻页
        int rotation = e.getWheelRotation();
        if (rotation < 0) {
            currentPage--;
        } else if (rotation > 0) {
            currentPage++;
        }
        currentPage = Math.max(0, Math.min(currentPage, pages.size() - 1));
        showPage();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    public static void main(String[] args) {
        new NovelReader();
    }
}