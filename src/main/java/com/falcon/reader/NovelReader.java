package com.falcon.reader;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import javafx.util.Pair;
import org.springframework.util.StringUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class NovelReader implements MouseListener, MouseMotionListener, MouseWheelListener {
    int x, y;
    Integer fontSize, fontStyle, width, height;
    private String fileName = null;
    private Color selectedColor = null;
    public JFrame jFrame;
    private JLabel label;
    private int currentPage = 0;
    private List<String> pages = new ArrayList<>();
    private static final String BOOKMARK_FILE = "bookmark.json";

    NovelReader() {
        try {
            // 设置UI样式为系统默认样式
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }
        jFrame = new JFrame();
        jFrame.setSize(900, 600);
        jFrame.setUndecorated(true);//设置jframe取消顶部标题栏
        jFrame.addMouseListener(this);//窗口添加鼠标监听器
        jFrame.addMouseMotionListener(this);//窗口添加鼠标姿势动作监听器
        jFrame.addMouseWheelListener(this);
        jFrame.setBackground(new Color(0, 0, 0, 1));

        jFrame.setLocation(800, 500);//设置窗口的显示位置
        jFrame.setLayout(null);
        jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);//创建并关闭窗口时的默认操作
        //        jFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // 不设置默认关闭操作，以便我们可以自定义
        jFrame.setVisible(true);

        // 创建一个文件选择器
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File(".")); // 设置当前目录为当前目录
        // 设置文件过滤器，只允许选择txt文件
        FileNameExtensionFilter txtFilter = new FileNameExtensionFilter(
                "文本文件 (*.txt)", "txt");
        fileChooser.setFileFilter(txtFilter);

        // 显示文件选择器
        int result = fileChooser.showOpenDialog(jFrame);

        // 检查用户是否点击了打开按钮
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            fileName = selectedFile.getAbsolutePath();
        } else{
            System.exit(1);
        }

        label = new JLabel();
        label.setLayout(new FlowLayout());
        label.setBounds(10, 5, jFrame.getSize().width - 100, jFrame.getSize().height - 100);
        label.setFont(new Font("Serif", Font.PLAIN, 15));
        label.setForeground(Color.WHITE);
        label.setVerticalAlignment(JLabel.TOP); // 或者 JLabel.CENTER, JLabel.BOTTOM
        label.setVerticalTextPosition(JLabel.TOP); // 或者 JLabel.CENTER, JLabel.BOTTOM
        jFrame.add(label);

        loadContent();
        calculationPages();
        showPage(currentPage);
    }

    public static void main(String[] args) {
        new NovelReader();
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
        jFrame.setLocation(e.getXOnScreen() - x, e.getYOnScreen() - y);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if(e.getButton() == MouseEvent.BUTTON3){
            saveContent();
            System.exit(1);
        } else if(e.getButton() == MouseEvent.BUTTON1){
            showDialog();
            if (width != null && height != null) {
                jFrame.setSize(width, height);
                label.setBounds(10, 5, jFrame.getSize().width - 100, jFrame.getSize().height - 100);
            }
            if(fontSize != null && fontStyle != null){
                label.setFont(new Font("Serif", fontStyle, fontSize));
            }
            if(selectedColor != null){
                label.setForeground(new Color(selectedColor.getRGB()));
            }
            calculationPages();
            showPage(currentPage);
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
        showPage(currentPage);
    }


    private void calculationPages(){
        pages.clear();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileName),
                    com.sjfl.main.EncodingDetect.getJavaEncode(fileName)));
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

    private void showPage(int page) {
        if (page >= 0 && page < pages.size()) {
            label.setText(pages.get(page));
        }
    }

    public void showDialog() {
        JButton colorButton = new JButton("选择颜色");

        // 添加按钮的点击事件监听器
        colorButton.addActionListener(e -> {
            // 显示颜色选择器，并获取选择的颜色
            selectedColor = JColorChooser.showDialog(jFrame, "选择颜色", label.getForeground());
        });

        final JTextField textWidth = new JTextField(10);
        ((AbstractDocument)textWidth.getDocument()).setDocumentFilter(new NumericDocumentFilter());
        textWidth.setText(String.valueOf(jFrame.getSize().width));
        final JTextField textHeight = new JTextField(10);
        ((AbstractDocument)textHeight.getDocument()).setDocumentFilter(new NumericDocumentFilter());
        textHeight.setText(String.valueOf(jFrame.getSize().height));

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
        JDialog dialog = new JDialog(jFrame, "设置", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setContentPane(panel);
        dialog.pack(); // 自动调整对话框大小以适应其内容
        dialog.setLocationRelativeTo(jFrame); // 将对话框放置在父窗口中央

        // 为确认按钮添加监听器
        okButton.addActionListener(e -> {
            if (selectedColor == null) {
                selectedColor = label.getForeground();
            }
            if (StrUtil.isBlank(textFontSize.getText())) {
                // 输入框为空，给出提示信息
                JOptionPane.showMessageDialog(jFrame, "请输入字体大小", "提示", JOptionPane.INFORMATION_MESSAGE);
                textFontSize.requestFocusInWindow(); // 请求输入框获取焦点
            } else if (StrUtil.isBlank(textWidth.getText())) {
                // 输入框为空，给出提示信息
                JOptionPane.showMessageDialog(jFrame, "请输入窗口宽度", "提示", JOptionPane.INFORMATION_MESSAGE);
                textWidth.requestFocusInWindow(); // 请求输入框获取焦点
            } else if (StrUtil.isBlank(textHeight.getText())) {
                // 输入框为空，给出提示信息
                JOptionPane.showMessageDialog(jFrame, "请输入窗口高度", "提示", JOptionPane.INFORMATION_MESSAGE);
                textHeight.requestFocusInWindow(); // 请求输入框获取焦点
            } else if (Integer.valueOf(textWidth.getText()) < 100 || Integer.valueOf(textWidth.getText()) < 100) {
                JOptionPane.showMessageDialog(jFrame, "窗口的宽高不能小于100", "提示", JOptionPane.INFORMATION_MESSAGE);
            } else if (Integer.valueOf(jumpPage.getText()) < 0 || Integer.valueOf(jumpPage.getText()) > pages.size() - 1) {
                JOptionPane.showMessageDialog(jFrame, "页码不能小于0或大于" + (pages.size() - 1), "提示", JOptionPane.INFORMATION_MESSAGE);
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

    class NumericDocumentFilter extends DocumentFilter {
        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws
                BadLocationException {
            if (text == null) {
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (char c : text.toCharArray()) {
                if (Character.isDigit(c)) {
                    sb.append(c);
                }
            }

            super.replace(fb, offset, length, sb.toString(), attrs);
        }
    }

    private void saveContent() {
        Path path = Paths.get(BOOKMARK_FILE);

        if(StrUtil.isNotBlank(fileName)) {
            JSONObject jsonObject = null;
            if (!Files.exists(path)) {
                try {
                    Files.createFile(path);
                    jsonObject = new JSONObject();
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            } else {
                try {
                    jsonObject = new JSONObject(new FileReader(BOOKMARK_FILE));
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }

            JSONObject currentNovel = new JSONObject();
            currentNovel.set("fileName", fileName);
            currentNovel.set("currentPage", currentPage);
            JSONArray novelArray = new JSONArray();
            boolean flag = true;
            if(jsonObject.containsKey("novels")){
                novelArray = jsonObject.getJSONArray("novels");
                if(!novelArray.isEmpty()){
                    for (Object object : novelArray) {
                        JSONObject novel = (JSONObject)object;
                        if(novel.containsKey("fileName") && fileName.equals(novel.getStr("fileName"))){
                            novel.set("currentPage", currentPage);
                            flag = false;
                            break;
                        }
                    }
                }
            }
            if(flag) {
                novelArray.add(currentNovel);
            }

            jsonObject.set("width", jFrame.getSize().width);
            jsonObject.set("height", jFrame.getSize().height);
            jsonObject.set("fontSize", label.getFont().getSize());
            jsonObject.set("fontStyle", label.getFont().getStyle());
            jsonObject.set("labelForeground", label.getForeground().getRGB());
            jsonObject.set("locationX", jFrame.getLocation().x);
            jsonObject.set("locationY", jFrame.getLocation().y);
            jsonObject.set("novels", novelArray);
            try (FileWriter file = new FileWriter(BOOKMARK_FILE)) {
                file.write(jsonObject.toString());
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(jFrame, "保存内容失败: " + ex.getMessage());
            }
        }
    }

    private void loadContent() {
        if(StrUtil.isNotBlank(fileName) && Files.exists(Paths.get(BOOKMARK_FILE))) {
            try {
                JSONObject jsonObject = new JSONObject(new FileReader(BOOKMARK_FILE));
                if (jsonObject.containsKey("width") && jsonObject.containsKey("height")) {
                    jFrame.setSize(jsonObject.getInt("width"), jsonObject.getInt("height"));
                    label.setBounds(10, 5, jFrame.getSize().width - 100, jFrame.getSize().height - 100);
                }
                if(jsonObject.containsKey("fontSize") && jsonObject.containsKey("fontStyle")){
                    label.setFont(new Font("Serif", jsonObject.getInt("fontStyle"), jsonObject.getInt("fontSize")));
                }
                if(jsonObject.containsKey("labelForeground")){
                    label.setForeground(new Color(jsonObject.getInt("labelForeground")));
                }
                if(jsonObject.containsKey("locationX") && jsonObject.containsKey("locationY")){
                    jFrame.setLocation(jsonObject.getInt("locationX"), jsonObject.getInt("locationY"));
                }

                if(jsonObject.containsKey("novels")){
                    JSONArray novelArray = jsonObject.getJSONArray("novels");
                    if(!novelArray.isEmpty()){
                        for (Object object : novelArray) {
                            JSONObject novel = (JSONObject)object;
                            if(novel.containsKey("fileName") && fileName.equals(novel.getStr("fileName"))){
                                if(novel.containsKey("currentPage")){
                                    currentPage = novel.getInt("currentPage");
                                    break;
                                }
                            }
                        }
                    }
                }

            } catch (IOException | NullPointerException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(jFrame, "加载内容失败: " + ex.getMessage());
            }
        }
    }
}