package com.falcon.reader.entity.novelItem;

import com.falcon.reader.entity.NovelRecord;
import lombok.Data;

import java.io.File;
import java.nio.file.Files;
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
    private Integer currentPage;
    private Integer totalPages;
    private boolean fileExists;

    public NovelItem(String filePath){
        this(filePath, null);
    }

    public NovelItem(String filePath, NovelRecord record){
        Path path = Paths.get(filePath);
        Path parent = path.getParent();

        this.fullPath = filePath;
        this.fileName = path.getFileName() == null ? filePath : path.getFileName().toString();
        this.filePath = parent == null ? "" : parent.toString() + File.separator;
        this.fileExists = Files.isRegularFile(path);
        if (record != null) {
            this.currentPage = record.getCurrentPage();
            this.totalPages = record.getTotalPages();
        }
    }

    public Integer getProgressPercent() {
        if (currentPage == null || currentPage < 0 || totalPages == null || totalPages <= 0) {
            return null;
        }

        int displayPage = Math.min(currentPage + 1, totalPages);
        return displayPage * 100 / totalPages;
    }

    public String getProgressPercentText() {
        Integer percent = getProgressPercent();
        return percent == null ? "--%" : percent + "%";
    }
}

