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
 * 采用像素宽度精确换行，最大化利用显示区域且绝不溢出
 *
 * @author zxy
 * @date 2026/2/12 15:24
 **/
public class PageCalculator {

    /**
     * 计算小说文件的页内容，根据标签尺寸和字体进行像素级精确分页
     *
     * @param filePath 文件路径
     * @param label    显示标签
     * @return 分页内容列表（每页为完整HTML片段）
     */
    public static List<String> calculatePages(String filePath, JLabel label) {
        List<String> pages = new ArrayList<>();

        // 字体度量与可用空间
        FontMetrics fm = label.getFontMetrics(label.getFont());
        // 精确行高（ascent+descent+leading+2 像素补偿，与原始算法一致）
        int lineHeight = fm.getAscent() + fm.getDescent() + fm.getLeading() + 2;
        // 有效显示宽度
        int availableWidth = label.getWidth();
        // 每页最大行数
        int maxLines = label.getHeight() / lineHeight;

        // 边界保护：空间不足时直接返回空列表
        if (availableWidth <= 0 || maxLines <= 0) {
            return pages;
        }

        // 逐页生成
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath),
                        EncodingDetect.getJavaEncode(filePath)))) {

            List<String> currentPageLines = new ArrayList<>(maxLines + 2); // 预估容量
            int currentLines = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (currentLines >= maxLines) {
                        pages.add(buildPage(currentPageLines));
                        currentPageLines.clear();
                        currentLines = 0;
                    }
                    currentPageLines.add(""); // 空行用空字符串表示
                    currentLines++;
                    continue;
                }

                int index = 0;
                while (index < line.length()) {
                    if (currentLines >= maxLines) {
                        pages.add(buildPage(currentPageLines));
                        currentPageLines.clear();
                        currentLines = 0;
                    }

                    // 找本行能放多少字符（逻辑不变）
                    int start = index;
                    int end = index;
                    int lineWidth = 0;
                    while (end < line.length()) {
                        char c = line.charAt(end);
                        int charWidth = fm.charWidth(c);
                        if (lineWidth + charWidth <= availableWidth) {
                            lineWidth += charWidth;
                            end++;
                        } else {
                            break;
                        }
                    }
                    if (start == end) end = start + 1;

                    String subLine = line.substring(start, end);
                    currentPageLines.add(subLine);
                    currentLines++;

                    index = end;
                }
            }

            // 最后一页
            if (!currentPageLines.isEmpty()) {
                pages.add(buildPage(currentPageLines));
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();  // 异常时返回空列表
        }

        return pages;
    }

    // 格式化一页
    private static String buildPage(List<String> lines) {
        if (lines.isEmpty()) return "<html></html>";

        StringBuilder sb = new StringBuilder(1024 + lines.size() * 40); // 粗略预估
        sb.append("<html>");

        for (int i = 0; i < lines.size(); i++) {
            String content = lines.get(i);
            if (content.isEmpty()) {
                sb.append("<br/>");
            } else {
                sb.append(content).append("<br/>");
            }
        }

        sb.append("</html>");
        return sb.toString();
    }
}
