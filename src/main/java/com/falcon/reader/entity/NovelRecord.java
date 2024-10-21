package com.falcon.reader.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小说记录
 *
 * @author zxy
 * @date 2024/10/18 16:05
 **/
@Data
public class NovelRecord {
    private String filePath;
    private Integer currentPage;
    private LocalDateTime lastReadingTime;
}
