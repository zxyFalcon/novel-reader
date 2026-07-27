package com.falcon.reader.epub;

import com.falcon.reader.domain.Chapter;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 * Parsed EPUB resources in spine order. The book owns its extraction directory;
 * callers must invoke {@link #close()} when the book is replaced or closed.
 */
public class EpubBook implements AutoCloseable {
    private final File extractedDirectory;
    private final List<URL> sections;
    private final List<Chapter> chapters;

    public EpubBook(File extractedDirectory, List<URL> sections, List<Chapter> chapters) {
        this.extractedDirectory = extractedDirectory;
        this.sections = Collections.unmodifiableList(sections);
        this.chapters = Collections.unmodifiableList(chapters);
    }

    public List<URL> getSections() {
        return sections;
    }

    public List<Chapter> getChapters() {
        return chapters;
    }

    @Override
    public void close() {
        EpubParser.deleteRecursively(extractedDirectory);
    }
}
