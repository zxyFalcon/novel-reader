package com.falcon.reader.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小说信息
 *
 * @author zxy
 * @date 2024/10/18 16:05
 **/
@Data
public class NovelInfo {
    private String fileName;
    private Integer currentPage;
    private LocalDateTime lastReadingTime;
}
