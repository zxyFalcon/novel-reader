package com.falcon.reader.model;

import com.falcon.reader.entity.Chapter;

import java.util.List;

/**
 * Pages and chapter positions produced by one pagination pass.
 */
public class PageResult {
    private final List<String> pages;
    private final List<Chapter> chapters;

    public PageResult(List<String> pages, List<Chapter> chapters) {
        this.pages = pages;
        this.chapters = chapters;
    }

    public List<String> getPages() {
        return pages;
    }

    public List<Chapter> getChapters() {
        return chapters;
    }
}
