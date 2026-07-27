package com.falcon.reader.bookshelf;

import com.falcon.reader.domain.NovelRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Search, grouping and sorting rules for bookshelf records.
 */
public final class BookshelfQuery {
    private BookshelfQuery() {
    }

    public static List<Map.Entry<String, NovelRecord>> apply(Map<String, NovelRecord> source, String keyword,
                                                             SortMode sortMode, GroupMode groupMode) {
        List<Map.Entry<String, NovelRecord>> result = new ArrayList<>();
        source.forEach((path, record) -> {
            if (matchesSearch(path, keyword) && matchesGroup(path, record, groupMode)) {
                result.add(new java.util.AbstractMap.SimpleEntry<>(path, record));
            }
        });
        sort(result, sortMode);
        return result;
    }

    private static boolean matchesSearch(String filePath, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        String normalizedPath = filePath.toLowerCase(Locale.ROOT);
        Path path = Paths.get(filePath);
        String fileName = path.getFileName() == null ? filePath : path.getFileName().toString();
        return fileName.toLowerCase(Locale.ROOT).contains(normalizedKeyword) || normalizedPath.contains(normalizedKeyword);
    }

    private static boolean matchesGroup(String path, NovelRecord record, GroupMode groupMode) {
        boolean exists = Files.isRegularFile(Paths.get(path));
        switch (groupMode) {
            case UNREAD:
                return exists && isUnread(record);
            case READING:
                return exists && !isUnread(record) && !isFinished(record);
            case FINISHED:
                return exists && isFinished(record);
            case MISSING:
                return !exists;
            default:
                return true;
        }
    }

    private static boolean isFinished(NovelRecord record) {
        if (record != null && record.getCurrentOffset() != null && record.getTotalLength() != null
                && record.getTotalLength() > 0 && record.getCurrentOffset() >= record.getTotalLength()) {
            return true;
        }
        return record != null && record.getCurrentPage() != null && record.getTotalPages() != null
                && record.getTotalPages() > 0 && record.getCurrentPage() + 1 >= record.getTotalPages();
    }

    private static boolean isUnread(NovelRecord record) {
        if (record != null && record.getCurrentOffset() != null) {
            return record.getCurrentOffset() <= 0;
        }
        return record == null || record.getCurrentPage() == null || record.getCurrentPage() <= 0;
    }

    private static void sort(List<Map.Entry<String, NovelRecord>> records, SortMode sortMode) {
        if (sortMode == SortMode.NAME) {
            Collator collator = Collator.getInstance(Locale.CHINA);
            records.sort((first, second) -> collator.compare(fileName(first.getKey()), fileName(second.getKey())));
        } else {
            records.sort((first, second) -> compareLastReadingTime(first.getValue(), second.getValue()));
        }
    }

    private static int compareLastReadingTime(NovelRecord first, NovelRecord second) {
        LocalDateTime firstTime = first == null ? null : first.getLastReadingTime();
        LocalDateTime secondTime = second == null ? null : second.getLastReadingTime();
        if (firstTime == null) return secondTime == null ? 0 : 1;
        if (secondTime == null) return -1;
        return secondTime.compareTo(firstTime);
    }

    private static String fileName(String filePath) {
        Path path = Paths.get(filePath);
        return path.getFileName() == null ? filePath : path.getFileName().toString();
    }

    public enum SortMode {NAME, LAST_READING_TIME}

    public enum GroupMode {
        ALL("全部"), UNREAD("未读"), READING("阅读中"), FINISHED("已读"), MISSING("失效");

        private final String displayName;

        GroupMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
