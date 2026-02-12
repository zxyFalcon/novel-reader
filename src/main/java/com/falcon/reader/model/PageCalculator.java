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

        // 1. 字体度量与可用空间
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

        // 2. 逐页生成
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath),
                        EncodingDetect.getJavaEncode(filePath)))) {

            StringBuilder pageContent = new StringBuilder();
            int currentLines = 0;          // 当前页已用行数
            String line;

            pageContent.append("<html>");  // 每页以<html>开头

            while ((line = reader.readLine()) != null) {
                // 处理空行：原算法会显示一个空白行，此处保持完全一致
                if (line.isEmpty()) {
                    // 当前页是否还有空间？
                    if (currentLines >= maxLines) {
                        // 页满，封页并开启新页
                        pageContent.append("</html>");
                        pages.add(pageContent.toString());
                        pageContent.setLength(0);
                        pageContent.append("<html>");
                        currentLines = 0;
                    }
                    // 添加空行
                    pageContent.append("<br/>");
                    currentLines++;
                    continue;
                }

                int index = 0;
                int len = line.length();

                // 将当前段落拆分为多行，每行宽度 ≤ availableWidth
                while (index < len) {
                    // 检查是否已到达本页底部
                    if (currentLines >= maxLines) {
                        // 当前页已满 → 封页，重置，继续处理当前行的剩余部分
                        pageContent.append("</html>");
                        pages.add(pageContent.toString());
                        pageContent.setLength(0);
                        pageContent.append("<html>");
                        currentLines = 0;
                    }

                    // 构建一行（贪婪累积字符直至超宽）
                    int start = index;
                    int end = index;
                    int lineWidth = 0;

                    while (end < len) {
                        char c = line.charAt(end);
                        int charWidth = fm.charWidth(c);
                        if (lineWidth + charWidth <= availableWidth) {
                            lineWidth += charWidth;
                            end++;
                        } else {
                            break;  // 当前行已满
                        }
                    }

                    // 极端情况：单个字符宽度已超过可用宽度 → 强制显示该字符（宁可溢出，不可丢失）
                    if (start == end) {
                        end = start + 1;
                    }

                    // 将这一行子串添加到当前页，并附加<br/>
                    String subLine = line.substring(start, end);
                    pageContent.append(subLine).append("<br/>");
                    currentLines++;

                    // 移动指针，处理剩余部分
                    index = end;
                }
            }

            // 3. 收尾：保存最后一页
            if (pageContent.length() > "<html>".length()) {
                pageContent.append("</html>");
                pages.add(pageContent.toString());
            }

        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();  // 异常时返回空列表
        }

        return pages;
    }
}
