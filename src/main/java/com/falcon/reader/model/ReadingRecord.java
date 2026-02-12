package com.falcon.reader.model;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.falcon.reader.entity.NovelConfig;
import com.falcon.reader.entity.NovelRecord;
import javafx.util.Pair;

import javax.swing.*;
import java.awt.*;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;

/**
 * 阅读记录处理类
 * 负责保存、删除和加载阅读器的配置（窗口大小、位置、字体、颜色）以及每本小说的阅读进度
 *
 * @author zxy
 * @date 2024/10/21 14:13
 **/
public class ReadingRecord {

    /** 存储所有阅读记录及配置的JSON文件名 */
    private static final String BOOKMARK_FILE = "bookmark.json";

    /**
     * 保存当前阅读状态及小说进度
     *
     * @param frame       主窗口，用于获取窗口大小和位置
     * @param label       内容显示标签，用于获取字体和前景色
     * @param filePath    当前阅读的小说文件路径，作为记录的唯一标识
     * @param currentPage 当前阅读的页码（或滚动位置）
     */
    public static void saveRecord(JFrame frame, JLabel label, String filePath, int currentPage) {
        Path path = Paths.get(BOOKMARK_FILE);

        // 仅当文件路径不为空时才执行保存操作
        if (StrUtil.isNotBlank(filePath)) {
            JSONObject jsonObject;

            // 如果记录文件不存在，则创建空文件并初始化一个空的JSON对象
            if (!Files.exists(path)) {
                try {
                    Files.createFile(path);
                    jsonObject = new JSONObject();
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            } else {
                // 文件已存在，读取现有内容
                try {
                    jsonObject = new JSONObject(new FileReader(BOOKMARK_FILE));
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }

            JSONArray novelArray = new JSONArray();
            boolean flag = true; // 标记是否需要新增记录（true表示未找到相同文件路径的记录）

            // 如果JSON中存在"novels"数组，则遍历查找是否已有当前文件路径的记录
            if (jsonObject.containsKey("novels")) {
                novelArray = jsonObject.getJSONArray("novels");
                if (!novelArray.isEmpty()) {
                    for (Object object : novelArray) {
                        JSONObject novel = (JSONObject) object;
                        if (novel.containsKey("filePath") && filePath.equals(novel.getStr("filePath"))) {
                            // 找到已有记录，更新页码和最后阅读时间
                            novel.set("currentPage", currentPage);
                            novel.set("lastReadingTime", LocalDateTime.now());
                            flag = false; // 标记为已更新，无需新增
                            break;
                        }
                    }
                }
            }

            // 如果没有找到相同文件路径的记录，则新增一条记录
            if (flag) {
                JSONObject currentNovel = new JSONObject();
                currentNovel.set("filePath", filePath);
                currentNovel.set("currentPage", currentPage);
                currentNovel.set("lastReadingTime", LocalDateTime.now());
                novelArray.add(currentNovel);
            }

            // 保存窗口配置：大小、位置
            jsonObject.set("width", frame.getSize().width);
            jsonObject.set("height", frame.getSize().height);
            jsonObject.set("locationX", frame.getLocation().x);
            jsonObject.set("locationY", frame.getLocation().y);

            // 保存文本显示配置：字体名称、大小、样式、前景色
            jsonObject.set("font", label.getFont().getName());
            jsonObject.set("fontSize", label.getFont().getSize());
            jsonObject.set("fontStyle", label.getFont().getStyle());
            jsonObject.set("labelForeground", label.getForeground().getRGB());

            // 更新小说记录数组
            jsonObject.set("novels", novelArray);

            // 将JSON对象写入文件
            try (FileWriter file = new FileWriter(BOOKMARK_FILE)) {
                file.write(jsonObject.toString());
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "保存记录失败: " + ex.getMessage());
            }
        }
    }

    /**
     * 删除指定文件路径的小说阅读记录
     *
     * @param frame    主窗口，用于显示错误提示
     * @param filePath 要删除记录的小说文件路径
     */
    public static void deleteRecord(JFrame frame, String filePath) {
        Path path = Paths.get(BOOKMARK_FILE);

        // 仅当文件路径不为空时才执行删除操作
        if (StrUtil.isNotBlank(filePath)) {
            JSONObject jsonObject;

            // 如果记录文件不存在，则创建空文件并初始化JSON对象（实际上不会包含任何记录）
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

            JSONArray novelArray = new JSONArray();
            // 从JSON中取出小说记录数组，并删除匹配的记录
            if (jsonObject.containsKey("novels")) {
                novelArray = jsonObject.getJSONArray("novels");
                if (!novelArray.isEmpty()) {
                    for (Object object : novelArray) {
                        JSONObject novel = (JSONObject) object;
                        if (novel.containsKey("filePath") && filePath.equals(novel.getStr("filePath"))) {
                            novelArray.remove(object); // 删除当前遍历到的元素
                            break;
                        }
                    }
                }
            }

            // 更新后的记录数组写回JSON对象
            jsonObject.set("novels", novelArray);

            // 将更新后的JSON写回文件
            try (FileWriter file = new FileWriter(BOOKMARK_FILE)) {
                file.write(jsonObject.toString());
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "删除记录失败: " + ex.getMessage());
            }
        }
    }

    /**
     * 加载所有保存的阅读记录和窗口配置
     *
     * @param frame 主窗口，加载后将应用保存的窗口大小和位置
     * @return Pair对象，左值为NovelConfig（包含字体、颜色等配置），右值为Map<String, NovelRecord>（文件路径到小说记录的映射，按最后阅读时间降序排列）
     */
    public static Pair<NovelConfig, Map<String, NovelRecord>> loadRecord(JFrame frame) {
        Map<String, NovelRecord> novelRecordMap = new LinkedHashMap<>();
        NovelConfig novelConfig = new NovelConfig();

        // 仅当记录文件存在时才执行加载
        if (Files.exists(Paths.get(BOOKMARK_FILE))) {
            try {
                JSONObject jsonObject = new JSONObject(new FileReader(BOOKMARK_FILE));

                // 加载窗口大小
                if (jsonObject.containsKey("width") && jsonObject.containsKey("height")) {
                    frame.setSize(jsonObject.getInt("width"), jsonObject.getInt("height"));
                }
                // 加载窗口位置
                if (jsonObject.containsKey("locationX") && jsonObject.containsKey("locationY")) {
                    frame.setLocation(jsonObject.getInt("locationX"), jsonObject.getInt("locationY"));
                }

                // 加载字体配置（名称、样式、大小）
                if (jsonObject.containsKey("font") && jsonObject.containsKey("fontSize") && jsonObject.containsKey("fontStyle")) {
                    novelConfig.setFont(
                            new Font(jsonObject.getStr("font"), jsonObject.getInt("fontStyle"), jsonObject.getInt("fontSize")));
                }
                // 加载标签前景色
                if (jsonObject.containsKey("labelForeground")) {
                    novelConfig.setForeground(new Color(jsonObject.getInt("labelForeground")));
                }

                // 加载所有小说阅读记录
                if (jsonObject.containsKey("novels")) {
                    JSONArray novelArray = jsonObject.getJSONArray("novels");
                    if (!novelArray.isEmpty()) {
                        List<NovelRecord> novelRecords = new ArrayList<>();
                        // 将JSON数组转换为NovelRecord对象列表
                        for (Object object : novelArray) {
                            JSONObject novel = (JSONObject) object;
                            novelRecords.add(BeanUtil.toBean(novel, NovelRecord.class));
                        }
                        // 按最后阅读时间降序排序（最近阅读的排在最前）
                        novelRecords.sort(Comparator.comparing(NovelRecord::getLastReadingTime).reversed());
                        // 存入有序Map，保持排序后的顺序
                        for (NovelRecord novelRecord : novelRecords) {
                            novelRecordMap.put(novelRecord.getFilePath(), novelRecord);
                        }
                    }
                }
            } catch (IOException | NullPointerException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "加载记录失败: " + ex.getMessage());
            }
        }
        return new Pair<>(novelConfig, novelRecordMap);
    }
}