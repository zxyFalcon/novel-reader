package com.falcon.reader;

import com.falcon.reader.entity.Chapter;
import com.falcon.reader.entity.NovelRecord;
import com.falcon.reader.model.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 主控制器类，处理窗口初始化和事件
 *
 * @author zxy
 * @date 2026/2/11 16:16
 */
public class NovelReader implements MouseListener, MouseMotionListener, MouseWheelListener {
    private static final int SAVE_DEBOUNCE_DELAY_MS = 1000;

    private int x, y;
    private JFrame frame;
    private String filePath;
    private int currentPage = 0;
    private List<String> pages = new ArrayList<>();
    private List<Integer> pageStartOffsets = new ArrayList<>();
    private List<Chapter> chapters = new ArrayList<>();
    private int totalLength = 0;
    private ReadingData readingData;
    private HomeView homeView;
    private NovelView novelView;
    private SwingWorker<PageResult, Void> pageWorker;
    private Timer saveTimer;

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
        setFrameIcon();
        frame.setSize(900, 600);
        frame.setUndecorated(true);//设置jframe取消顶部标题栏
        frame.addMouseListener(this);//窗口添加鼠标监听器
        frame.addMouseMotionListener(this);//窗口添加鼠标姿势动作监听器
        frame.addMouseWheelListener(this);
        frame.setBackground(new Color(0, 0, 0, 1));

        frame.setLocation(800, 500);//设置窗口的显示位置
        frame.setLayout(null);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);//关闭前统一保存状态
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveAndExit();
            }
        });

        // 加载阅读记录
        readingData = ReadingRecord.loadRecord(frame);
        // 初始化主页视图
        homeView = new HomeView(frame, this::openNovel, this::saveAndExit, readingData, data -> readingData = data);
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
            novelView = new NovelView(frame, readingData.getConfig());
        }
        homeView.hide();
        novelView.show();

        Integer targetOffset = null;
        // 检查是否已有阅读记录，若有则恢复当前页
        if (readingData.getRecords().containsKey(filePath)) {
            NovelRecord record = readingData.getRecords().get(filePath);
            currentPage = record.getCurrentPage() == null ? 0 : record.getCurrentPage();
            targetOffset = record.getCurrentOffset();
        } else {
            currentPage = 0;
        }

        loadPagesAsync(filePath, currentPage, targetOffset);
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

    private void loadPagesAsync(String targetFilePath, int targetPage) {
        loadPagesAsync(targetFilePath, targetPage, null);
    }

    private void loadPagesAsync(String targetFilePath, int targetPage, Integer targetOffset) {
        if (pageWorker != null && !pageWorker.isDone()) {
            pageWorker.cancel(true);
        }

        pages = new ArrayList<>();
        pageStartOffsets = new ArrayList<>();
        chapters = new ArrayList<>();
        totalLength = 0;
        currentPage = Math.max(0, targetPage);
        novelView.getLabel().setText("<html>正在分页，请稍候...</html>");
        FontMetrics fontMetrics = novelView.getLabel().getFontMetrics(novelView.getLabel().getFont());
        int labelWidth = novelView.getLabel().getWidth();
        int labelHeight = novelView.getLabel().getHeight();

        pageWorker = new SwingWorker<PageResult, Void>() {
            @Override
            protected PageResult doInBackground() {
                return PageCalculator.calculate(targetFilePath, fontMetrics, labelWidth, labelHeight);
            }

            @Override
            protected void done() {
                if (isCancelled() || !targetFilePath.equals(filePath)) {
                    return;
                }
                try {
                    PageResult pageResult = get();
                    pages = pageResult.getPages();
                    pageStartOffsets = pageResult.getPageStartOffsets();
                    chapters = pageResult.getChapters();
                    totalLength = pageResult.getTotalLength();
                    if (pages.isEmpty()) {
                        currentPage = 0;
                        novelView.getLabel().setText("<html>无法读取或显示该文件</html>");
                        return;
                    }
                    currentPage = targetOffset == null ? Math.max(0, Math.min(targetPage, pages.size() - 1))
                            : findPageByOffset(targetOffset);
                    saveCurrentRecord();
                    showPage();
                } catch (Exception ex) {
                    currentPage = 0;
                    pages = new ArrayList<>();
                    pageStartOffsets = new ArrayList<>();
                    chapters = new ArrayList<>();
                    totalLength = 0;
                    novelView.getLabel().setText("<html>分页失败: " + ex.getMessage() + "</html>");
                }
            }
        };
        pageWorker.execute();
    }

    /**
     * 展示设置对话框
     * @author zxy
     * @date 2024/10/21
     */
    private void showSettings() {
        if (pages.isEmpty()) {
            return;
        }
        new SettingsDialog(frame, novelView.getLabel(),
                changes -> {
                    // 应用设置
                    frame.setSize(changes.width, changes.height);
                    novelView.getLabel().setBounds(0, 0, changes.width, changes.height);
                    novelView.getLabel().setFont(new Font(changes.fontName, changes.fontStyle, changes.fontSize));
                    novelView.getLabel().setForeground(changes.color);
                    loadPagesAsync(filePath, currentPage, getCurrentOffset());
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
                saveCurrentRecord();
                refreshReadingData();
                novelView.hide();
                homeView.updateNovelList(readingData);  // 更新列表
                homeView.show();
            } else if (e.getButton() == MouseEvent.BUTTON1) {
                // 左键显示设置
                showSettings();
            } else if (e.getButton() == MouseEvent.BUTTON2) {
                // 中键显示章节目录
                showChapters();
            }
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (pages.isEmpty()) {
            return;
        }
        // 处理鼠标滚轮翻页
        int rotation = e.getWheelRotation();
        if (rotation < 0) {
            currentPage--;
        } else if (rotation > 0) {
            currentPage++;
        }
        currentPage = Math.max(0, Math.min(currentPage, pages.size() - 1));
        showPage();
        scheduleSaveCurrentRecord();
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

    private void saveCurrentRecord() {
        if (saveTimer != null) {
            saveTimer.stop();
        }
        saveCurrentRecordNow();
    }

    private void scheduleSaveCurrentRecord() {
        if (novelView == null || !novelView.isVisible()) {
            return;
        }
        if (saveTimer == null) {
            saveTimer = new Timer(SAVE_DEBOUNCE_DELAY_MS, e -> saveCurrentRecordNow());
            saveTimer.setRepeats(false);
        }
        saveTimer.restart();
    }

    private void saveCurrentRecordNow() {
        if (novelView != null && novelView.isVisible()) {
            ReadingRecord.saveRecord(frame, novelView.getLabel(), filePath, currentPage, pages.size(),
                    getCurrentOffset(), totalLength);
        }
    }

    private int getCurrentOffset() {
        if (pageStartOffsets == null || currentPage < 0 || currentPage >= pageStartOffsets.size()) {
            return 0;
        }
        return pageStartOffsets.get(currentPage);
    }

    private int findPageByOffset(int offset) {
        if (pageStartOffsets == null || pageStartOffsets.isEmpty()) {
            return Math.max(0, Math.min(currentPage, pages.size() - 1));
        }

        int safeOffset = Math.max(0, offset);
        int pageIndex = 0;
        for (int i = 0; i < pageStartOffsets.size(); i++) {
            if (pageStartOffsets.get(i) <= safeOffset) {
                pageIndex = i;
            } else {
                break;
            }
        }
        return Math.max(0, Math.min(pageIndex, pages.size() - 1));
    }

    private void refreshReadingData() {
        readingData = ReadingRecord.loadRecord(frame);
    }

    private void showChapters() {
        if (pages.isEmpty()) {
            return;
        }
        new ChapterDialog(frame, chapters, novelView.getLabel().getFont(), pages.size(), currentPage, pageIndex -> {
            currentPage = Math.max(0, Math.min(pageIndex, pages.size() - 1));
            showPage();
            saveCurrentRecord();
        }).show();
    }

    private void saveAndExit() {
        saveCurrentRecord();
        if (pageWorker != null && !pageWorker.isDone()) {
            pageWorker.cancel(true);
        }
        frame.dispose();
        System.exit(0);
    }

    private void setFrameIcon() {
        URL iconUrl = NovelReader.class.getResource("/icon.png");
        if (iconUrl == null) {
            return;
        }

        try {
            Image icon = ImageIO.read(iconUrl);
            if (icon != null) {
                frame.setIconImage(icon);
                frame.setIconImages(Collections.singletonList(icon));
            }
        } catch (IOException e) {
            frame.setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
        }
    }

    public static void main(String[] args) {
        new NovelReader();
    }
}
