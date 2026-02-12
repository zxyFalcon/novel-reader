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
     * 实现原理：
     * 1. 使用 FontMetrics 测量每个字符的精确像素宽度
     * 2. 按行读取原始文本，将每行按可用宽度切割成多个显示行
     * 3. 根据标签高度和行高计算每页最多容纳的行数
     * 4. 将切割后的显示行组装成 HTML 格式的页面
     *
     * @param filePath 小说文件路径
     * @param label    显示内容的标签，用于获取字体信息和显示区域尺寸
     * @return 分页内容列表，每个元素是一页完整的 HTML 字符串；如果无法分页或发生异常则返回空列表
     */
    public static List<String> calculatePages(String filePath, JLabel label) {
        List<String> pages = new ArrayList<>();

        // 1. 获取字体度量对象，用于精确测量字符宽度
        FontMetrics fm = label.getFontMetrics(label.getFont());

        // 精确行高 = 上升 + 下降 + 行间距 + 2像素补偿（防止文字紧贴边缘）
        int lineHeight = fm.getAscent() + fm.getDescent() + fm.getLeading() + 2;

        // 标签的有效显示宽度（像素）
        int availableWidth = label.getWidth();

        // 每页最多容纳的行数 = 标签高度 / 行高（向下取整）
        int maxLines = label.getHeight() / lineHeight;

        // 边界保护：如果显示区域宽度或高度不足，直接返回空列表，避免后续除零或无限循环
        if (availableWidth <= 0 || maxLines <= 0) {
            return pages;
        }

        // 2. 逐页生成：使用 BufferedReader 按行读取原始文件，并动态切分为显示行
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath),
                        EncodingDetect.getJavaEncode(filePath)))) {

            List<String> currentPageLines = new ArrayList<>(maxLines + 2); // 当前页已积累的显示行
            int currentLines = 0;                                          // 当前页已占用的行数

            String line;
            while ((line = reader.readLine()) != null) {
                // 空行处理
                if (line.isEmpty()) {
                    // 如果当前页已满，先保存当前页，再清空缓存
                    if (currentLines >= maxLines) {
                        pages.add(buildPage(currentPageLines));
                        currentPageLines.clear();
                        currentLines = 0;
                    }
                    // 空行用一个空字符串占位，后续 buildPage 时会转为 <br/>
                    currentPageLines.add("");
                    currentLines++;
                    continue;
                }

                // 非空行处理：按像素宽度切分
                int index = 0; // 当前处理到的字符位置
                while (index < line.length()) {
                    // 页满则保存当前页，重置缓存
                    if (currentLines >= maxLines) {
                        pages.add(buildPage(currentPageLines));
                        currentPageLines.clear();
                        currentLines = 0;
                    }

                    // 贪心算法：从 index 开始，累加字符宽度直到达到可用宽度
                    int start = index;
                    int end = index;
                    int lineWidth = 0; // 当前已累加的像素宽度

                    while (end < line.length()) {
                        char c = line.charAt(end);
                        int charWidth = fm.charWidth(c);
                        // 如果加上下一个字符不超过可用宽度，则加入本行
                        if (lineWidth + charWidth <= availableWidth) {
                            lineWidth += charWidth;
                            end++;
                        } else {
                            break; // 已超宽，结束本行
                        }
                    }

                    // 极端情况：单个字符宽度已超过可用宽度，强制放入（避免死循环）
                    if (start == end) {
                        end = start + 1;
                    }

                    // 截取本显示行的文本
                    String subLine = line.substring(start, end);
                    currentPageLines.add(subLine);
                    currentLines++;

                    // 继续处理该行剩余的字符
                    index = end;
                }
            }

            // 3. 处理最后剩余的内容（如果当前页还有未保存的行）
            if (!currentPageLines.isEmpty()) {
                pages.add(buildPage(currentPageLines));
            }

        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>(); // 异常时返回空列表，调用方自行处理
        }

        return pages;
    }

    /**
     * 将一页的显示行列表构建为完整的 HTML 字符串
     * 每行内容后添加 <br/> 换行，空字符串转为纯 <br/> 标签
     *
     * @param lines 当前页的所有显示行（字符串列表）
     * @return HTML 格式的页面内容
     */
    private static String buildPage(List<String> lines) {
        if (lines.isEmpty()) {
            return "<html></html>";
        }

        // 预估 StringBuilder 容量：基础长度 + 每行平均长度 + 换行标签长度
        StringBuilder sb = new StringBuilder(1024 + lines.size() * 40);
        sb.append("<html>");

        for (String content : lines) {
            if (content.isEmpty()) {
                sb.append("<br/>"); // 空行仅输出换行
            } else {
                sb.append(content).append("<br/>"); // 非空行：文本 + 换行
            }
        }

        sb.append("</html>");
        return sb.toString();
    }
}