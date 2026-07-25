package com.falcon.reader.model;

import com.falcon.reader.entity.Chapter;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/** Parsed EPUB resources in spine reading order. */
public class EpubBook {
    private final File extractedDirectory;
    private final List<URL> sections;
    private final List<Chapter> chapters;

    public EpubBook(File extractedDirectory, List<URL> sections, List<Chapter> chapters) {
        this.extractedDirectory = extractedDirectory;
        this.sections = Collections.unmodifiableList(sections);
        this.chapters = Collections.unmodifiableList(chapters);
    }

    public File getExtractedDirectory() {
        return extractedDirectory;
    }

    public List<URL> getSections() {
        return sections;
    }

    public List<Chapter> getChapters() {
        return chapters;
    }
}
