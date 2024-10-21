package com.falcon.reader;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.falcon.reader.entity.NovelRecord;
import com.falcon.reader.entity.novelItem.NovelItem;
import com.falcon.reader.entity.novelItem.NovelItemRenderer;
import com.falcon.reader.model.ReadingRecord;
import com.falcon.reader.util.EncodingDetect;
import com.falcon.reader.util.NumericDocumentFilter;
import javafx.util.Pair;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.List;
import java.util.*;

/**
 * @author zxy
 * @date 2024/10/17
 */
public class NovelReader implements MouseListener, MouseMotionListener, MouseWheelListener {
    int x, y;
    Integer fontSize, fontStyle, width, height;
    private String filePath = null;
    private Color selectedColor = null;
    private JFrame frame;
    private JButton openButton;
    private JButton closeButton;
    private JScrollPane scrollPane;
    private JLabel label;
    private int currentPage = 0;
    private Map<String, NovelRecord> novelRecordMap = new LinkedHashMap<>();
    private List<String> pages = new ArrayList<>();


    NovelReader() {
        try {
            // 设置UI样式为系统默认样式
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                UnsupportedLookAndFeelException e) {
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
        //        jFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // 不设置默认关闭操作，以便我们可以自定义

        openHome();

        frame.setVisible(true);
    }

    /**
     * 打开主页
     * @author zxy
     * @date 2024/10/21
     */
    private void openHome(){
        novelRecordMap = ReadingRecord.loadRecord(frame);
        addOpenButton();
        addCloseButton();
        addNovelScrollList();
    }

    /**
     * 添加打开按钮
     * @author zxy
     * @date 2024/10/21
     */
    private void addOpenButton(){
        openButton = new JButton("打开新文件");
        openButton.setBounds(6, 10, 80, 35);
        openButton.setForeground(Color.WHITE); // 设置按钮背景色
        openButton.setFont(new Font("Serif", Font.PLAIN, 13));
        openButton.setContentAreaFilled(false);// 移除内容区域填充
        openButton.setOpaque(false);// 设为不透明
        openButton.setBorder(new LineBorder(Color.GRAY, 1));// 设置灰色边框
        openButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));// 设置光标为手型
        openButton.addActionListener(e -> {
            // 创建一个文件选择器
            JFileChooser fileChooser = new JFileChooser();
            // 设置文件选择器默认目录为当前目录
            fileChooser.setCurrentDirectory(new File("."));
            // 设置文件过滤器，只允许选择txt文件
            FileNameExtensionFilter txtFilter = new FileNameExtensionFilter("文本文件 (*.txt)", "txt");
            fileChooser.setFileFilter(txtFilter);

            // 显示文件选择器
            int result = fileChooser.showOpenDialog(frame);

            // 检查用户是否点击了打开按钮
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                filePath = selectedFile.getAbsolutePath();
                openNovel();
            }
        });

        frame.add(openButton);
    }

    /**
     * 添加关闭按钮
     * @author zxy
     * @date 2024/10/21
     */
    private void addCloseButton() {
        closeButton = new JButton("×");
        closeButton.setBounds(frame.getSize().width - 35, 10, 25, 25);
        // 设置按钮背景色
        closeButton.setForeground(Color.WHITE);
        closeButton.setFont(new Font("Serif", Font.PLAIN, 18));
        closeButton.setContentAreaFilled(false); // 移除内容区域填充
        closeButton.setOpaque(false); // 设为不透明
        // 设置白色边框
        closeButton.setBorder(new LineBorder(Color.GRAY, 1));
        // 设置光标为手型
        closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> System.exit(1));

        frame.add(closeButton);
    }

    /**
     * 添加滚动列表
     * @author zxy
     * @date 2024/10/21
     */
    private void addNovelScrollList(){
        List<String> novelList = new ArrayList<>();
        if(CollectionUtil.isNotEmpty(novelRecordMap)) {
            novelList = new ArrayList<>(novelRecordMap.keySet());
        }
        DefaultListModel<NovelItem> listModel = new DefaultListModel<>();
        for (String item : novelList) {
            listModel.addElement(new NovelItem(item));
        }
        JList<NovelItem> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        NovelItemRenderer renderer = new NovelItemRenderer();
        list.setCellRenderer(renderer);
        list.setFont(new Font("Serif", Font.PLAIN, 16));
        list.setBackground(new Color(0, 0, 0, 0)); // 设置透明背景
        list.setForeground(Color.WHITE);

        // 根据文本设置项的宽度
        int maxWidth = 0;
        int maxHeight = 0;
        for (int i = 0; i < listModel.size(); i++) {
            Dimension size = renderer.getPreferredSizeForItem(listModel.get(i));
            if (size.width > maxWidth) {
                maxWidth = size.width;
            }
            if (size.height > maxHeight) {
                maxHeight = size.height;
            }
        }

        // 设置每个项的高度
        list.setFixedCellHeight(maxHeight + 5);
        // 设置每个项的宽度
        list.setFixedCellWidth(maxWidth);
        list.setPreferredSize(new Dimension(maxWidth, list.getPreferredSize().height));
        // 设为不透明
        list.setOpaque(false);

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                Rectangle bounds = list.getCellBounds(index, index);
                if (bounds.contains(e.getPoint())) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        list.setSelectedIndex(index);
                        filePath =
                                list.getModel().getElementAt(index).getFilePath() + list.getModel().getElementAt(index).getFileName();
                        openNovel();
                    } else if (e.getButton() == MouseEvent.BUTTON3) {
                        list.setSelectedIndex(index);
                        int confirm = JOptionPane.showConfirmDialog(frame, "是否删除“" + list.getModel().getElementAt(index).getFileName() + "”？",
                                "删除记录", JOptionPane.YES_NO_OPTION);

                        if (confirm == JOptionPane.YES_OPTION) {
                            // 执行删除操作
                            if (index != -1) {
                                filePath = list.getModel().getElementAt(index).getFilePath() + list.getModel().getElementAt(index).getFileName();
                                // 移除选中的项
                                listModel.remove(index);
                                novelRecordMap.remove(filePath);
                                // 删除文件中的该项
                                ReadingRecord.deleteRecord(frame, filePath);
                            }
                        }
                    }
                }
            }
        });

        // 将列表放入滚动面板
        scrollPane = new JScrollPane(list);
        scrollPane.setPreferredSize(new Dimension(220, list.getPreferredSize().height + 20));
        scrollPane.setBounds(0, 50, frame.getSize().width - 10, frame.getSize().height - 60);
        scrollPane.setPreferredSize(new Dimension(10, 20));
        scrollPane.setOpaque(false); // 设为不透明
        scrollPane.getViewport().setOpaque(false); // 设为透明
        scrollPane.setBorder(null); // 移除边框

        // 设置垂直滚动条透明和宽度等样式
        JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
        setScrollbarTransparency(verticalScrollBar, 4, verticalScrollBar.getHeight());

        // 设置水平滚动条透明和高度等样式
        JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();
        setScrollbarTransparency(horizontalScrollBar, horizontalScrollBar.getWidth(), 4);

        frame.add(scrollPane);
    }

    /**
     * 打开小说
     * @author zxy
     * @date 2024/10/21
     */
    private void openNovel() {
        if (StrUtil.isNotBlank(filePath)) {
            frame.remove(openButton);
            frame.remove(closeButton);
            frame.remove(scrollPane);
            frame.revalidate(); // 更新界面
            frame.repaint();

            if(novelRecordMap.containsKey(filePath)) {
                currentPage = novelRecordMap.get(filePath).getCurrentPage();
            }

            label = new JLabel();
            label.setLayout(new FlowLayout());
            label.setBounds(0, 0, frame.getSize().width, frame.getSize().height);
            label.setFont(new Font("Serif", Font.PLAIN, 15));
            label.setForeground(Color.WHITE);
            label.setVerticalAlignment(JLabel.TOP); // 或者 JLabel.CENTER, JLabel.BOTTOM
            label.setVerticalTextPosition(JLabel.TOP); // 或者 JLabel.CENTER, JLabel.BOTTOM
            frame.add(label);

            calculationPages();
            showPage();
        }
    }

    /**
     * 设置滚动条样式和透明
     * @param scrollBar
     * @param width
     * @param height
     * @author zxy
     * @date 2024/10/21
     */
    private void setScrollbarTransparency(JScrollBar scrollBar, int width, int height){
        scrollBar.setOpaque(false);
        scrollBar.setBackground(new Color(0, 0, 0, 0)); // 设置滚动条透明背景
        scrollBar.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = Color.LIGHT_GRAY; // 设置滚动条滑块颜色
                this.trackColor = new Color(0, 0, 0, 0); // 设置滚动条轨道颜色
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                JButton button = new JButton() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        // 透明背景
                        g.setColor(new Color(0, 0, 0, 0));
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
                button.setPreferredSize(new Dimension(0, 0)); // 设置大小为零
                button.setBorder(null); // 移除边框
                button.setOpaque(false); // 设为透明
                return button;
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                JButton button = new JButton() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        // 透明背景
                        g.setColor(new Color(0, 0, 0, 0));
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
                button.setPreferredSize(new Dimension(0, 0)); // 设置大小为零
                button.setBorder(null); // 移除边框
                button.setOpaque(false); // 设为透明
                return button;
            }

            @Override
            public Dimension getPreferredSize(JComponent c) {
                return new Dimension(width, height); // 设置滚动条宽度为10
            }
        });
    }

    /**
     * 计算页内容
     * @author zxy
     * @date 2024/10/21
     */
    private void calculationPages(){
        pages.clear();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath),
                    EncodingDetect.getJavaEncode(filePath)));
            String line = reader.readLine();
            StringBuilder text = new StringBuilder();

            // 获取字体信息
            FontMetrics fontMetrics = label.getFontMetrics(label.getFont());
            // 获取单个中文字符的宽度和高度
            int charWidth = fontMetrics.charWidth('测'); // 使用中文字符计算宽度
            int charHeight = fontMetrics.getHeight(); // 字符高度
            // 计算每行最多能显示的中文字数
            int charsPerLine = label.getWidth() / charWidth;
            // 计算最多能显示的行数
            int maxLines = label.getHeight() / charHeight;

            while (line != null) {
                text.append("<html>");
                int i = 0;
                do{
                    int lineCount;
                    if((line.length() % charsPerLine) != 0) {
                        lineCount = line.length() / charsPerLine + 1;
                    } else{
                        lineCount = line.length() / charsPerLine == 0 ? 1 : line.length() / charsPerLine;
                    }
                    i += lineCount;
                    if (i > maxLines){
                        int endIndex = charsPerLine * (lineCount - (i - maxLines));
                        text.append(line, 0, endIndex).append("<br/>");
                        line = line.substring(endIndex);
                        break;
                    }
                    text.append(line).append("<br/>");
                }while ((line = reader.readLine()) != null);
                pages.add(text.append("</html>").toString());
                text.delete(0, text.length());
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 展示页内容
     * @author zxy
     * @date 2024/10/21
     */
    private void showPage() {
        if (currentPage >= 0 && currentPage < pages.size()) {
            label.setText(pages.get(currentPage));
        }
    }

    /**
     * 展示设置弹出框
     * @author zxy
     * @date 2024/10/21
     */
    public void showSetting() {
        JButton colorButton = new JButton("选择颜色");

        // 添加按钮的点击事件监听器
        colorButton.addActionListener(e -> {
            // 显示颜色选择器，并获取选择的颜色
            selectedColor = JColorChooser.showDialog(frame, "选择颜色", label.getForeground());
        });

        final JTextField textWidth = new JTextField(10);
        ((AbstractDocument)textWidth.getDocument()).setDocumentFilter(new NumericDocumentFilter());
        textWidth.setText(String.valueOf(frame.getSize().width));
        final JTextField textHeight = new JTextField(10);
        ((AbstractDocument)textHeight.getDocument()).setDocumentFilter(new NumericDocumentFilter());
        textHeight.setText(String.valueOf(frame.getSize().height));

        final JTextField textFontSize = new JTextField(10);
        ((AbstractDocument)textFontSize.getDocument()).setDocumentFilter(new NumericDocumentFilter());
        textFontSize.setText(String.valueOf(label.getFont().getSize()));

        final JTextField jumpPage = new JTextField(10);
        ((AbstractDocument)jumpPage.getDocument()).setDocumentFilter(new NumericDocumentFilter());
        jumpPage.setText(String.valueOf(currentPage));

        JComboBox<Pair<String, Integer>> fontStyleComboBox = new JComboBox<>();
        fontStyleComboBox.addItem(new Pair<>("常规", Font.PLAIN));
        fontStyleComboBox.addItem(new Pair<>("加粗", Font.BOLD));
        fontStyleComboBox.addItem(new Pair<>("斜体", Font.ITALIC));
        fontStyleComboBox.setSelectedIndex(label.getFont().getStyle());
        // 添加下拉框的动作监听器，以便在用户选择项时获取值
        fontStyleComboBox.addActionListener(e -> {
            fontStyle = ((Pair<String, Integer>) fontStyleComboBox.getSelectedItem()).getValue();
        });

        JButton okButton = new JButton("确认");
        JButton cancelButton = new JButton("取消");

        // 创建面板来放置组件
        JPanel panel = new JPanel(new GridLayout(7, 2, 5, 5)); // 使用网格布局
        panel.add(new JLabel(" 字体颜色:"));
        panel.add(colorButton);
        panel.add(new JLabel(" 字体大小:"));
        panel.add(textFontSize);
        panel.add(new JLabel(" 字体样式:"));
        panel.add(fontStyleComboBox);
        panel.add(new JLabel(" 窗口宽度:"));
        panel.add(textWidth);
        panel.add(new JLabel(" 窗口高度:"));
        panel.add(textHeight);
        panel.add(new JLabel(" 跳页(0/" + (pages.size() - 1) + "):"));
        panel.add(jumpPage);

        panel.add(okButton);
        panel.add(cancelButton);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 创建对话框
        JDialog dialog = new JDialog(frame, "设置", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setContentPane(panel);
        dialog.pack(); // 自动调整对话框大小以适应其内容
        dialog.setLocationRelativeTo(frame); // 将对话框放置在父窗口中央

        // 为确认按钮添加监听器
        okButton.addActionListener(e -> {
            if (selectedColor == null) {
                selectedColor = label.getForeground();
            }
            if (StrUtil.isBlank(textFontSize.getText())) {
                // 输入框为空，给出提示信息
                JOptionPane.showMessageDialog(frame, "请输入字体大小", "提示", JOptionPane.INFORMATION_MESSAGE);
                textFontSize.requestFocusInWindow(); // 请求输入框获取焦点
            } else if (StrUtil.isBlank(textWidth.getText())) {
                // 输入框为空，给出提示信息
                JOptionPane.showMessageDialog(frame, "请输入窗口宽度", "提示", JOptionPane.INFORMATION_MESSAGE);
                textWidth.requestFocusInWindow(); // 请求输入框获取焦点
            } else if (StrUtil.isBlank(textHeight.getText())) {
                // 输入框为空，给出提示信息
                JOptionPane.showMessageDialog(frame, "请输入窗口高度", "提示", JOptionPane.INFORMATION_MESSAGE);
                textHeight.requestFocusInWindow(); // 请求输入框获取焦点
            } else if (Integer.valueOf(textWidth.getText()) < 100 || Integer.valueOf(textWidth.getText()) < 100) {
                JOptionPane.showMessageDialog(frame, "窗口的宽高不能小于100", "提示", JOptionPane.INFORMATION_MESSAGE);
            } else if (Integer.valueOf(jumpPage.getText()) < 0 || Integer.valueOf(jumpPage.getText()) > pages.size() - 1) {
                JOptionPane.showMessageDialog(frame, "页码不能小于0或大于" + (pages.size() - 1), "提示", JOptionPane.INFORMATION_MESSAGE);
            } else {
                fontSize = Integer.valueOf(textFontSize.getText());
                fontStyle =  fontStyleComboBox.getSelectedIndex();
                width = Integer.valueOf(textWidth.getText());
                height = Integer.valueOf(textHeight.getText());
                currentPage = Integer.valueOf(jumpPage.getText());
                dialog.dispose(); // 关闭对话框
            }
        });

        // 为取消按钮添加监听器
        cancelButton.addActionListener(e -> {
            dialog.dispose(); // 关闭对话框
        });
        // 显示对话框
        dialog.setVisible(true);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        //鼠标点击的时候，把当前屏幕上x，y的值给全局变量x，y
        x = e.getX();
        y = e.getY();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        //设置jframe位置为当前鼠标按住拖动的位置减去最开始鼠标在jframe按下的位置
        frame.setLocation(e.getXOnScreen() - x, e.getYOnScreen() - y);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if(Arrays.asList(frame.getContentPane().getComponents()).contains(label)) {
            if (e.getButton() == MouseEvent.BUTTON3) {
                ReadingRecord.saveRecord(frame, label, filePath, currentPage);
                frame.remove(label);
                novelRecordMap = ReadingRecord.loadRecord(frame);
                openHome();
                frame.revalidate(); // 更新界面
                frame.repaint();

            } else if (e.getButton() == MouseEvent.BUTTON1) {
                showSetting();
                if (width != null && height != null) {
                    frame.setSize(width, height);
                    label.setBounds(0, 0, frame.getSize().width, frame.getSize().height);
                }
                if (fontSize != null && fontStyle != null) {
                    label.setFont(new Font("Serif", fontStyle, fontSize));
                }
                if (selectedColor != null) {
                    label.setForeground(new Color(selectedColor.getRGB()));
                }
                calculationPages();
                showPage();
            }
        }
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

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        // 获取鼠标滚轮的旋转方向
        int rotation = e.getWheelRotation();
        if (rotation < 0) {
            // 鼠标滚轮向前（远离用户），向前翻页
            --currentPage;
        } else if (rotation > 0) {
            // 鼠标滚轮向后（朝向用户），向后翻页
            ++currentPage;
        }
        if (currentPage < 0) {
            currentPage = 0;
        } else if (currentPage >= pages.size()) {
            currentPage = pages.size() - 1;
        }
        showPage();
    }

    public static void main(String[] args) {
        new NovelReader();
    }
}