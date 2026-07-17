package com.falcon.reader.model;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.falcon.reader.entity.novelItem.NovelItem;
import com.falcon.reader.entity.novelItem.NovelItemRenderer;
import com.falcon.reader.entity.NovelRecord;
import com.falcon.reader.util.UIUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.basic.BasicMenuUI;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.Collator;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 主页视图，显示小说列表和按钮
 *
 * @author zxy
 * @date 2026/2/11 16:16
 */
public class HomeView {
    private static final int SEARCH_X = 70;
    private static final int SEARCH_WIDTH = 100;
    private static final String SELECTED_MENU_ITEM_PROPERTY = "selectedMenuItem";

    private JFrame frame;
    private JButton openButton;
    private JButton menuButton;
    private JButton closeButton;
    private JTextField searchField;
    private JLabel groupHintLabel;
    private JScrollPane scrollPane;
    private JLabel emptyResultLabel;
    private Consumer<String> openNovelCallback;
    private Consumer<ReadingData> readingDataChangeCallback;
    private Runnable settingsCallback;
    private Runnable closeCallback;
    private ReadingData readingData;
    private SortMode sortMode = SortMode.LAST_READING_TIME;
    private GroupMode groupMode = GroupMode.ALL;

    /**
     * 构造函数，初始化主页视图
     * @param frame 主窗口
     * @param openNovelCallback 打开小说回调函数
     * @param readingData
     * @author zxy
     */
    public HomeView(JFrame frame, Consumer<String> openNovelCallback, Runnable closeCallback, ReadingData readingData) {
        this(frame, openNovelCallback, closeCallback, readingData, null, null);
    }

    public HomeView(JFrame frame, Consumer<String> openNovelCallback, Runnable closeCallback, ReadingData readingData,
            Consumer<ReadingData> readingDataChangeCallback) {
        this(frame, openNovelCallback, closeCallback, readingData, readingDataChangeCallback, null);
    }

    public HomeView(JFrame frame, Consumer<String> openNovelCallback, Runnable closeCallback, ReadingData readingData,
            Consumer<ReadingData> readingDataChangeCallback, Runnable settingsCallback) {
        this.frame = frame;
        this.openNovelCallback = openNovelCallback;
        this.closeCallback = closeCallback;
        this.readingData = readingData;
        this.readingDataChangeCallback = readingDataChangeCallback;
        this.settingsCallback = settingsCallback;
        initComponents();
    }

    /**
     * 初始化组件，包括添加打开按钮、关闭按钮，并更新小说列表
     * @author zxy
     * @date 2024/10/21
     */
    private void initComponents() {
        // 创建并配置打开新文件按钮
        openButton = UIUtils.createStyledButton("打开", 6, 10, 52, 35);
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

        searchField = new SearchField("搜索文件名");
        searchField.setBounds(SEARCH_X, 10, SEARCH_WIDTH, 35);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateNovelList(readingData);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateNovelList(readingData);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateNovelList(readingData);
            }
        });
        frame.add(searchField);

        groupHintLabel = createGroupHintLabel();
        updateGroupHint();
        frame.add(groupHintLabel);

        menuButton = createMenuButton();
        menuButton.setBounds(frame.getWidth() - 65, 10, 25, 25);
        menuButton.setMargin(new Insets(0, 0, 0, 0));
        menuButton.addActionListener(e -> showHomeMenu());
        frame.add(menuButton);

        // 创建并配置关闭按钮
        closeButton = UIUtils.createStyledButton("×", frame.getWidth() - 35, 10, 25, 25);
        closeButton.setFont(new Font("Serif", Font.PLAIN, 18));
        closeButton.addActionListener(e -> closeCallback.run());
        frame.add(closeButton);

        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                updateBounds();
            }
        });

        // 更新小说列表显示
        updateNovelList(readingData);
    }

    /**
     * 更新小说列表，根据提供的配置和记录对刷新列表内容
     * @param readingData
     * @author zxy
     */
    public void updateNovelList(ReadingData readingData) {
        // 如果滚动面板已存在，先移除它
        if (scrollPane != null) {
            frame.remove(scrollPane);
        }
        if (emptyResultLabel != null) {
            frame.remove(emptyResultLabel);
            emptyResultLabel = null;
        }
        this.readingData = readingData;

        DefaultListModel<NovelItem> listModel = new DefaultListModel<>();
        // 将阅读记录转换为NovelItem并添加到模型中
        if (CollectionUtil.isNotEmpty(readingData.getRecords())) {
            List<Map.Entry<String, NovelRecord>> records = new ArrayList<>();
            readingData.getRecords().forEach((path, record) -> {
                if (matchesGroup(path, record) && matchesSearch(path)) {
                    records.add(new java.util.AbstractMap.SimpleEntry<>(path, record));
                }
            });
            sortRecords(records);
            records.forEach(entry -> listModel.addElement(new NovelItem(entry.getKey(), entry.getValue())));
        }

        if ((isSearchActive() || groupMode != GroupMode.ALL) && listModel.isEmpty()) {
            emptyResultLabel = createEmptyResultLabel();
            updateBounds();
            frame.add(emptyResultLabel);
            frame.revalidate();
            frame.repaint();
            return;
        }

        // 创建小说列表组件
        JList<NovelItem> list = new JList<>(listModel);
        list.setCellRenderer(new NovelItemRenderer());
        list.setFont(new Font("Serif", Font.PLAIN, 16));
        list.setBackground(new Color(0, 0, 0, 0));
        list.setForeground(Color.WHITE);
        list.setOpaque(false);
        list.setFixedCellHeight(36);  // 示例高度，根据 renderer 调整
        list.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index != -1 && list.getCellBounds(index, index).contains(e.getPoint())) {
                    list.setToolTipText(listModel.get(index).getFullPath());
                } else {
                    list.setToolTipText(null);
                }
            }
        });
        // 添加鼠标监听器处理点击事件
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index != -1 && list.getCellBounds(index, index).contains(e.getPoint())) {
                    NovelItem item = listModel.get(index);
                    String path = item.getFullPath();
                    if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                        // 左键点击打开小说
                        if (Files.isRegularFile(Paths.get(path))) {
                            openNovelCallback.accept(path);
                        } else {
                            JOptionPane.showMessageDialog(frame, "文件不存在，可能已被移动或删除。", "无法打开", JOptionPane.WARNING_MESSAGE);
                            updateNovelList(readingData);
                        }
                    } else if (e.getButton() == java.awt.event.MouseEvent.BUTTON3) {
                        showNovelMenu(list, e.getX(), e.getY(), item);
                    }
                }
            }
        });

        // 创建滚动面板并配置样式
        scrollPane = new JScrollPane(list);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        updateBounds();

        // 设置滚动条透明度
        UIUtils.setScrollbarTransparency(scrollPane.getVerticalScrollBar(), 4, scrollPane.getVerticalScrollBar().getHeight());
        UIUtils.setScrollbarTransparency(scrollPane.getHorizontalScrollBar(), scrollPane.getHorizontalScrollBar().getWidth(), 4);

        frame.add(scrollPane);
        frame.revalidate();
        frame.repaint();
    }

    private boolean matchesSearch(String filePath) {
        if (searchField == null || StrUtil.isBlank(searchField.getText())) {
            return true;
        }

        String keyword = searchField.getText().trim().toLowerCase(Locale.ROOT);
        String normalizedPath = filePath.toLowerCase(Locale.ROOT);
        Path path = Paths.get(filePath);
        String fileName = path.getFileName() == null ? filePath : path.getFileName().toString();
        return fileName.toLowerCase(Locale.ROOT).contains(keyword) || normalizedPath.contains(keyword);
    }

    private boolean isSearchActive() {
        return searchField != null && StrUtil.isNotBlank(searchField.getText());
    }

    private JLabel createEmptyResultLabel() {
        JLabel label = new JLabel(getEmptyResultText(), SwingConstants.CENTER);
        label.setForeground(new Color(165, 165, 165));
        label.setFont(new Font("Serif", Font.PLAIN, 14));
        label.setOpaque(false);
        return label;
    }

    private String getEmptyResultText() {
        if (isSearchActive()) {
            return "没有匹配的书籍";
        }
        return "当前筛选项下没有书籍";
    }

    private void showNovelMenu(JList<NovelItem> list, int x, int y, NovelItem item) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem relocateItem = new JMenuItem("重新定位文件");
        JMenuItem openFolderItem = new JMenuItem("打开所在文件夹");
        JMenuItem deleteItem = new JMenuItem("删除记录");
        compactMenuItem(relocateItem);
        compactMenuItem(openFolderItem);
        compactMenuItem(deleteItem);

        relocateItem.addActionListener(e -> relocateNovel(item));
        openFolderItem.addActionListener(e -> openNovelFolder(item));
        deleteItem.addActionListener(e -> deleteNovel(item));

        menu.add(relocateItem);
        menu.add(openFolderItem);
        menu.add(deleteItem);
        menu.show(list, x, y);
    }

    private void showHomeMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenu sortMenu = new JMenu("排序");
        JMenu filterMenu = new JMenu("筛选");
        JMenuItem settingsItem = new JMenuItem("设置");
        JMenuItem sortByNameItem = new JMenuItem("按名称排序");
        JMenuItem sortByTimeItem = new JMenuItem("按最后阅读时间排序");
        JMenuItem filterAllItem = new JMenuItem("全部");
        JMenuItem filterUnreadItem = new JMenuItem("未读");
        JMenuItem filterReadingItem = new JMenuItem("阅读中");
        JMenuItem filterFinishedItem = new JMenuItem("已读");
        JMenuItem filterMissingItem = new JMenuItem("失效");
        compactSubMenu(sortMenu);
        compactSubMenu(filterMenu);
        compactMenuItem(settingsItem);
        compactMenuItem(sortByNameItem);
        compactMenuItem(sortByTimeItem);
        compactMenuItem(filterAllItem);
        compactMenuItem(filterUnreadItem);
        compactMenuItem(filterReadingItem);
        compactMenuItem(filterFinishedItem);
        compactMenuItem(filterMissingItem);
        markSelected(sortByNameItem, sortMode == SortMode.NAME);
        markSelected(sortByTimeItem, sortMode == SortMode.LAST_READING_TIME);
        markSelected(filterAllItem, groupMode == GroupMode.ALL);
        markSelected(filterUnreadItem, groupMode == GroupMode.UNREAD);
        markSelected(filterReadingItem, groupMode == GroupMode.READING);
        markSelected(filterFinishedItem, groupMode == GroupMode.FINISHED);
        markSelected(filterMissingItem, groupMode == GroupMode.MISSING);
        settingsItem.addActionListener(e -> {
            if (settingsCallback != null) {
                settingsCallback.run();
            }
        });
        sortByNameItem.addActionListener(e -> applySort(SortMode.NAME));
        sortByTimeItem.addActionListener(e -> applySort(SortMode.LAST_READING_TIME));
        filterAllItem.addActionListener(e -> applyGroup(GroupMode.ALL));
        filterUnreadItem.addActionListener(e -> applyGroup(GroupMode.UNREAD));
        filterReadingItem.addActionListener(e -> applyGroup(GroupMode.READING));
        filterFinishedItem.addActionListener(e -> applyGroup(GroupMode.FINISHED));
        filterMissingItem.addActionListener(e -> applyGroup(GroupMode.MISSING));

        sortMenu.add(sortByNameItem);
        sortMenu.add(sortByTimeItem);
        filterMenu.add(filterAllItem);
        filterMenu.add(filterUnreadItem);
        filterMenu.add(filterReadingItem);
        filterMenu.add(filterFinishedItem);
        filterMenu.add(filterMissingItem);
        menu.add(sortMenu);
        menu.add(filterMenu);
        menu.addSeparator();
        menu.add(settingsItem);
        menu.show(menuButton, 0, menuButton.getHeight());
    }

    private void applySort(SortMode sortMode) {
        this.sortMode = sortMode;
        updateNovelList(readingData);
    }

    private void applyGroup(GroupMode groupMode) {
        this.groupMode = groupMode;
        updateGroupHint();
        updateNovelList(readingData);
    }

    private JLabel createGroupHintLabel() {
        JLabel label = new JLabel();
        label.setForeground(new Color(165, 185, 170));
        label.setFont(new Font("Serif", Font.PLAIN, 13));
        label.setOpaque(false);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        label.setToolTipText("点击清除筛选");
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (groupMode != GroupMode.ALL) {
                    applyGroup(GroupMode.ALL);
                }
            }
        });
        return label;
    }

    private void updateGroupHint() {
        if (groupHintLabel == null) {
            return;
        }
        groupHintLabel.setText(groupMode == GroupMode.ALL ? "" : groupMode.getDisplayName());
        groupHintLabel.setVisible(groupMode != GroupMode.ALL);
        updateBounds();
    }

    private JButton createMenuButton() {
        JButton button = new JButton("⋮");
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Dialog", Font.BOLD, 18));
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorder(null);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void compactMenuItem(JMenuItem menuItem) {
        menuItem.setUI(new CompactMenuItemUI());
        menuItem.setIconTextGap(0);
        menuItem.setMargin(new Insets(3, 8, 3, 12));
    }

    private void compactSubMenu(JMenu menu) {
        menu.setUI(new CompactMenuUI());
        menu.setIconTextGap(0);
        menu.setMargin(new Insets(3, 8, 3, 12));
    }

    private void markSelected(JMenuItem menuItem, boolean selected) {
        menuItem.putClientProperty(SELECTED_MENU_ITEM_PROPERTY, selected);
    }

    private static class CompactMenuUI extends BasicMenuUI {
        private static final int ARROW_WIDTH = 14;

        @Override
        protected void installDefaults() {
            super.installDefaults();
            checkIcon = null;
            arrowIcon = null;
            defaultTextIconGap = 0;
        }

        @Override
        protected Dimension getPreferredMenuItemSize(JComponent c, Icon checkIcon, Icon arrowIcon, int defaultTextIconGap) {
            JMenu menu = (JMenu) c;
            FontMetrics metrics = menu.getFontMetrics(menu.getFont());
            Insets insets = menu.getInsets();
            int width = metrics.stringWidth(menu.getText()) + insets.left + insets.right + ARROW_WIDTH;
            int height = metrics.getHeight() + insets.top + insets.bottom;
            return new Dimension(width, height);
        }

        @Override
        protected void paintMenuItem(Graphics g, JComponent c, Icon checkIcon, Icon arrowIcon,
                Color background, Color foreground, int defaultTextIconGap) {
            JMenu menu = (JMenu) c;
            ButtonModel model = menu.getModel();
            boolean selected = model.isArmed() || model.isSelected();

            g.setColor(selected ? selectionBackground : menu.getBackground());
            g.fillRect(0, 0, menu.getWidth(), menu.getHeight());

            g.setFont(menu.getFont());
            g.setColor(selected ? selectionForeground : menu.getForeground());
            FontMetrics metrics = g.getFontMetrics();
            Insets insets = menu.getInsets();
            int textY = (menu.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(menu.getText(), insets.left, textY);
            g.drawString("›", menu.getWidth() - insets.right - metrics.stringWidth("›"), textY);
        }
    }

    private static class CompactMenuItemUI extends BasicMenuItemUI {
        private static final int CHECK_WIDTH = 18;
        private static final int ARROW_WIDTH = 14;

        @Override
        protected void installDefaults() {
            super.installDefaults();
            checkIcon = null;
            arrowIcon = null;
            defaultTextIconGap = 0;
        }

        @Override
        protected Dimension getPreferredMenuItemSize(JComponent c, Icon checkIcon, Icon arrowIcon, int defaultTextIconGap) {
            JMenuItem menuItem = (JMenuItem) c;
            FontMetrics metrics = menuItem.getFontMetrics(menuItem.getFont());
            Insets insets = menuItem.getInsets();
            int width = metrics.stringWidth(menuItem.getText()) + insets.left + insets.right;
            if (isSelectedMenuItem(menuItem)) {
                width += CHECK_WIDTH;
            }
            if (menuItem instanceof JMenu) {
                width += ARROW_WIDTH;
            }
            int height = metrics.getHeight() + insets.top + insets.bottom;
            return new Dimension(width, height);
        }

        @Override
        protected void paintMenuItem(Graphics g, JComponent c, Icon checkIcon, Icon arrowIcon,
                Color background, Color foreground, int defaultTextIconGap) {
            JMenuItem menuItem = (JMenuItem) c;
            ButtonModel model = menuItem.getModel();
            boolean selected = model.isArmed() || model.isSelected();

            g.setColor(selected ? selectionBackground : menuItem.getBackground());
            g.fillRect(0, 0, menuItem.getWidth(), menuItem.getHeight());

            g.setFont(menuItem.getFont());
            g.setColor(selected ? selectionForeground : menuItem.getForeground());
            FontMetrics metrics = g.getFontMetrics();
            Insets insets = menuItem.getInsets();
            int textY = (menuItem.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(menuItem.getText(), insets.left, textY);

            int right = menuItem.getWidth() - insets.right;
            if (menuItem instanceof JMenu) {
                g.drawString("›", right - metrics.stringWidth("›"), textY);
                right -= ARROW_WIDTH;
            }
            if (isSelectedMenuItem(menuItem)) {
                g.drawString("✓", right - metrics.stringWidth("✓"), textY);
            }
        }

        private static boolean isSelectedMenuItem(JMenuItem menuItem) {
            return Boolean.TRUE.equals(menuItem.getClientProperty(SELECTED_MENU_ITEM_PROPERTY));
        }
    }

    private void openNovelFolder(NovelItem item) {
        File file = new File(item.getFullPath());
        File folder = file.getParentFile();
        if (folder == null || !folder.isDirectory()) {
            JOptionPane.showMessageDialog(frame, "所在文件夹不存在，可能已被移动或删除。", "无法打开", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            if (file.isFile() && isWindows()) {
                new ProcessBuilder("explorer.exe", "/select," + file.getAbsolutePath()).start();
                return;
            }

            if (!Desktop.isDesktopSupported()) {
                JOptionPane.showMessageDialog(frame, "当前系统不支持打开文件夹。", "无法打开", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Desktop.getDesktop().open(folder);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "打开所在文件夹失败: " + ex.getMessage(), "无法打开", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private void sortRecords(List<Map.Entry<String, NovelRecord>> records) {
        if (sortMode == SortMode.NAME) {
            Collator collator = Collator.getInstance(Locale.CHINA);
            records.sort((first, second) -> collator.compare(getFileName(first.getKey()), getFileName(second.getKey())));
            return;
        }

        records.sort((first, second) -> compareLastReadingTime(first.getValue(), second.getValue()));
    }

    private int compareLastReadingTime(NovelRecord first, NovelRecord second) {
        LocalDateTime firstTime = first == null ? null : first.getLastReadingTime();
        LocalDateTime secondTime = second == null ? null : second.getLastReadingTime();
        if (firstTime == null && secondTime == null) {
            return 0;
        }
        if (firstTime == null) {
            return 1;
        }
        if (secondTime == null) {
            return -1;
        }
        return secondTime.compareTo(firstTime);
    }

    private String getFileName(String filePath) {
        Path path = Paths.get(filePath);
        return path.getFileName() == null ? filePath : path.getFileName().toString();
    }

    private boolean matchesGroup(String path, NovelRecord record) {
        switch (groupMode) {
            case UNREAD:
                return Files.isRegularFile(Paths.get(path)) && isUnread(record);
            case READING:
                return Files.isRegularFile(Paths.get(path)) && !isUnread(record) && !isFinished(record);
            case FINISHED:
                return Files.isRegularFile(Paths.get(path)) && isFinished(record);
            case MISSING:
                return !Files.isRegularFile(Paths.get(path));
            case ALL:
            default:
                return true;
        }
    }

    private boolean isFinished(NovelRecord record) {
        if (record != null && record.getCurrentOffset() != null && record.getTotalLength() != null
                && record.getTotalLength() > 0 && record.getCurrentOffset() >= record.getTotalLength()) {
            return true;
        }
        if (record == null || record.getCurrentPage() == null || record.getTotalPages() == null || record.getTotalPages() <= 0) {
            return false;
        }
        return record.getCurrentPage() + 1 >= record.getTotalPages();
    }

    private boolean isUnread(NovelRecord record) {
        if (record != null && record.getCurrentOffset() != null) {
            return record.getCurrentOffset() <= 0;
        }
        return record == null || record.getCurrentPage() == null || record.getCurrentPage() <= 0;
    }

    private enum SortMode {
        NAME,
        LAST_READING_TIME
    }

    private enum GroupMode {
        ALL("全部"),
        UNREAD("未读"),
        READING("阅读中"),
        FINISHED("已读"),
        MISSING("失效");

        private final String displayName;

        GroupMode(String displayName) {
            this.displayName = displayName;
        }

        private String getDisplayName() {
            return displayName;
        }
    }

    private static class SearchField extends JTextField {
        private static final int CLEAR_HIT_SIZE = 24;
        private final String placeholder;
        private boolean focused = false;

        private SearchField(String placeholder) {
            this.placeholder = placeholder;
            setToolTipText(placeholder);
            setForeground(Color.WHITE);
            setCaretColor(Color.WHITE);
            setFont(new Font("Serif", Font.PLAIN, 14));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 24));
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusGained(java.awt.event.FocusEvent e) {
                    focused = true;
                    repaint();
                }

                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    focused = false;
                    repaint();
                }
            });
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (isClearHit(e.getX(), e.getY())) {
                        setText("");
                    }
                }
            });
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(java.awt.event.MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(isClearHit(e.getX(), e.getY()) ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR));
                }
            });
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                }
            });
        }

        private boolean isClearHit(int x, int y) {
            return StrUtil.isNotBlank(getText()) && x >= getWidth() - CLEAR_HIT_SIZE && y >= 0 && y <= getHeight();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(focused ? new Color(160, 190, 170) : new Color(92, 96, 102));
            g2.drawLine(0, getHeight() - 2, getWidth() - 1, getHeight() - 2);
            g2.dispose();

            super.paintComponent(g);

            if (StrUtil.isBlank(getText())) {
                Graphics2D placeholderGraphics = (Graphics2D) g.create();
                placeholderGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                placeholderGraphics.setFont(getFont());
                placeholderGraphics.setColor(new Color(160, 160, 160));
                FontMetrics metrics = placeholderGraphics.getFontMetrics();
                int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                placeholderGraphics.drawString(placeholder, 0, y);
                placeholderGraphics.dispose();
            } else {
                Graphics2D clearGraphics = (Graphics2D) g.create();
                clearGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                clearGraphics.setFont(new Font("Dialog", Font.PLAIN, 14));
                clearGraphics.setColor(new Color(185, 185, 185));
                FontMetrics metrics = clearGraphics.getFontMetrics();
                String clearText = "×";
                int x = getWidth() - 18;
                int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                clearGraphics.drawString(clearText, x, y);
                clearGraphics.dispose();
            }
        }
    }

    private void relocateNovel(NovelItem item) {
        JFileChooser fileChooser = new JFileChooser(".");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件 (*.txt)", "txt"));
        if (fileChooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String oldPath = item.getFullPath();
        String newPath = fileChooser.getSelectedFile().getAbsolutePath();
        if (StrUtil.isBlank(newPath) || !newPath.toLowerCase().endsWith(".txt")) {
            JOptionPane.showMessageDialog(frame, "无效的文件！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!Files.isRegularFile(Paths.get(newPath))) {
            JOptionPane.showMessageDialog(frame, "文件不存在或无法读取。", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (oldPath.equals(newPath)) {
            return;
        }
        if (readingData.getRecords().containsKey(newPath)) {
            JOptionPane.showMessageDialog(frame, "该文件已经在书架中。", "无法重新定位", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean relocated = ReadingRecord.relocateRecord(frame, oldPath, newPath);
        if (relocated) {
            readingData = ReadingRecord.loadRecord(frame);
            notifyReadingDataChanged();
            updateNovelList(readingData);
        } else {
            JOptionPane.showMessageDialog(frame, "未找到可更新的阅读记录。", "重新定位失败", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void deleteNovel(NovelItem item) {
        String path = item.getFullPath();
        int confirm = JOptionPane.showConfirmDialog(frame, "是否删除 “" + item.getFileName() + "” ？", "删除记录", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            readingData.getRecords().remove(path);
            ReadingRecord.deleteRecord(frame, path);
            notifyReadingDataChanged();
            updateNovelList(readingData);
        }
    }

    private void notifyReadingDataChanged() {
        if (readingDataChangeCallback != null) {
            readingDataChangeCallback.accept(readingData);
        }
    }

    private void updateBounds() {
        if (closeButton != null) {
            closeButton.setBounds(Math.max(0, frame.getWidth() - 35), 10, 25, 25);
        }
        if (menuButton != null) {
            menuButton.setBounds(Math.max(0, frame.getWidth() - 65), 10, 25, 25);
        }
        if (searchField != null) {
            searchField.setBounds(SEARCH_X, 10, Math.min(SEARCH_WIDTH, Math.max(0, frame.getWidth() - SEARCH_X - 75)), 35);
        }
        if (groupHintLabel != null) {
            FontMetrics metrics = groupHintLabel.getFontMetrics(groupHintLabel.getFont());
            int hintWidth = Math.min(metrics.stringWidth(groupHintLabel.getText()) + 8, 110);
            int hintX = Math.max(SEARCH_X + SEARCH_WIDTH + 8, frame.getWidth() - 65 - hintWidth - 8);
            groupHintLabel.setBounds(hintX, 12, hintWidth, 25);
        }
        if (scrollPane != null) {
            scrollPane.setBounds(0, 50, Math.max(0, frame.getWidth() - 10), Math.max(0, frame.getHeight() - 60));
        }
        if (emptyResultLabel != null) {
            emptyResultLabel.setBounds(0, 50, Math.max(0, frame.getWidth() - 10), Math.max(0, frame.getHeight() - 60));
        }
    }

    public void refreshLayout() {
        updateBounds();
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
        menuButton.setVisible(true);
        closeButton.setVisible(true);
        searchField.setVisible(true);
        groupHintLabel.setVisible(groupMode != GroupMode.ALL);
        if (scrollPane != null) {
            scrollPane.setVisible(true);
        }
        if (emptyResultLabel != null) {
            emptyResultLabel.setVisible(true);
        }
    }

    /**
     * 隐藏主页视图组件
     * @author zxy
     * @date 2024/10/21
     */
    public void hide() {
        openButton.setVisible(false);
        menuButton.setVisible(false);
        closeButton.setVisible(false);
        searchField.setVisible(false);
        groupHintLabel.setVisible(false);
        if (scrollPane != null) {
            scrollPane.setVisible(false);
        }
        if (emptyResultLabel != null) {
            emptyResultLabel.setVisible(false);
        }
    }
}
