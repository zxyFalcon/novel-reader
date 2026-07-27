package com.falcon.reader.epub;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** A read-only EPUB ZIP exposed as hierarchical URLs for Swing's HTML renderer. */
final class EpubArchive implements AutoCloseable {
    private static final String URL_PREFIX = "epub://book/";

    private final ZipFile zip;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final URLStreamHandler handler = new URLStreamHandler() {
        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            final String entryName = entryName(url);
            final ZipEntry entry = getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new FileNotFoundException("EPUB resource not found: " + entryName);
            }
            return new URLConnection(url) {
                @Override
                public void connect() {
                    connected = true;
                }

                @Override
                public long getContentLengthLong() {
                    return entry.getSize();
                }

                @Override
                public String getContentType() {
                    return URLConnection.guessContentTypeFromName(entryName);
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    connect();
                    return open(entryName);
                }
            };
        }
    };

    EpubArchive(File file) throws IOException {
        zip = new ZipFile(file);
    }

    URL url(String entryName) throws IOException {
        String normalized = normalizeEntryName(entryName);
        return new URL(null, URL_PREFIX + normalized, handler);
    }

    boolean contains(String entryName) throws IOException {
        return getEntry(normalizeEntryName(entryName)) != null;
    }

    InputStream open(String entryName) throws IOException {
        ensureOpen();
        String normalized = normalizeEntryName(entryName);
        ZipEntry entry = zip.getEntry(normalized);
        if (entry == null || entry.isDirectory()) {
            throw new FileNotFoundException("EPUB resource not found: " + normalized);
        }
        return zip.getInputStream(entry);
    }

    private ZipEntry getEntry(String entryName) throws IOException {
        ensureOpen();
        return zip.getEntry(entryName);
    }

    private String entryName(URL url) throws IOException {
        if (!"book".equals(url.getHost())) {
            throw new IOException("Invalid EPUB resource host");
        }
        try {
            return normalizeEntryName(url.toURI().getPath());
        } catch (java.net.URISyntaxException ex) {
            throw new IOException("Invalid EPUB resource URL", ex);
        }
    }

    static String normalizeEntryName(String value) throws IOException {
        String path = value == null ? "" : value.replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        java.util.ArrayDeque<String> parts = new java.util.ArrayDeque<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (parts.isEmpty()) {
                    throw new IOException("EPUB resource escapes archive root");
                }
                parts.removeLast();
            } else {
                parts.addLast(part);
            }
        }
        return String.join("/", parts);
    }

    private void ensureOpen() throws IOException {
        if (closed.get()) {
            throw new IOException("EPUB archive is closed");
        }
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            zip.close();
        }
    }
}
