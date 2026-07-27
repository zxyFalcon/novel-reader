package com.falcon.reader.domain;

import java.net.URL;

/**
 * Chapter entry detected from the original novel text.
 */
public class Chapter {
    private final String title;
    private final int pageIndex;
    private final int lineNumber;
    private final URL target;

    public Chapter(String title, int pageIndex, int lineNumber) {
        this(title, pageIndex, lineNumber, null);
    }

    public Chapter(String title, int pageIndex, int lineNumber, URL target) {
        this.title = title;
        this.pageIndex = pageIndex;
        this.lineNumber = lineNumber;
        this.target = target;
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

    public URL getTarget() {
        return target;
    }

    @Override
    public String toString() {
        return title + "  ·  第 " + (pageIndex + 1) + " 页";
    }
}
