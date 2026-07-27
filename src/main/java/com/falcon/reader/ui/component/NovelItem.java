package com.falcon.reader.ui.component;

import com.falcon.reader.domain.NovelRecord;
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
    private Integer currentOffset;
    private Integer totalLength;
    private boolean fileExists;

    public NovelItem(String filePath, NovelRecord record){
        Path path = Paths.get(filePath);
        Path parent = path.getParent();

        this.fullPath = filePath;
        this.fileName = path.getFileName() == null ? filePath : path.getFileName().toString();
        this.filePath = parent == null ? "" : parent + File.separator;
        this.fileExists = Files.isRegularFile(path);
        if (record != null) {
            this.currentPage = record.getCurrentPage();
            this.totalPages = record.getTotalPages();
            this.currentOffset = record.getCurrentOffset();
            this.totalLength = record.getTotalLength();
        }
    }

    public Integer getProgressPercent() {
        if (fullPath != null && fullPath.toLowerCase(java.util.Locale.ROOT).endsWith(".epub")
                && currentPage != null && currentPage >= 0 && totalPages != null && totalPages > 0) {
            double sectionProgress = 0.0;
            if (currentOffset != null && currentOffset >= 0 && totalLength != null && totalLength > 1) {
                sectionProgress = Math.min(1.0, currentOffset / (double) (totalLength - 1));
            }
            double bookProgress = (Math.min(currentPage, totalPages - 1) + sectionProgress) / totalPages;
            return (int) Math.max(0, Math.min(100, Math.round(bookProgress * 100.0)));
        }
        if (currentOffset != null && currentOffset >= 0 && totalLength != null && totalLength > 0) {
            if (currentPage != null && totalPages != null && totalPages > 0 && currentPage + 1 >= totalPages) {
                return 100;
            }
            return (int) Math.min(100, currentOffset * 100L / totalLength);
        }
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

