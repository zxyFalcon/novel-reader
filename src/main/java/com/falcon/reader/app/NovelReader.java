package com.falcon.reader.app;

import com.falcon.reader.domain.Chapter;
import com.falcon.reader.domain.NovelRecord;
import com.falcon.reader.domain.ReadingData;
import com.falcon.reader.domain.WindowState;
import com.falcon.reader.epub.EpubBook;
import com.falcon.reader.epub.EpubParser;
import com.falcon.reader.pagination.PageCalculator;
import com.falcon.reader.pagination.PageResult;
import com.falcon.reader.persistence.ReadingRecordRepository;
import com.falcon.reader.ui.dialog.ChapterDialog;
import com.falcon.reader.ui.dialog.SettingsDialog;
import com.falcon.reader.ui.view.HomeView;
import com.falcon.reader.ui.view.NovelView;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * 主控制器类，处理窗口初始化和事件
 *
 * @author zxy
 * @date 2026/2/11 16:16
 */
public class NovelReader implements MouseListener, MouseMotionListener, MouseWheelListener {
    private static final int SAVE_DEBOUNCE_DELAY_MS = 1000;
    private static final String GUIDE_SHOWN_KEY = "operationGuideShown";

    private int x, y;
    private JFrame frame;
    private String filePath;
    private int currentPage = 0;
    private List<String> pages = new ArrayList<>();
    private List<Integer> pageStartOffsets = new ArrayList<>();
    private List<Chapter> chapters = new ArrayList<>();
    private int totalLength = 0;
    private ReadingData readingData;
    private final ReadingRecordRepository recordRepository = new ReadingRecordRepository(
            message -> JOptionPane.showMessageDialog(frame, message, "错误", JOptionPane.ERROR_MESSAGE));
    private final HomeView homeView;
    private NovelView novelView;
    private SwingWorker<PageResult, Void> pageWorker;
    private SwingWorker<EpubBook, Void> epubWorker;
    private EpubBook epubBook;
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
        readingData = recordRepository.loadRecord();
        applyWindowState(readingData.getWindowState());
        // 初始化主页视图
        homeView = new HomeView(frame, this::openNovel, this::saveAndExit, readingData, data -> readingData = data,
                this::showSettings, recordRepository);
        homeView.show();

        frame.setVisible(true);
        showOperationGuideOnFirstLaunch();
    }

    private void showOperationGuideOnFirstLaunch() {
        Preferences preferences = Preferences.userNodeForPackage(NovelReader.class);
        if (preferences.getBoolean(GUIDE_SHOWN_KEY, false)) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            String guide = "<html><div style='width: 320px'>"
                    + "<h3>欢迎使用小说阅读器</h3>"
                    + "<p>1. 点击左上角“打开”，选择 TXT 或 EPUB 书籍。</p>"
                    + "<p>2. 在书架中左键点击书籍开始阅读，右键可管理记录。</p>"
                    + "<p>3. 阅读时滚动鼠标翻页，右键返回书架。</p>"
                    + "<p>4. 使用首页右上角菜单进行排序、筛选和阅读设置。</p>"
                    + "</div></html>";
            JOptionPane.showMessageDialog(frame, guide, "操作指南", JOptionPane.INFORMATION_MESSAGE);
            preferences.putBoolean(GUIDE_SHOWN_KEY, true);
        });
    }

    /**
     * 打开小说，切换到小说视图并计算页内容
     * @param selectedFilePath 小说文件路径
     * @author zxy
     * @date 2024/10/21
     */
    public void openNovel(String selectedFilePath) {
        cancelLoading();
        releaseEpubBook();
        filePath = selectedFilePath;
        if (novelView == null) {
            novelView = new NovelView(frame, readingData.getConfig());
            novelView.setEpubCallbacks(this::openEpubLink, this::returnHome, this::showChapters,
                    () -> changeEpubSection(-1), () -> changeEpubSection(1), this::scheduleSaveCurrentRecord,
                    this::showSettings);
        }
        homeView.hide();

        Integer targetOffset = null;
        // 检查是否已有阅读记录，若有则恢复当前页
        if (readingData.getRecords().containsKey(filePath)) {
            NovelRecord record = readingData.getRecords().get(filePath);
            currentPage = record.getCurrentPage() == null ? 0 : record.getCurrentPage();
            targetOffset = record.getCurrentOffset();
        } else {
            currentPage = 0;
        }

        if (isEpubFile(filePath)) {
            novelView.showEpubMessage("正在解析 EPUB，请稍候...");
            loadEpubAsync(filePath, currentPage, targetOffset);
        } else {
            novelView.showTxt();
            loadPagesAsync(filePath, currentPage, targetOffset);
        }
    }

    private void loadEpubAsync(String targetFilePath, int targetSection, Integer targetPage) {
        epubWorker = new SwingWorker<EpubBook, Void>() {
            @Override
            protected EpubBook doInBackground() throws Exception {
                EpubBook parsed = EpubParser.parse(targetFilePath);
                if (isCancelled()) {
                    parsed.close();
                }
                return parsed;
            }

            @Override
            protected void done() {
                if (isCancelled() || !targetFilePath.equals(filePath)) {
                    return;
                }
                try {
                    epubBook = get();
                    pages = new ArrayList<>();
                    for (int i = 0; i < epubBook.getSections().size(); i++) {
                        pages.add("");
                    }
                    chapters = epubBook.getChapters();
                    totalLength = pages.size();
                    currentPage = Math.max(0, Math.min(targetSection, pages.size() - 1));
                    novelView.restoreEpubPage(targetPage);
                    showPage();
                } catch (Exception ex) {
                    pages = new ArrayList<>();
                    chapters = new ArrayList<>();
                    totalLength = 0;
                    novelView.showEpubMessage("无法读取 EPUB：" + rootCauseMessage(ex));
                }
            }
        };
        epubWorker.execute();
    }

    /**
     * 展示当前页内容
     * @author zxy
     * @date 2024/10/21
     */
    private void showPage() {
        if (novelView != null && novelView.isEpubMode()) {
            if (epubBook != null && currentPage >= 0 && currentPage < epubBook.getSections().size()) {
                try {
                    novelView.showEpub(epubBook.getSections().get(currentPage));
                } catch (IOException ex) {
                    novelView.showEpubMessage("无法显示 EPUB 章节：" + rootCauseMessage(ex));
                }
            }
            return;
        }
        if (currentPage >= 0 && currentPage < pages.size()) {
            novelView.getLabel().setText(pages.get(currentPage));
        }
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
        JLabel settingsLabel = getSettingsLabel();
        boolean readingVisible = novelView != null && novelView.isVisible() && !novelView.isEpubMode() && filePath != null;
        new SettingsDialog(frame, settingsLabel,
                changes -> {
                    // 应用设置
                    frame.setSize(changes.width, changes.height);
                    Font newFont = new Font(changes.fontName, changes.fontStyle, changes.fontSize);
                    settingsLabel.setFont(newFont);
                    settingsLabel.setForeground(changes.color);
                    readingData.getConfig().setFont(newFont);
                    readingData.getConfig().setForeground(changes.color);
                    homeView.refreshLayout();
                    if (novelView != null) {
                        novelView.updateBounds(changes.width, changes.height);
                        novelView.updateReadingStyle(newFont, changes.color);
                    }
                    recordRepository.saveConfig(currentWindowState(), readingData.getConfig());
                    refreshReadingData();
                    if (readingVisible) {
                        loadPagesAsync(filePath, currentPage, getCurrentOffset());
                    }
                }).show();
    }

    private JLabel getSettingsLabel() {
        if (novelView != null) {
            return novelView.getLabel();
        }
        JLabel label = new JLabel();
        label.setFont(readingData.getConfig().getFont());
        label.setForeground(readingData.getConfig().getForeground());
        return label;
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
                returnHome();
            } else if (e.getButton() == MouseEvent.BUTTON1) {
                // EPUB 的左键由富文本组件接管，用于打开脚注和链接
                if (!novelView.isEpubMode()) {
                    showSettings();
                }
            } else if (e.getButton() == MouseEvent.BUTTON2) {
                // 中键显示章节目录
                showChapters();
            }
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (pages.isEmpty() || (novelView != null && novelView.isEpubMode())) {
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
            int offset = novelView.isEpubMode() ? novelView.getEpubPageIndex() : getCurrentOffset();
            int length = novelView.isEpubMode() ? novelView.getEpubPageCount() : totalLength;
            recordRepository.saveRecord(currentWindowState(), readingData.getConfig(), filePath, currentPage,
                    pages.size(), offset, length);
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
        readingData = recordRepository.loadRecord();
    }

    private void showChapters() {
        if (pages.isEmpty()) {
            return;
        }
        int chapterPage = novelView.isEpubMode()
                ? novelView.getCurrentEpubSectionIndex(pages.size())
                : currentPage;
        new ChapterDialog(frame, chapters, novelView.getReadingFont(), pages.size(), chapterPage, pageIndex -> {
            currentPage = Math.max(0, Math.min(pageIndex, pages.size() - 1));
            showPage();
            saveCurrentRecord();
        }).show();
    }

    private void saveAndExit() {
        saveCurrentRecord();
        cancelLoading();
        releaseEpubBook();
        frame.dispose();
        System.exit(0);
    }

    private void returnHome() {
        saveCurrentRecord();
        refreshReadingData();
        novelView.hide();
        homeView.updateNovelList(readingData);
        homeView.show();
    }

    private void changeEpubSection(int delta) {
        if (epubBook == null || epubBook.getSections().isEmpty()) {
            return;
        }
        int next = Math.max(0, Math.min(currentPage + delta, epubBook.getSections().size() - 1));
        if (next != currentPage) {
            currentPage = next;
            showPage();
            scheduleSaveCurrentRecord();
        }
    }

    private void openEpubLink(URL target) {
        if (target == null || epubBook == null) {
            return;
        }
        if (novelView.navigateEpubLink(target)) {
            scheduleSaveCurrentRecord();
            return;
        }
        if (!"file".equalsIgnoreCase(target.getProtocol())) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(target.toURI());
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "无法打开链接：" + ex.getMessage(), "链接", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }

        try {
            String targetPath = new File(new URI(target.getProtocol(), target.getAuthority(), target.getPath(), null, null))
                    .getCanonicalPath();
            for (int i = 0; i < epubBook.getSections().size(); i++) {
                URL section = epubBook.getSections().get(i);
                File sectionFile = new File(new URI(section.getProtocol(), section.getAuthority(),
                        section.getPath(), null, null));
                if (sectionFile.getCanonicalPath().equals(targetPath)) {
                    currentPage = i;
                    novelView.showEpub(target);
                    scheduleSaveCurrentRecord();
                    return;
                }
            }
            novelView.showEpub(target);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "无法打开 EPUB 链接：" + ex.getMessage(), "链接", JOptionPane.WARNING_MESSAGE);
        }
    }

    private boolean isEpubFile(String path) {
        return path != null && path.toLowerCase(java.util.Locale.ROOT).endsWith(".epub");
    }

    private void cancelLoading() {
        if (pageWorker != null && !pageWorker.isDone()) {
            pageWorker.cancel(true);
        }
        if (epubWorker != null && !epubWorker.isDone()) {
            epubWorker.cancel(true);
        }
    }

    private void releaseEpubBook() {
        if (epubBook != null) {
            epubBook.close();
            epubBook = null;
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
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

    private WindowState currentWindowState() {
        return new WindowState(frame.getWidth(), frame.getHeight(), frame.getX(), frame.getY());
    }

    private void applyWindowState(WindowState windowState) {
        if (windowState == null) {
            return;
        }
        frame.setSize(windowState.getWidth(), windowState.getHeight());
        frame.setLocation(windowState.getLocationX(), windowState.getLocationY());
    }

    public static void main(String[] args) {
        new NovelReader();
    }
}
