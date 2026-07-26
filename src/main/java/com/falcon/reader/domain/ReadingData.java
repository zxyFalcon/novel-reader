package com.falcon.reader.domain;

import java.util.Map;

/**
 * Loaded reader settings, window state and saved novel records.
 */
public class ReadingData {
    private final NovelConfig config;
    private final Map<String, NovelRecord> records;
    private final WindowState windowState;

    public ReadingData(NovelConfig config, Map<String, NovelRecord> records, WindowState windowState) {
        this.config = config;
        this.records = records;
        this.windowState = windowState;
    }

    public NovelConfig getConfig() {
        return config;
    }

    public Map<String, NovelRecord> getRecords() {
        return records;
    }

    public WindowState getWindowState() {
        return windowState;
    }
}
