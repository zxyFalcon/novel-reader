package com.falcon.reader.epub;

import com.falcon.reader.domain.Chapter;

import java.net.URL;
import java.awt.Dimension;
import java.io.InputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Parsed EPUB resources in spine order. The book owns its open ZIP archive;
 * callers must invoke {@link #close()} when the book is replaced or closed.
 */
public class EpubBook implements AutoCloseable {
    private static final Pattern IMAGE_SOURCE = Pattern.compile(
            "(?is)<img\\b[^>]*\\bsrc\\s*=\\s*(['\"])([^'\"]+)\\1");
    private final EpubArchive archive;
    private final List<URL> sections;
    private final List<Chapter> chapters;
    private final EpubHtmlProcessor processor = new EpubHtmlProcessor();
    private final Map<Integer, Future<EpubHtmlProcessor.ProcessedContent>> sectionCache = new ConcurrentHashMap<>();
    private final Map<String, Dimension> imageSizeCache = new ConcurrentHashMap<>();
    private final ExecutorService preloadExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "epub-preloader");
        thread.setDaemon(true);
        return thread;
    });

    public EpubBook(EpubArchive archive, List<URL> sections, List<Chapter> chapters) {
        this.archive = archive;
        this.sections = Collections.unmodifiableList(sections);
        this.chapters = Collections.unmodifiableList(chapters);
    }

    public List<URL> getSections() {
        return sections;
    }

    public List<Chapter> getChapters() {
        return chapters;
    }

    /** Returns the spine index for a URL belonging to this EPUB, ignoring its fragment. */
    public int findSectionIndex(URL target) {
        if (target == null) return -1;
        for (int i = 0; i < sections.size(); i++) {
            URL section = sections.get(i);
            if (section.getProtocol().equalsIgnoreCase(target.getProtocol())
                    && equal(section.getHost(), target.getHost())
                    && equal(section.getPath(), target.getPath())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean equal(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    public EpubHtmlProcessor.ProcessedContent prepareSection(int index) throws IOException {
        if (index < 0 || index >= sections.size()) {
            throw new IndexOutOfBoundsException("EPUB section " + index);
        }
        Future<EpubHtmlProcessor.ProcessedContent> future = sectionCache.computeIfAbsent(index,
                key -> preloadExecutor.submit(() -> loadSection(key)));
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("EPUB section loading interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("Unable to prepare EPUB section", cause);
        }
    }

    public void preloadAround(int index) {
        preload(index - 1);
        preload(index + 1);
    }

    public Dimension getImageSize(URL image) {
        Dimension size = imageSizeCache.get(image.toExternalForm());
        return size == null ? null : new Dimension(size);
    }

    private void preload(int index) {
        if (index >= 0 && index < sections.size()) {
            sectionCache.computeIfAbsent(index, key -> preloadExecutor.submit(() -> loadSection(key)));
        }
    }

    private EpubHtmlProcessor.ProcessedContent loadSection(int index) throws Exception {
        URL section = sections.get(index);
        EpubHtmlProcessor.ProcessedContent content;
        try (InputStream input = section.openStream()) {
            // The processor has no shared mutable state in readProcessed().
            content = processor.readProcessed(input);
        }
        Matcher matcher = IMAGE_SOURCE.matcher(content.getHtml());
        while (matcher.find()) {
            try {
                URL image = new URL(section, matcher.group(2));
                String key = image.toExternalForm();
                if (!imageSizeCache.containsKey(key)) {
                    Dimension size = readImageSize(image);
                    if (size != null) {
                        imageSizeCache.putIfAbsent(key, size);
                    }
                }
            } catch (Exception ignored) {
                // An optional or unsupported image must not prevent opening the section.
            }
        }
        return content;
    }

    private Dimension readImageSize(URL image) throws IOException {
        try (InputStream input = image.openStream(); ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return width > 0 && height > 0 ? new Dimension(width, height) : null;
            } finally {
                reader.dispose();
            }
        }
    }

    @Override
    public void close() {
        preloadExecutor.shutdownNow();
        sectionCache.clear();
        imageSizeCache.clear();
        try {
            archive.close();
        } catch (IOException ignored) {
            // Closing is best-effort; there are no extracted temporary files to clean up.
        }
    }
}
