package com.falcon.reader.persistence;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.falcon.reader.domain.NovelConfig;
import com.falcon.reader.domain.NovelRecord;
import com.falcon.reader.domain.ReadingData;
import com.falcon.reader.domain.WindowState;

import java.awt.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/**
 * Persists reader settings, window bounds and per-book reading progress in one JSON file.
 * This class is UI-independent; failures are reported through the injected error handler.
 *
 * @author zxy
 * @date 2024/10/21 14:13
 **/
public class ReadingRecordRepository {

    /**
     * JSON file containing reader settings and progress records.
     */
    private final Path bookmarkFile;
    private final Consumer<String> errorHandler;

    public ReadingRecordRepository(Path bookmarkFile) {
        this(bookmarkFile, message -> {
        });
    }

    public ReadingRecordRepository(Consumer<String> errorHandler) {
        this(Paths.get("bookmark.json"), errorHandler);
    }

    public ReadingRecordRepository(Path bookmarkFile, Consumer<String> errorHandler) {
        this.bookmarkFile = bookmarkFile;
        this.errorHandler = errorHandler == null ? message -> {
        } : errorHandler;
    }

    private static void writeWindowState(JSONObject jsonObject, WindowState windowState) {
        if (windowState == null) {
            return;
        }
        jsonObject.set("width", windowState.getWidth());
        jsonObject.set("height", windowState.getHeight());
        jsonObject.set("locationX", windowState.getLocationX());
        jsonObject.set("locationY", windowState.getLocationY());
    }

    private static void writeConfig(JSONObject jsonObject, NovelConfig config) {
        if (config == null) {
            return;
        }
        jsonObject.set("font", config.getFont().getName());
        jsonObject.set("fontSize", config.getFont().getSize());
        jsonObject.set("fontStyle", config.getFont().getStyle());
        jsonObject.set("labelForeground", config.getForeground().getRGB());
    }

    /**
     * Saves the current application settings and one book's reading position.
     */
    public void saveRecord(WindowState windowState, NovelConfig config, String filePath, int currentPage, Integer totalPages,
                           Integer currentOffset, Integer totalLength) {
        Path path = bookmarkFile;

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
                    jsonObject = readJson(path);
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
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
                            if (totalPages != null && totalPages > 0) {
                                novel.set("totalPages", totalPages);
                            }
                            if (currentOffset != null && currentOffset >= 0) {
                                novel.set("currentOffset", currentOffset);
                            }
                            if (totalLength != null && totalLength > 0) {
                                novel.set("totalLength", totalLength);
                            }
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
                if (totalPages != null && totalPages > 0) {
                    currentNovel.set("totalPages", totalPages);
                }
                if (currentOffset != null && currentOffset >= 0) {
                    currentNovel.set("currentOffset", currentOffset);
                }
                if (totalLength != null && totalLength > 0) {
                    currentNovel.set("totalLength", totalLength);
                }
                currentNovel.set("lastReadingTime", LocalDateTime.now());
                novelArray.add(currentNovel);
            }

            // 保存窗口配置：大小、位置
            writeWindowState(jsonObject, windowState);

            // 保存文本显示配置：字体名称、大小、样式、前景色
            writeConfig(jsonObject, config);

            // 更新小说记录数组
            jsonObject.set("novels", novelArray);

            // 将JSON对象写入文件
            try (Writer file = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                file.write(jsonObject.toString());
            } catch (IOException ex) {
                ex.printStackTrace();
                reportError("保存记录失败", ex);
            }
        }
    }

    public void saveConfig(WindowState windowState, NovelConfig config) {
        Path path = bookmarkFile;
        JSONObject jsonObject;

        if (!Files.exists(path)) {
            try {
                Files.createFile(path);
                jsonObject = new JSONObject();
            } catch (IOException e) {
                e.printStackTrace();
                reportError("保存设置失败", e);
                return;
            }
        } else {
            try {
                jsonObject = readJson(path);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        writeWindowState(jsonObject, windowState);
        writeConfig(jsonObject, config);

        try (Writer file = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            file.write(jsonObject.toString());
        } catch (IOException ex) {
            ex.printStackTrace();
            reportError("保存设置失败", ex);
        }
    }

    /**
     * 删除指定文件路径的小说阅读记录
     *
     * @param filePath 要删除记录的小说文件路径
     */
    public void deleteRecord(String filePath) {
        Path path = bookmarkFile;

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
                    jsonObject = readJson(path);
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }

            JSONArray novelArray = new JSONArray();
            // 从JSON中取出小说记录数组，并删除匹配的记录
            if (jsonObject.containsKey("novels")) {
                novelArray = jsonObject.getJSONArray("novels");
                if (!novelArray.isEmpty()) {
                    for (int i = 0; i < novelArray.size(); i++) {
                        JSONObject novel = novelArray.getJSONObject(i);
                        if (novel.containsKey("filePath") && filePath.equals(novel.getStr("filePath"))) {
                            novelArray.remove(i); // 删除匹配的记录
                            break;
                        }
                    }
                }
            }

            // 更新后的记录数组写回JSON对象
            jsonObject.set("novels", novelArray);

            // 将更新后的JSON写回文件
            try (Writer file = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                file.write(jsonObject.toString());
            } catch (IOException ex) {
                ex.printStackTrace();
                reportError("删除记录失败", ex);
            }
        }
    }

    /**
     * 重新定位指定小说记录的文件路径，保留页码、时间和分页信息等阅读进度。
     *
     * @param oldFilePath 原小说文件路径
     * @param newFilePath 新小说文件路径
     * @return 是否成功更新记录
     */
    public boolean relocateRecord(String oldFilePath, String newFilePath) {
        if (StrUtil.isBlank(oldFilePath) || StrUtil.isBlank(newFilePath)) {
            return false;
        }

        Path path = bookmarkFile;
        if (!Files.exists(path)) {
            return false;
        }

        try {
            JSONObject jsonObject = readJson(path);
            JSONArray novelArray = jsonObject.containsKey("novels") ? jsonObject.getJSONArray("novels") : new JSONArray();
            boolean updated = false;

            for (int i = 0; i < novelArray.size(); i++) {
                JSONObject novel = novelArray.getJSONObject(i);
                if (oldFilePath.equals(novel.getStr("filePath"))) {
                    novel.set("filePath", newFilePath);
                    updated = true;
                    break;
                }
            }

            if (!updated) {
                return false;
            }

            jsonObject.set("novels", novelArray);
            try (Writer file = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                file.write(jsonObject.toString());
            }
            return true;
        } catch (IOException ex) {
            ex.printStackTrace();
            reportError("重新定位文件失败", ex);
            return false;
        }
    }

    /**
     * Loads reader settings, window bounds and records ordered by last reading time.
     *
     * @return persisted reader data, or defaults when the file does not exist
     */
    public ReadingData loadRecord() {
        Map<String, NovelRecord> novelRecordMap = new LinkedHashMap<>();
        NovelConfig novelConfig = new NovelConfig();
        WindowState windowState = null;

        // 仅当记录文件存在时才执行加载
        if (Files.exists(bookmarkFile)) {
            try {
                JSONObject jsonObject = readJson(bookmarkFile);

                // 加载窗口大小
                if (jsonObject.containsKey("width") && jsonObject.containsKey("height")) {
                    int locationX = jsonObject.containsKey("locationX") ? jsonObject.getInt("locationX") : 800;
                    int locationY = jsonObject.containsKey("locationY") ? jsonObject.getInt("locationY") : 500;
                    windowState = new WindowState(jsonObject.getInt("width"), jsonObject.getInt("height"), locationX, locationY);
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
                reportError("加载记录失败", ex);
            }
        }
        return new ReadingData(novelConfig, novelRecordMap, windowState);
    }

    private void reportError(String action, Exception exception) {
        errorHandler.accept(action + ": " + exception.getMessage());
    }

    private JSONObject readJson(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length == 0) {
            return new JSONObject();
        }

        try {
            return parseJson(bytes, StandardCharsets.UTF_8, true);
        } catch (IOException | RuntimeException utf8Ex) {
            try {
                return parseJson(bytes, Charset.defaultCharset(), false);
            } catch (RuntimeException defaultCharsetEx) {
                IOException ex = new IOException("阅读记录文件格式错误，请检查或删除 " + bookmarkFile, defaultCharsetEx);
                ex.addSuppressed(utf8Ex);
                throw ex;
            }
        }
    }

    private static JSONObject parseJson(byte[] bytes, Charset charset, boolean reportMalformedInput) throws IOException {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes),
                reportMalformedInput ? charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT) : charset.newDecoder())) {
            return new JSONObject(reader);
        }
    }
}
