package com.falcon.reader.entity;

/**
 * Chapter entry detected from the original novel text.
 */
public class Chapter {
    private final String title;
    private final int pageIndex;
    private final int lineNumber;

    public Chapter(String title, int pageIndex, int lineNumber) {
        this.title = title;
        this.pageIndex = pageIndex;
        this.lineNumber = lineNumber;
    }

    public String getTitle() {
        return title;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public String toString() {
        return title + "  ·  第 " + (pageIndex + 1) + " 页";
    }
}
