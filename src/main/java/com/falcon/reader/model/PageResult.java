package com.falcon.reader.model;

import com.falcon.reader.entity.Chapter;

import java.util.List;

/**
 * Pages and chapter positions produced by one pagination pass.
 */
public class PageResult {
    private final List<String> pages;
    private final List<Chapter> chapters;
    private final List<Integer> pageStartOffsets;
    private final int totalLength;

    public PageResult(List<String> pages, List<Chapter> chapters) {
        this(pages, chapters, new java.util.ArrayList<>(), 0);
    }

    public PageResult(List<String> pages, List<Chapter> chapters, List<Integer> pageStartOffsets, int totalLength) {
        this.pages = pages;
        this.chapters = chapters;
        this.pageStartOffsets = pageStartOffsets;
        this.totalLength = totalLength;
    }

    public List<String> getPages() {
        return pages;
    }

    public List<Chapter> getChapters() {
        return chapters;
    }

    public List<Integer> getPageStartOffsets() {
        return pageStartOffsets;
    }

    public int getTotalLength() {
        return totalLength;
    }
}
