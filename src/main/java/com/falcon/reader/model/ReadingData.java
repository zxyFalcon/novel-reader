package com.falcon.reader.model;

import com.falcon.reader.entity.NovelConfig;
import com.falcon.reader.entity.NovelRecord;

import java.util.Map;

/**
 * Snapshot of reader settings and saved novel records.
 */
public class ReadingData {
    private final NovelConfig config;
    private final Map<String, NovelRecord> records;

    public ReadingData(NovelConfig config, Map<String, NovelRecord> records) {
        this.config = config;
        this.records = records;
    }

    public NovelConfig getConfig() {
        return config;
    }

    public Map<String, NovelRecord> getRecords() {
        return records;
    }
}
