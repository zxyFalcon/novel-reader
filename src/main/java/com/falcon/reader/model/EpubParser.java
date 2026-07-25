package com.falcon.reader.model;

import com.falcon.reader.entity.Chapter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Extracts an EPUB and resolves its package, spine and EPUB 2/3 navigation. */
public final class EpubParser {
    private EpubParser() {
    }

    public static EpubBook parse(String filePath) throws Exception {
        File extractionRoot = Files.createTempDirectory("novel-reader-epub-").toFile();
        boolean success = false;
        try {
            extractSafely(new File(filePath), extractionRoot);
            File container = new File(extractionRoot, "META-INF/container.xml");
            if (!container.isFile()) {
                throw new IOException("EPUB 缺少 META-INF/container.xml");
            }

            Document containerDocument = parseXml(container);
            Element rootFile = firstElement(containerDocument, "rootfile");
            if (rootFile == null || rootFile.getAttribute("full-path").isEmpty()) {
                throw new IOException("EPUB 未声明内容包");
            }

            File packageFile = resolveInside(extractionRoot, rootFile.getAttribute("full-path"));
            Document packageDocument = parseXml(packageFile);
            File packageDirectory = packageFile.getParentFile();

            Map<String, ManifestItem> manifest = readManifest(packageDocument, packageDirectory, extractionRoot);
            List<URL> originalSections = readSpine(packageDocument, manifest);
            if (originalSections.isEmpty()) {
                throw new IOException("EPUB 阅读顺序为空");
            }

            List<Chapter> chapters = readNavigation(packageDocument, manifest, originalSections);
            if (chapters.isEmpty()) {
                for (int i = 0; i < originalSections.size(); i++) {
                    String path = new File(originalSections.get(i).toURI()).getName();
                    chapters.add(new Chapter(path, i, i + 1));
                }
            }
            List<URL> sections = combineSections(extractionRoot, originalSections);
            success = true;
            return new EpubBook(extractionRoot, sections, chapters);
        } finally {
            if (!success) {
                deleteRecursively(extractionRoot);
            }
        }
    }

    private static Map<String, ManifestItem> readManifest(Document document, File packageDirectory, File root)
            throws Exception {
        Map<String, ManifestItem> result = new LinkedHashMap<>();
        NodeList items = document.getElementsByTagNameNS("*", "item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String id = item.getAttribute("id");
            String href = item.getAttribute("href");
            if (id.isEmpty() || href.isEmpty()) {
                continue;
            }
            File file = resolveInside(root, relativize(root, packageDirectory) + decodedPath(href));
            result.put(id, new ManifestItem(id, href, item.getAttribute("media-type"),
                    item.getAttribute("properties"), file));
        }
        return result;
    }

    private static List<URL> readSpine(Document document, Map<String, ManifestItem> manifest) throws Exception {
        List<URL> sections = new ArrayList<>();
        NodeList itemRefs = document.getElementsByTagNameNS("*", "itemref");
        for (int i = 0; i < itemRefs.getLength(); i++) {
            ManifestItem item = manifest.get(((Element) itemRefs.item(i)).getAttribute("idref"));
            if (item != null && item.file.isFile()) {
                sections.add(item.file.toURI().toURL());
            }
        }
        return sections;
    }

    private static List<URL> combineSections(File extractionRoot, List<URL> originalSections) throws Exception {
        Map<String, Integer> sectionIndexes = new HashMap<>();
        for (int i = 0; i < originalSections.size(); i++) {
            sectionIndexes.put(canonicalWithoutFragment(originalSections.get(i)), i);
        }

        StringBuilder combinedHead = new StringBuilder();
        StringBuilder combinedBody = new StringBuilder(64 * 1024);
        for (int i = 0; i < originalSections.size(); i++) {
            URL section = originalSections.get(i);
            String xhtml = new String(Files.readAllBytes(new File(section.toURI()).toPath()), StandardCharsets.UTF_8);
            java.util.regex.Matcher headMatcher = java.util.regex.Pattern
                    .compile("(?is)<head\\b[^>]*>(.*?)</head\\s*>").matcher(xhtml);
            if (headMatcher.find()) {
                combinedHead.append(extractScopedStyles(headMatcher.group(1), section, i));
            }
            java.util.regex.Matcher bodyMatcher = java.util.regex.Pattern
                    .compile("(?is)<body\\b[^>]*>(.*)</body\\s*>").matcher(xhtml);
            String body = bodyMatcher.find() ? bodyMatcher.group(1) : xhtml;
            body = rewriteSectionReferences(body, section, i, sectionIndexes);
            body = scopeEmbeddedStyles(body, section, i);
            combinedBody.append("<div id=\"codex-section-").append(i).append("\">")
                    .append(body).append("</div>");
        }
        String combined = "<html><head><meta charset=\"UTF-8\">" + combinedHead
                + "</head><body>" + combinedBody + "</body></html>";

        File combinedFile = new File(extractionRoot, ".novel-reader-combined.html");
        Files.write(combinedFile.toPath(), combined.getBytes(StandardCharsets.UTF_8));
        URL combinedUrl = combinedFile.toURI().toURL();
        List<URL> sections = new ArrayList<>();
        for (int i = 0; i < originalSections.size(); i++) {
            sections.add(new URL(combinedUrl.toExternalForm() + "#codex-section-" + i));
        }
        return sections;
    }

    private static String extractScopedStyles(String head, URL section, int sectionIndex) {
        StringBuilder styles = new StringBuilder();
        java.util.regex.Matcher inlineMatcher = java.util.regex.Pattern
                .compile("(?is)<style\\b[^>]*>(.*?)</style\\s*>").matcher(head);
        while (inlineMatcher.find()) {
            styles.append("<style>").append(scopeCss(inlineMatcher.group(1), section, sectionIndex))
                    .append("</style>");
        }

        java.util.regex.Matcher linkMatcher = java.util.regex.Pattern
                .compile("(?is)<link\\b([^>]*\\b(?:rel\\s*=\\s*(['\"])?stylesheet\\2|href\\s*=)[^>]*)>")
                .matcher(head);
        while (linkMatcher.find()) {
            java.util.regex.Matcher hrefMatcher = java.util.regex.Pattern
                    .compile("(?i)\\bhref\\s*=\\s*(['\"])([^'\"]+)\\1")
                    .matcher(linkMatcher.group());
            if (!hrefMatcher.find()) {
                continue;
            }
            try {
                URL cssUrl = new URL(section, hrefMatcher.group(2));
                byte[] bytes;
                try (InputStream input = cssUrl.openStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        output.write(buffer, 0, count);
                    }
                    bytes = output.toByteArray();
                }
                styles.append("<style>").append(scopeCss(new String(bytes, StandardCharsets.UTF_8), cssUrl, sectionIndex))
                        .append("</style>");
            } catch (Exception ignored) {
                // A missing optional stylesheet must not prevent reading.
            }
        }
        return styles.toString();
    }

    private static String scopeEmbeddedStyles(String body, URL section, int sectionIndex) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?is)<style\\b[^>]*>(.*?)</style\\s*>").matcher(body);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(
                    "<style>" + scopeCss(matcher.group(1), section, sectionIndex) + "</style>"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String scopeCss(String css, URL cssBase, int sectionIndex) {
        String withoutComments = css.replaceAll("(?s)/\\*.*?\\*/", "");
        java.util.regex.Matcher ruleMatcher = java.util.regex.Pattern
                .compile("(?s)([^{}]+)\\{([^{}]*)}").matcher(withoutComments);
        StringBuilder scoped = new StringBuilder();
        String prefix = "#codex-section-" + sectionIndex;
        while (ruleMatcher.find()) {
            String selectors = ruleMatcher.group(1).trim();
            String declarations = rewriteCssUrls(ruleMatcher.group(2), cssBase);
            if (selectors.startsWith("@font-face") || selectors.startsWith("@page")) {
                scoped.append(selectors).append('{').append(declarations).append('}');
                continue;
            }
            if (selectors.startsWith("@")) {
                continue;
            }
            String[] selectorParts = selectors.split(",");
            for (int i = 0; i < selectorParts.length; i++) {
                if (i > 0) {
                    scoped.append(',');
                }
                String selector = selectorParts[i].trim();
                selector = selector.replaceFirst("(?i)^html(?:\\s+body)?\\b", "")
                        .replaceFirst("(?i)^body\\b", "").trim();
                scoped.append(prefix);
                if (!selector.isEmpty()) {
                    scoped.append(' ').append(selector);
                }
            }
            scoped.append('{').append(declarations).append('}');
        }
        return scoped.toString();
    }

    private static String rewriteCssUrls(String declarations, URL cssBase) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)url\\(\\s*(['\"]?)([^)'\"]+)\\1\\s*\\)").matcher(declarations);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(2).trim();
            String replacement = value;
            try {
                if (!value.startsWith("data:") && !new URI(value).isAbsolute()) {
                    replacement = new URL(cssBase, value).toExternalForm();
                }
            } catch (Exception ignored) {
                // Keep the original URL when it cannot be resolved.
            }
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement("url('" + replacement + "')"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String rewriteSectionReferences(String html, URL section, int sectionIndex,
            Map<String, Integer> sectionIndexes) {
        String rewritten = rewriteIds(html, sectionIndex);
        java.util.regex.Pattern attributePattern = java.util.regex.Pattern.compile(
                "(?i)\\b(src|href)\\s*=\\s*(['\"])([^'\"]+)\\2");
        java.util.regex.Matcher matcher = attributePattern.matcher(rewritten);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String attribute = matcher.group(1);
            String value = matcher.group(3);
            String replacementValue = rewriteReference(value, section, sectionIndex, sectionIndexes,
                    "href".equalsIgnoreCase(attribute));
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(
                    attribute + "=" + matcher.group(2) + replacementValue + matcher.group(2)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String rewriteIds(String html, int sectionIndex) {
        java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile(
                "(?i)\\b(id|name)\\s*=\\s*(['\"])([^'\"]+)\\2");
        java.util.regex.Matcher matcher = idPattern.matcher(html);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(
                    matcher.group(1) + "=" + matcher.group(2) + "s" + sectionIndex + "-"
                            + matcher.group(3) + matcher.group(2)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String rewriteReference(String value, URL section, int sectionIndex,
            Map<String, Integer> sectionIndexes, boolean hyperlink) {
        try {
            if (value.startsWith("#")) {
                return "#s" + sectionIndex + "-" + value.substring(1);
            }
            URI valueUri = new URI(value);
            if (valueUri.isAbsolute() || value.startsWith("data:")) {
                return value;
            }
            URL resolved = new URL(section, value);
            if (hyperlink) {
                Integer targetSection = sectionIndexes.get(canonicalWithoutFragment(resolved));
                if (targetSection != null) {
                    String fragment = resolved.getRef();
                    return fragment == null || fragment.isEmpty()
                            ? "#codex-section-" + targetSection
                            : "#s" + targetSection + "-" + fragment;
                }
            }
            return resolved.toExternalForm();
        } catch (Exception ignored) {
            return value;
        }
    }

    private static List<Chapter> readNavigation(Document document, Map<String, ManifestItem> manifest,
            List<URL> sections) throws Exception {
        Map<String, Integer> spineIndexes = new HashMap<>();
        for (int i = 0; i < sections.size(); i++) {
            spineIndexes.put(canonicalWithoutFragment(sections.get(i)), i);
        }

        for (ManifestItem item : manifest.values()) {
            if (containsToken(item.properties, "nav") && item.file.isFile()) {
                List<Chapter> chapters = readEpub3Navigation(item.file, spineIndexes);
                if (!chapters.isEmpty()) {
                    return chapters;
                }
            }
        }

        Element spine = firstElement(document, "spine");
        String tocId = spine == null ? "" : spine.getAttribute("toc");
        ManifestItem ncx = manifest.get(tocId);
        if (ncx == null) {
            for (ManifestItem item : manifest.values()) {
                if ("application/x-dtbncx+xml".equals(item.mediaType)) {
                    ncx = item;
                    break;
                }
            }
        }
        return ncx == null || !ncx.file.isFile()
                ? new ArrayList<>() : readNcxNavigation(ncx.file, spineIndexes);
    }

    private static List<Chapter> readEpub3Navigation(File navFile, Map<String, Integer> spineIndexes) throws Exception {
        Document document = parseXml(navFile);
        Element toc = null;
        NodeList navs = document.getElementsByTagNameNS("*", "nav");
        for (int i = 0; i < navs.getLength(); i++) {
            Element nav = (Element) navs.item(i);
            String type = nav.getAttribute("epub:type");
            if (type.isEmpty()) {
                type = nav.getAttributeNS("http://www.idpf.org/2007/ops", "type");
            }
            if (containsToken(type, "toc")) {
                toc = nav;
                break;
            }
        }
        List<Chapter> result = new ArrayList<>();
        if (toc != null) {
            NodeList links = toc.getElementsByTagNameNS("*", "a");
            for (int i = 0; i < links.getLength(); i++) {
                Element link = (Element) links.item(i);
                addNavigationEntry(result, link.getTextContent(), navFile, link.getAttribute("href"), spineIndexes);
            }
        }
        return result;
    }

    private static List<Chapter> readNcxNavigation(File ncxFile, Map<String, Integer> spineIndexes) throws Exception {
        Document document = parseXml(ncxFile);
        List<Chapter> result = new ArrayList<>();
        NodeList points = document.getElementsByTagNameNS("*", "navPoint");
        for (int i = 0; i < points.getLength(); i++) {
            Element point = (Element) points.item(i);
            Element content = descendant(point, "content");
            Element text = descendant(point, "text");
            if (content != null) {
                addNavigationEntry(result, text == null ? "章节 " + (i + 1) : text.getTextContent(), ncxFile,
                        content.getAttribute("src"), spineIndexes);
            }
        }
        return result;
    }

    private static void addNavigationEntry(List<Chapter> result, String title, File navigationFile, String href,
            Map<String, Integer> spineIndexes) throws Exception {
        if (href == null || href.trim().isEmpty()) {
            return;
        }
        URL target = navigationFile.toURI().resolve(href).toURL();
        Integer pageIndex = spineIndexes.get(canonicalWithoutFragment(target));
        if (pageIndex == null) {
            return;
        }
        String normalizedTitle = title == null ? "" : title.replaceAll("\\s+", " ").trim();
        if (normalizedTitle.isEmpty()) {
            normalizedTitle = "章节 " + (result.size() + 1);
        }
        result.add(new Chapter(normalizedTitle, pageIndex, result.size() + 1));
    }

    private static void extractSafely(File epub, File destination) throws IOException {
        String rootPath = destination.getCanonicalPath() + File.separator;
        try (ZipFile zip = new ZipFile(epub)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File output = new File(destination, entry.getName());
                if (!output.getCanonicalPath().startsWith(rootPath)) {
                    throw new IOException("EPUB 包含非法路径");
                }
                if (entry.isDirectory()) {
                    if (!output.isDirectory() && !output.mkdirs()) {
                        throw new IOException("无法创建 EPUB 临时目录");
                    }
                    continue;
                }
                File parent = output.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("无法创建 EPUB 临时目录");
                }
                try (InputStream input = zip.getInputStream(entry); OutputStream out = new FileOutputStream(output)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        out.write(buffer, 0, count);
                    }
                }
            }
        }
    }

    private static Document parseXml(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream stream = new FileInputStream(file)) {
            return builder.parse(new InputSource(stream));
        }
    }

    private static Element firstElement(Document document, String localName) {
        NodeList elements = document.getElementsByTagNameNS("*", localName);
        return elements.getLength() == 0 ? null : (Element) elements.item(0);
    }

    private static Element descendant(Element element, String localName) {
        NodeList elements = element.getElementsByTagNameNS("*", localName);
        return elements.getLength() == 0 ? null : (Element) elements.item(0);
    }

    private static File resolveInside(File root, String relativePath) throws IOException {
        File resolved = new File(root, relativePath).getCanonicalFile();
        String rootPath = root.getCanonicalPath() + File.separator;
        if (!resolved.getPath().startsWith(rootPath)) {
            throw new IOException("EPUB 引用了包外资源");
        }
        return resolved;
    }

    private static String relativize(File root, File directory) {
        URI relative = root.toURI().relativize(directory.toURI());
        String value = relative.getPath();
        return value.isEmpty() || value.endsWith("/") ? value : value + "/";
    }

    private static String decodedPath(String href) {
        try {
            String path = new URI(href).getPath();
            return path == null ? "" : path;
        } catch (Exception ignored) {
            int fragment = href.indexOf('#');
            int query = href.indexOf('?');
            int end = fragment < 0 ? href.length() : fragment;
            if (query >= 0) {
                end = Math.min(end, query);
            }
            return href.substring(0, end);
        }
    }

    private static String canonicalWithoutFragment(URL url) throws Exception {
        URI uri = url.toURI();
        URI withoutFragment = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null);
        return new File(withoutFragment).getCanonicalPath();
    }

    private static boolean containsToken(String values, String token) {
        return values != null && Arrays.asList(values.trim().split("\\s+")).contains(token);
    }

    public static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    private static final class ManifestItem {
        private final String id;
        private final String href;
        private final String mediaType;
        private final String properties;
        private final File file;

        private ManifestItem(String id, String href, String mediaType, String properties, File file) {
            this.id = id;
            this.href = href;
            this.mediaType = mediaType;
            this.properties = properties;
            this.file = file;
        }
    }
}
