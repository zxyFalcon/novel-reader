package com.falcon.reader.model;

import com.falcon.reader.util.EncodingDetect;
import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页计算器，负责计算小说文本的分页内容
 *
 * @author zxy
 * @date 2026/2/11 16:20
 **/
public class PageCalculator {
    /**
     * 计算小说文件的页内容，根据标签尺寸和字体计算分页
     * @param filePath 文件路径
     * @param label 显示标签
     * @return 分页内容列表
     * @author zxy
     * @date 2024/10/21
     */
    public static List<String> calculatePages(String filePath, JLabel label) {
        List<String> pages = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), EncodingDetect.getJavaEncode(filePath)))) {
            String line;
            StringBuilder text = new StringBuilder();

            // 获取字体度量
            FontMetrics fontMetrics = label.getFontMetrics(label.getFont());
            int charWidth = fontMetrics.charWidth('测');
            int charHeight = fontMetrics.getHeight();
            int charsPerLine = label.getWidth() / charWidth;
            int realLineHeight = (int) (charHeight * 1.2);
            int maxLines = label.getHeight() / realLineHeight;

            // 逐行读取并分页
            while ((line = reader.readLine()) != null) {
                text.append("<html>");
                int i = 0;
                do {
                    int lineCount = (line.length() + charsPerLine - 1) / charsPerLine;  // 简化计算
                    i += lineCount;
                    if (i > maxLines) {
                        int endIndex = charsPerLine * (lineCount - (i - maxLines));
                        text.append(line.substring(0, endIndex)).append("<br/>");
                        line = line.substring(endIndex);
                        break;
                    }
                    text.append(line).append("<br/>");
                    line = reader.readLine();  // 读取下一行
                } while (line != null);
                pages.add(text.append("</html>").toString());
                text.setLength(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return pages;
    }
}