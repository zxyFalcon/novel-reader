package com.falcon.reader.model;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.falcon.reader.entity.NovelRecord;

import javax.swing.*;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 阅读记录处理
 *
 * @author zxy
 * @date 2024/10/21 14:13
 **/
public class ReadingRecord {

    private static final String BOOKMARK_FILE = "bookmark.json";

    public static void saveRecord(JFrame frame, JLabel label, String filePath, int currentPage) {
        Path path = Paths.get(BOOKMARK_FILE);

        if(StrUtil.isNotBlank(filePath)) {
            JSONObject jsonObject;
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
            boolean flag = true;
            if(jsonObject.containsKey("novels")){
                novelArray = jsonObject.getJSONArray("novels");
                if(!novelArray.isEmpty()){
                    for (Object object : novelArray) {
                        JSONObject novel = (JSONObject)object;
                        if(novel.containsKey("filePath") && filePath.equals(novel.getStr("filePath"))){
                            novel.set("currentPage", currentPage);
                            novel.set("lastReadingTime", LocalDateTime.now());
                            flag = false;
                            break;
                        }
                    }
                }
            }
            if(flag) {
                JSONObject currentNovel = new JSONObject();
                currentNovel.set("filePath", filePath);
                currentNovel.set("currentPage", currentPage);
                currentNovel.set("lastReadingTime", LocalDateTime.now());
                novelArray.add(currentNovel);
            }

            jsonObject.set("width", frame.getSize().width);
            jsonObject.set("height", frame.getSize().height);
            jsonObject.set("fontSize", label.getFont().getSize());
            jsonObject.set("fontStyle", label.getFont().getStyle());
            jsonObject.set("labelForeground", label.getForeground().getRGB());
            jsonObject.set("locationX", frame.getLocation().x);
            jsonObject.set("locationY", frame.getLocation().y);
            jsonObject.set("novels", novelArray);
            try (FileWriter file = new FileWriter(BOOKMARK_FILE)) {
                file.write(jsonObject.toString());
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "保存记录失败: " + ex.getMessage());
            }
        }
    }

    public static void deleteRecord(JFrame frame, String filePath) {
        Path path = Paths.get(BOOKMARK_FILE);

        if(StrUtil.isNotBlank(filePath)) {
            JSONObject jsonObject;
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
            if(jsonObject.containsKey("novels")){
                novelArray = jsonObject.getJSONArray("novels");
                if(!novelArray.isEmpty()){
                    for (Object object : novelArray) {
                        JSONObject novel = (JSONObject)object;
                        if(novel.containsKey("filePath") && filePath.equals(novel.getStr("filePath"))){
                            novelArray.remove(object);
                            break;
                        }
                    }
                }
            }
            jsonObject.set("novels", novelArray);
            try (FileWriter file = new FileWriter(BOOKMARK_FILE)) {
                file.write(jsonObject.toString());
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "删除记录失败: " + ex.getMessage());
            }
        }
    }

    public static Map<String, NovelRecord> loadRecord(JFrame frame) {
        Map<String, NovelRecord> novelRecordMap = new LinkedHashMap<>();
        if(Files.exists(Paths.get(BOOKMARK_FILE))) {
            try {
                JSONObject jsonObject = new JSONObject(new FileReader(BOOKMARK_FILE));
                if (jsonObject.containsKey("width") && jsonObject.containsKey("height")) {
                    frame.setSize(jsonObject.getInt("width"), jsonObject.getInt("height"));
                }
                if(jsonObject.containsKey("locationX") && jsonObject.containsKey("locationY")){
                    frame.setLocation(jsonObject.getInt("locationX"), jsonObject.getInt("locationY"));
                }

                if(jsonObject.containsKey("novels")){
                    JSONArray novelArray = jsonObject.getJSONArray("novels");
                    if(!novelArray.isEmpty()){
                        List<NovelRecord> novelRecords = new ArrayList<>();
                        for (Object object : novelArray) {
                            JSONObject novel = (JSONObject)object;
                            novelRecords.add(BeanUtil.toBean(novel, NovelRecord.class));
                        }
                        novelRecords.sort(Comparator.comparing(NovelRecord::getLastReadingTime).reversed());
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
        return novelRecordMap;
    }
}
