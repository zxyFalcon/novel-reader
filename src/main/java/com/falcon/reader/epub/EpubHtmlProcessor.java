package com.falcon.reader.epub;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prepares EPUB XHTML for the Swing renderer and extracts linked footnotes.
 */
public final class EpubHtmlProcessor {
    private static final Pattern NOTE_BLOCK = Pattern.compile(
            "<(aside|li|ol|div|section|p|footer)\\b([^>]*(?:epub:type\\s*=\\s*(['\"])[^'\"]*(?:footnote|endnote)[^'\"]*\\3"
                    + "|role\\s*=\\s*(['\"])[^'\"]*(?:doc-footnote|doc-endnote)[^'\"]*\\4"
                    + "|class\\s*=\\s*(['\"])[^'\"]*(?:footnotes?|endnotes?)[^'\"]*\\5)[^>]*)>"
                    + "(.*?)</\\1\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ID_ELEMENT = Pattern.compile(
            "<([a-z][\\w:-]*)\\b([^>]*\\bid\\s*=\\s*(['\"])([^'\"]+)\\3[^>]*)>(.*?)</\\1\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ID_ATTRIBUTE = Pattern.compile("\\bid\\s*=\\s*(['\"])([^'\"]+)\\1",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANCHOR = Pattern.compile("<a\\b([^>]*)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HREF = Pattern.compile("\\bhref\\s*=\\s*(['\"])([^'\"]+)\\1",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NOTE_HINT = Pattern.compile(
            "(?i)(?:epub:type|role|class)\\s*=\\s*(['\"])[^'\"]*(?:noteref|footnote|endnote)[^'\"]*\\1");

    private final Map<String, String> footnotes = new HashMap<>();

    private static String stripDocumentHeader(String value) {
        return value.replaceFirst("(?is)^\\s*<\\?xml\\s+[^?]*\\?>", "")
                .replaceFirst("(?is)^\\s*<!DOCTYPE[^>]*>", "");
    }

    private static void collectNoteBlocks(String html, Map<String, String> target) {
        Matcher matcher = NOTE_BLOCK.matcher(html);
        while (matcher.find()) {
            String block = matcher.group(0);
            String inner = matcher.group(6);
            Matcher id = ID_ATTRIBUTE.matcher(block.substring(0, block.indexOf('>') + 1));
            if (id.find()) store(target, id.group(2), inner);
            Matcher nested = ID_ELEMENT.matcher(inner);
            while (nested.find()) store(target, nested.group(4), nested.group(5));
            Matcher nestedId = ID_ATTRIBUTE.matcher(inner);
            while (nestedId.find()) store(target, nestedId.group(2), inner);
        }
    }

    private static void collectReferencedNotes(String html, Map<String, String> target) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher anchor = ANCHOR.matcher(html);
        while (anchor.find()) {
            Matcher href = HREF.matcher(anchor.group(1));
            if (!href.find() || !href.group(2).startsWith("#")) continue;
            String id = decode(href.group(2).substring(1));
            boolean noteLink = NOTE_HINT.matcher(anchor.group(1)).find()
                    || id.toLowerCase(java.util.Locale.ROOT).matches(".*(?:footnote|endnote|note[-_]?\\d+|fn[-_]?\\d+).*");
            Matcher targetId = Pattern.compile("\\bid\\s*=\\s*(['\"])" + Pattern.quote(id) + "\\1",
                    Pattern.CASE_INSENSITIVE).matcher(html);
            if (noteLink && targetId.find() && targetId.start() > anchor.end()) ids.add(id);
        }
        for (String id : ids) {
            Matcher adjacent = Pattern.compile("<a\\b[^>]*\\bid\\s*=\\s*(['\"])" + Pattern.quote(id)
                            + "\\1[^>]*>\\s*</a>\\s*<(p|div|li)\\b[^>]*>(.*?)</\\2\\s*>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
            if (adjacent.find()) {
                store(target, id, adjacent.group(3));
                continue;
            }
            Matcher element = targetPattern(id).matcher(html);
            if (element.find()) store(target, id, element.group(3));
        }
    }

    private static Pattern targetPattern(String id) {
        return Pattern.compile("<([a-z][\\w:-]*)\\b[^>]*\\bid\\s*=\\s*(['\"])" + Pattern.quote(id)
                + "\\2[^>]*>(.*?)</\\1\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    private static void store(Map<String, String> target, String id, String html) {
        String text = plainText(html);
        if (!id.isEmpty() && !text.isEmpty()) target.put(id, text);
    }

    private static String plainText(String html) {
        return html.replaceAll("(?is)<a\\b[^>]*(?:backlink|backref)[^>]*>.*?</a>", " ")
                .replaceAll("(?is)<[^>]+>", " ").replace("&nbsp;", " ").replace("&#160;", " ")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replaceAll("\\s+", " ").trim();
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        byte[] bytes = output.toByteArray();
        int offset = bytes.length >= 3 && (bytes[0] & 0xff) == 0xef && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf ? 3 : 0;
        return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }

    public String readAndProcess(InputStream input) throws IOException {
        return process(readUtf8(input));
    }

    private String process(String xhtml) {
        footnotes.clear();
        String html = stripDocumentHeader(xhtml);
        html = html.replaceAll("(?is)<svg(?:\\:[a-z0-9_-]+)?\\b[^>]*>", "")
                .replaceAll("(?is)</svg(?:\\:[a-z0-9_-]+)?>", "")
                .replaceAll("(?is)<(?:svg:)?image\\b", "<img")
                .replaceAll("(?i)\\bxlink:href\\s*=", "src=")
                .replaceAll("(?is)(<img\\b[^>]*?)\\bhref\\s*=", "$1src=")
                .replaceAll("(?i)\\sbgcolor\\s*=\\s*(['\"]).*?\\1", "")
                .replaceAll("(?i)background(?:-color)?\\s*:\\s*[^;}]+;?", "");
        collectReferencedNotes(html, footnotes);
        collectNoteBlocks(html, footnotes);
        return html;
    }

    public String getFootnote(String id) {
        return footnotes.get(id);
    }

    public String loadLinkedFootnote(URL target, String id) {
        if (target == null || id == null || id.isEmpty() || !"file".equalsIgnoreCase(target.getProtocol())) return null;
        try (InputStream input = target.openStream()) {
            String xhtml = stripDocumentHeader(readUtf8(input));
            Map<String, String> linked = new HashMap<>();
            collectNoteBlocks(xhtml, linked);
            String note = linked.get(id);
            if (note != null && !note.isEmpty()) return note;
            Pattern targetPattern = targetPattern(id);
            Matcher matcher = targetPattern.matcher(xhtml);
            return matcher.find() ? plainText(matcher.group(3)) : null;
        } catch (IOException ignored) {
            return null;
        }
    }
}
