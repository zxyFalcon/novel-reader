package com.falcon.reader.entity.novelItem;

import lombok.Data;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 小说项
 *
 * @author zxy
 * @date 2024/10/18 16:05
 **/
@Data
public class NovelItem {
    private String fullPath;
    private String fileName;
    private String filePath;

    public NovelItem(String filePath){
        Path path = Paths.get(filePath);
        Path parent = path.getParent();

        this.fullPath = filePath;
        this.fileName = path.getFileName() == null ? filePath : path.getFileName().toString();
        this.filePath = parent == null ? "" : parent.toString() + File.separator;
    }
}

