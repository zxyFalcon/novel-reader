package com.falcon.reader.ui.view;

import com.falcon.reader.domain.NovelConfig;
import com.falcon.reader.epub.EpubHtmlProcessor;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLDecoder;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小说阅读视图，管理小说内容的显示
 *
 * @author zxy
 * @date 2026/2/11 16:18
 **/
public class NovelView {
    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("<img\\b[^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SRC_ATTRIBUTE_PATTERN = Pattern.compile(
            "\\bsrc\\s*=\\s*(['\"])([^'\"]+)\\1", Pattern.CASE_INSENSITIVE);
    private final JFrame frame;
    private final JLabel label;
    private final JEditorPane epubPane;
    private final JScrollPane epubScrollPane;
    private boolean visible = false;
    private boolean epubMode = false;
    private Consumer<URL> hyperlinkCallback;
    private Runnable returnCallback;
    private Runnable chapterCallback;
    private Runnable settingsCallback;
    private Runnable previousSectionCallback;
    private Runnable nextSectionCallback;
    private Runnable epubPageChangeCallback;
    private URL currentSection;
    private Point epubDragStart;
    private Point frameDragStart;
    private boolean epubWasDragged;
    private boolean footnoteDismissedByPress;
    private final EpubHtmlProcessor htmlProcessor = new EpubHtmlProcessor();
    private FootnoteBubble footnoteBubble;
    private int epubPageIndex;
    private int epubPageCount = 1;
    private final java.util.List<Integer> epubPagePositions = new java.util.ArrayList<>();
    private boolean openEpubAtEnd;
    private Integer pendingEpubPageIndex;
    private final Timer epubReflowTimer;

    /**
     * 构造函数，初始化小说视图
     * @param frame 主窗口
     * @param config 小说配置
     * @author zxy
     * @date 2024/10/21
     */
    public NovelView(JFrame frame, NovelConfig config) {
        this.frame = frame;
        label = new JLabel();
        label.setLayout(new FlowLayout());
        label.setBounds(0, 0, frame.getWidth(), frame.getHeight());
        label.setFont(config.getFont());
        label.setForeground(config.getForeground());
        label.setVerticalAlignment(JLabel.TOP);
        label.setVerticalTextPosition(JLabel.TOP);
        frame.add(label);

        epubPane = new JEditorPane();
        epubPane.setEditable(false);
        epubPane.setHighlighter(null);
        epubPane.setContentType("text/html");
        epubPane.setOpaque(false);
        epubPane.setBackground(new Color(0, 0, 0, 0));
        epubPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        epubPane.setFont(config.getFont());
        epubPane.setForeground(config.getForeground());
        epubPane.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && !epubWasDragged) {
                if (showFootnote(event)) {
                    return;
                }
                URL target = resolveHyperlink(event);
                if (target != null && hyperlinkCallback != null) {
                    hyperlinkCallback.accept(target);
                }
            }
        });
        epubPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                footnoteDismissedByPress = event.getButton() == MouseEvent.BUTTON1 && footnoteBubble != null;
                hideFootnoteBubble();
                if (event.getButton() == MouseEvent.BUTTON1) {
                    epubDragStart = event.getLocationOnScreen();
                    frameDragStart = frame.getLocation();
                    epubWasDragged = false;
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                epubDragStart = null;
                frameDragStart = null;
                SwingUtilities.invokeLater(() -> {
                    epubWasDragged = false;
                    footnoteDismissedByPress = false;
                });
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1 && footnoteDismissedByPress) {
                    footnoteDismissedByPress = false;
                    return;
                }
                if (event.getButton() == MouseEvent.BUTTON3 && returnCallback != null) {
                    returnCallback.run();
                } else if (event.getButton() == MouseEvent.BUTTON2 && chapterCallback != null) {
                    chapterCallback.run();
                } else if (event.getButton() == MouseEvent.BUTTON1 && !epubWasDragged
                        && !isInteractiveElementAt(event.getPoint()) && settingsCallback != null) {
                    settingsCallback.run();
                }
            }
        });
        epubPane.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                if (epubDragStart == null || frameDragStart == null) {
                    return;
                }
                Point current = event.getLocationOnScreen();
                int deltaX = current.x - epubDragStart.x;
                int deltaY = current.y - epubDragStart.y;
                if (Math.abs(deltaX) + Math.abs(deltaY) >= 3) {
                    epubWasDragged = true;
                    frame.setLocation(frameDragStart.x + deltaX, frameDragStart.y + deltaY);
                    epubPane.setCaretPosition(Math.max(0, epubPane.getCaretPosition()));
                }
            }
        });

        epubScrollPane = new JScrollPane(epubPane);
        epubScrollPane.setOpaque(false);
        epubScrollPane.getViewport().setOpaque(false);
        epubScrollPane.getViewport().setBackground(new Color(0, 0, 0, 0));
        epubScrollPane.setBorder(null);
        epubScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        epubScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        epubScrollPane.setBounds(0, 0, frame.getWidth(), frame.getHeight());
        epubScrollPane.addMouseWheelListener(this::handleEpubWheelAtBoundary);
        epubScrollPane.getVerticalScrollBar().addAdjustmentListener(event -> {
            if (footnoteBubble != null) {
                hideFootnoteBubble();
            }
        });
        epubScrollPane.setVisible(false);
        frame.add(epubScrollPane);

        epubReflowTimer = new Timer(180, event -> {
            if (epubMode && currentSection != null && visible) {
                try {
                    pendingEpubPageIndex = epubPageIndex;
                    showEpub(currentSection);
                } catch (IOException ex) {
                    showEpubMessage("无法重新排版 EPUB：" + ex.getMessage());
                }
            }
        });
        epubReflowTimer.setRepeats(false);
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                updateBounds(frame.getWidth(), frame.getHeight());
                if (epubMode && visible) {
                    epubReflowTimer.restart();
                }
            }
        });
    }

    /**
     * 获取显示标签
     * @return JLabel
     * @author zxy
     * @date 2024/10/21
     */
    public JLabel getLabel() {
        return label;
    }

    public void setEpubCallbacks(Consumer<URL> hyperlinkCallback, Runnable returnCallback, Runnable chapterCallback,
            Runnable previousSectionCallback, Runnable nextSectionCallback, Runnable epubPageChangeCallback,
            Runnable settingsCallback) {
        this.hyperlinkCallback = hyperlinkCallback;
        this.returnCallback = returnCallback;
        this.chapterCallback = chapterCallback;
        this.previousSectionCallback = previousSectionCallback;
        this.nextSectionCallback = nextSectionCallback;
        this.epubPageChangeCallback = epubPageChangeCallback;
        this.settingsCallback = settingsCallback;
    }

    private boolean isInteractiveElementAt(Point point) {
        if (!(epubPane.getDocument() instanceof HTMLDocument)) {
            return false;
        }
        int offset = epubPane.viewToModel(point);
        if (offset < 0) {
            return false;
        }
        Element element = ((HTMLDocument) epubPane.getDocument()).getCharacterElement(offset);
        while (element != null) {
            javax.swing.text.AttributeSet attributes = element.getAttributes();
            Object anchor = attributes.getAttribute(javax.swing.text.html.HTML.Tag.A);
            if (anchor instanceof javax.swing.text.AttributeSet
                    && ((javax.swing.text.AttributeSet) anchor)
                            .getAttribute(javax.swing.text.html.HTML.Attribute.HREF) != null) {
                return true;
            }
            if (attributes.getAttribute(javax.swing.text.html.HTML.Attribute.HREF) != null) {
                return true;
            }
            element = element.getParentElement();
        }
        return false;
    }

    public void restoreEpubPage(Integer pageIndex) {
        pendingEpubPageIndex = pageIndex;
    }

    public int getEpubPageIndex() {
        return epubPageIndex;
    }

    public int getEpubPageCount() {
        return epubPageCount;
    }

    /**
     * Returns the spine section currently visible in the continuous EPUB document.
     */
    public int getCurrentEpubSectionIndex(int sectionCount) {
        if (!epubMode || sectionCount <= 0 || !(epubPane.getDocument() instanceof HTMLDocument)) {
            return 0;
        }

        HTMLDocument document = (HTMLDocument) epubPane.getDocument();
        int visibleY = epubScrollPane.getViewport().getViewPosition().y;
        int currentSectionIndex = 0;
        for (int i = 0; i < sectionCount; i++) {
            Element section = findElementById(document.getDefaultRootElement(), "codex-section-" + i);
            if (section == null) {
                continue;
            }
            try {
                Rectangle bounds = epubPane.modelToView(section.getStartOffset());
                if (bounds != null && bounds.y <= visibleY) {
                    currentSectionIndex = i;
                } else if (bounds != null) {
                    break;
                }
            } catch (BadLocationException ignored) {
                // Keep the closest section that could be resolved.
            }
        }
        return currentSectionIndex;
    }

    public boolean navigateEpubLink(URL target) {
        if (target == null || currentSection == null || target.getRef() == null
                || !target.getProtocol().equalsIgnoreCase(currentSection.getProtocol())
                || !target.getPath().equals(currentSection.getPath())
                || !(epubPane.getDocument() instanceof HTMLDocument)) {
            return false;
        }
        String reference = target.getRef();
        try {
            reference = URLDecoder.decode(reference, "UTF-8");
        } catch (Exception ignored) {
            // Keep the raw fragment when it is not URL encoded.
        }
        HTMLDocument document = (HTMLDocument) epubPane.getDocument();
        Element targetElement = findElementById(document.getDefaultRootElement(), reference);
        if (targetElement == null) {
            return false;
        }
        try {
            Rectangle targetBounds = epubPane.modelToView(targetElement.getStartOffset());
            if (targetBounds == null) {
                return false;
            }
            epubPageIndex = findEpubPageForPosition(targetBounds.y);
            showEpubPage();
            notifyEpubPageChanged();
            return true;
        } catch (BadLocationException ignored) {
            return false;
        }
    }

    public void showTxt() {
        epubMode = false;
        epubScrollPane.setVisible(false);
        label.setVisible(true);
        visible = true;
    }

    public void showEpub(URL section) throws IOException {
        hideFootnoteBubble();
        epubMode = true;
        currentSection = section;
        label.setVisible(false);
        epubScrollPane.setVisible(true);
        HTMLEditorKit kit = new HTMLEditorKit();
        HTMLDocument document = (HTMLDocument) kit.createDefaultDocument();
        document.setBase(section);
        // EPUB XHTML usually contains an XML declaration or a meta charset tag.
        // The content has already been decoded as UTF-8, so prevent Swing's HTML
        // parser from aborting with a message-less ChangedCharSetException.
        document.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
        try (InputStream input = section.openStream()) {
            try {
                kit.read(new StringReader(resizeImages(htmlProcessor.readAndProcess(input))), document, 0);
            } catch (javax.swing.text.BadLocationException ex) {
                throw new IOException("EPUB 章节内容无效", ex);
            }
        }
        epubPane.setEditorKit(kit);
        epubPane.setDocument(document);
        epubPane.setCaretPosition(0);
        boolean startAtEnd = openEpubAtEnd;
        Integer requestedPage = pendingEpubPageIndex;
        openEpubAtEnd = false;
        pendingEpubPageIndex = null;
        SwingUtilities.invokeLater(() -> layoutEpubPages(startAtEnd, section.getRef(), requestedPage));
        visible = true;
    }

    public void showEpubMessage(String message) {
        epubMode = true;
        label.setVisible(false);
        epubScrollPane.setVisible(true);
        epubPane.setText("<html><body>" + escapeHtml(message) + "</body></html>");
        visible = true;
    }

    public boolean isEpubMode() {
        return epubMode;
    }

    public Font getReadingFont() {
        return epubMode ? epubPane.getFont() : label.getFont();
    }

    public void updateBounds(int width, int height) {
        label.setBounds(0, 0, width, height);
        epubScrollPane.setBounds(0, 0, width, height);
    }

    public void updateReadingStyle(Font font, Color foreground) {
        label.setFont(font);
        label.setForeground(foreground);
        epubPane.setFont(font);
        epubPane.setForeground(foreground);
        if (epubMode && currentSection != null) {
            pendingEpubPageIndex = epubPageIndex;
            try {
                showEpub(currentSection);
            } catch (IOException ex) {
                showEpubMessage("无法重新排版 EPUB：" + ex.getMessage());
            }
        }
    }

    /**
     * 显示小说视图
     * @author zxy
     * @date 2024/10/21
     */
    public void show() {
        label.setVisible(!epubMode);
        epubScrollPane.setVisible(epubMode);
        visible = true;
    }

    /**
     * 隐藏小说视图
     * @author zxy
     * @date 2024/10/21
     */
    public void hide() {
        hideFootnoteBubble();
        label.setVisible(false);
        epubScrollPane.setVisible(false);
        visible = false;
    }

    /**
     * 检查视图是否可见
     * @return boolean
     * @author zxy
     * @date 2024/10/21
     */
    public boolean isVisible() {
        return visible;
    }

    private void handleEpubWheelAtBoundary(MouseWheelEvent event) {
        hideFootnoteBubble();
        event.consume();
        if (event.getWheelRotation() < 0) {
            if (epubPageIndex > 0) {
                epubPageIndex--;
                showEpubPage();
                notifyEpubPageChanged();
            } else if (!isContinuousEpubDocument() && previousSectionCallback != null) {
                openEpubAtEnd = true;
                previousSectionCallback.run();
            }
        } else if (event.getWheelRotation() > 0) {
            if (epubPageIndex < epubPageCount - 1) {
                epubPageIndex++;
                showEpubPage();
                notifyEpubPageChanged();
            } else if (!isContinuousEpubDocument() && nextSectionCallback != null) {
                openEpubAtEnd = false;
                nextSectionCallback.run();
            }
        }
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String resizeImages(String html) {
        int viewportWidth = epubScrollPane.getViewport().getExtentSize().width;
        int maxWidth = Math.max(80, (viewportWidth > 0 ? viewportWidth : frame.getWidth()) - 28);
        int viewportHeight = epubScrollPane.getViewport().getExtentSize().height;
        int maxHeight = Math.max(80, (viewportHeight > 0 ? viewportHeight : frame.getHeight()) - 28);
        Matcher matcher = IMG_TAG_PATTERN.matcher(html);
        StringBuffer resizedHtml = new StringBuffer();
        while (matcher.find()) {
            String tag = matcher.group();
            Matcher sourceMatcher = SRC_ATTRIBUTE_PATTERN.matcher(tag);
            if (!sourceMatcher.find() || currentSection == null) {
                matcher.appendReplacement(resizedHtml, Matcher.quoteReplacement(tag));
                continue;
            }
            try {
                URL imageUrl = new URL(currentSection, sourceMatcher.group(2));
                BufferedImage image = ImageIO.read(imageUrl);
                if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                    matcher.appendReplacement(resizedHtml, Matcher.quoteReplacement(tag));
                    continue;
                }
                double scale = Math.min(1.0, Math.min(maxWidth / (double) image.getWidth(),
                        maxHeight / (double) image.getHeight()));
                int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
                String resizedTag = tag
                        .replaceAll("(?i)\\s+(?:width|height)\\s*=\\s*(['\"])[^'\"]*\\1", "")
                        .replaceFirst("\\s*/?>$", " width=\"" + width + "\" height=\"" + height + "\">");
                matcher.appendReplacement(resizedHtml, Matcher.quoteReplacement(resizedTag));
            } catch (Exception ignored) {
                matcher.appendReplacement(resizedHtml, Matcher.quoteReplacement(tag));
            }
        }
        matcher.appendTail(resizedHtml);
        return resizedHtml.toString();
    }

    private void layoutEpubPages(boolean startAtEnd, String reference, Integer requestedPage) {
        Dimension extent = epubScrollPane.getViewport().getExtentSize();
        int pageWidth = Math.max(1, extent.width);
        int pageHeight = Math.max(1, extent.height);

        epubPane.setSize(pageWidth, Short.MAX_VALUE);
        Dimension preferred = epubPane.getPreferredSize();
        int contentHeight = Math.max(pageHeight, preferred.height);
        epubPane.setSize(pageWidth, contentHeight);

        int maxScroll = Math.max(0, contentHeight - pageHeight);
        epubPagePositions.clear();
        epubPagePositions.add(0);
        int position = 0;
        while (position < maxScroll) {
            int target = Math.min(maxScroll, position + pageHeight);
            try {
                int offset = epubPane.viewToModel(new Point(4, target));
                Rectangle line = epubPane.modelToView(offset);
                if (line != null && line.y > position + 4 && line.y <= maxScroll) {
                    target = line.y;
                }
            } catch (BadLocationException ignored) {
                // The height-based target remains a safe fallback.
            }
            if (target <= position) {
                break;
            }
            epubPagePositions.add(target);
            position = target;
        }
        epubPageCount = Math.max(1, epubPagePositions.size());

        if (requestedPage != null) {
            epubPageIndex = Math.max(0, Math.min(requestedPage, epubPageCount - 1));
        } else if (reference != null && !reference.isEmpty()) {
            Element target = findElementById(epubPane.getDocument().getDefaultRootElement(), reference);
            if (target != null) {
                try {
                    Rectangle bounds = epubPane.modelToView(target.getStartOffset());
                    epubPageIndex = bounds == null ? 0 : findEpubPageForPosition(bounds.y);
                } catch (BadLocationException ignored) {
                    epubPageIndex = 0;
                }
            } else {
                epubPageIndex = 0;
            }
        } else if (startAtEnd) {
            epubPageIndex = epubPageCount - 1;
        } else {
            epubPageIndex = 0;
        }
        showEpubPage();
        notifyEpubPageChanged();
    }

    private int findEpubPageForPosition(int position) {
        int page = 0;
        for (int i = 0; i < epubPagePositions.size(); i++) {
            if (epubPagePositions.get(i) <= position) {
                page = i;
            } else {
                break;
            }
        }
        return page;
    }

    private void showEpubPage() {
        if (epubPagePositions.isEmpty()) {
            return;
        }
        epubPageIndex = Math.max(0, Math.min(epubPageIndex, epubPagePositions.size() - 1));
        epubScrollPane.getViewport().setViewPosition(new Point(0, epubPagePositions.get(epubPageIndex)));
    }

    private boolean isContinuousEpubDocument() {
        return currentSection != null && currentSection.getPath().endsWith(".novel-reader-combined.html");
    }

    private void notifyEpubPageChanged() {
        if (epubPageChangeCallback != null) {
            epubPageChangeCallback.run();
        }
    }

    private URL resolveHyperlink(HyperlinkEvent event) {
        if (event.getURL() != null) {
            return event.getURL();
        }
        if (currentSection == null || event.getDescription() == null) {
            return null;
        }
        try {
            return new URL(currentSection, event.getDescription());
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean showFootnote(HyperlinkEvent event) {
        String description = event.getDescription();
        if (description == null || !description.contains("#")) {
            return false;
        }
        try {
            String id = URLDecoder.decode(description.substring(description.indexOf('#') + 1), "UTF-8");
            String extractedNote = htmlProcessor.getFootnote(id);
            if ((extractedNote == null || extractedNote.isEmpty())) {
                URL target = resolveHyperlink(event);
                extractedNote = htmlProcessor.loadLinkedFootnote(target, id);
            }
            if (extractedNote != null && !extractedNote.isEmpty()) {
                showFootnoteBubble(extractedNote, event);
                return true;
            }
            if (!(epubPane.getDocument() instanceof HTMLDocument)) {
                return false;
            }
            HTMLDocument document = (HTMLDocument) epubPane.getDocument();
            Element target = document.getElement(id);
            if (target == null) {
                return false;
            }
            String text = document.getText(target.getStartOffset(),
                    Math.max(0, target.getEndOffset() - target.getStartOffset())).trim();
            if (text.isEmpty()) {
                return false;
            }
            showFootnoteBubble(text, event);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Element findElementById(Element element, String id) {
        Object htmlId = element.getAttributes().getAttribute(javax.swing.text.html.HTML.Attribute.ID);
        Object rawId = element.getAttributes().getAttribute("id");
        if (id.equals(htmlId) || id.equals(rawId)) {
            return element;
        }
        for (int i = 0; i < element.getElementCount(); i++) {
            Element match = findElementById(element.getElement(i), id);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private void showFootnoteBubble(String text, HyperlinkEvent event) {
        hideFootnoteBubble();
        JLayeredPane layeredPane = frame.getRootPane().getLayeredPane();
        Point anchor = getLinkAnchor(event, layeredPane);
        int maxTextWidth = Math.min(360, Math.max(220, frame.getWidth() - 60));
        footnoteBubble = new FootnoteBubble(text, epubPane.getFont(), maxTextWidth);

        Dimension bubbleSize = footnoteBubble.getPreferredSize();
        boolean showBelow = anchor.y + bubbleSize.height + 12 <= layeredPane.getHeight();
        int bubbleX = Math.max(8, Math.min(anchor.x - bubbleSize.width / 2,
                layeredPane.getWidth() - bubbleSize.width - 8));
        int bubbleY = showBelow ? anchor.y : anchor.y - bubbleSize.height;
        bubbleY = Math.max(8, Math.min(bubbleY, layeredPane.getHeight() - bubbleSize.height - 8));

        footnoteBubble.setArrowOnTop(showBelow);
        footnoteBubble.setArrowX(anchor.x - bubbleX);
        footnoteBubble.setBounds(bubbleX, bubbleY, bubbleSize.width, bubbleSize.height);
        layeredPane.add(footnoteBubble, JLayeredPane.POPUP_LAYER);
        layeredPane.revalidate();
        layeredPane.repaint();
    }

    private Point getLinkAnchor(HyperlinkEvent event, JLayeredPane layeredPane) {
        try {
            Element source = event.getSourceElement();
            if (source != null) {
                Rectangle bounds = epubPane.modelToView(source.getStartOffset());
                if (bounds != null) {
                    return SwingUtilities.convertPoint(epubPane, bounds.x + bounds.width / 2,
                            bounds.y + bounds.height, layeredPane);
                }
            }
        } catch (BadLocationException ignored) {
            // Fall back to the current mouse location below.
        }
        Point mouse = epubPane.getMousePosition();
        if (mouse == null) {
            mouse = new Point(epubPane.getWidth() / 2, epubPane.getHeight() / 2);
        }
        return SwingUtilities.convertPoint(epubPane, mouse, layeredPane);
    }

    private void hideFootnoteBubble() {
        if (footnoteBubble == null) {
            return;
        }
        Container parent = footnoteBubble.getParent();
        if (parent != null) {
            parent.remove(footnoteBubble);
            parent.revalidate();
            parent.repaint();
        }
        footnoteBubble = null;
    }

    private final class FootnoteBubble extends JComponent {
        private static final long serialVersionUID = 1L;
        private static final int PADDING = 12;
        private static final int ARROW_HEIGHT = 12;
        private static final int ARC = 18;
        private final JTextArea textArea;
        private final JScrollPane contentScroll;
        private final Dimension preferredSize;
        private boolean arrowOnTop;
        private int arrowX;

        private FootnoteBubble(String text, Font font, int textWidth) {
            setLayout(null);
            setOpaque(false);
            textArea = new JTextArea(text);
            textArea.setEditable(false);
            textArea.setFocusable(false);
            textArea.setHighlighter(null);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setOpaque(false);
            textArea.setForeground(Color.WHITE);
            textArea.setFont(font);
            textArea.setMargin(new Insets(0, 0, 0, 0));
            textArea.setSize(textWidth, Short.MAX_VALUE);
            int textHeight = Math.max(font.getSize() + 8, textArea.getPreferredSize().height);
            textHeight = Math.min(textHeight, Math.max(80, frame.getHeight() - 100));
            preferredSize = new Dimension(textWidth + PADDING * 2,
                    textHeight + PADDING * 2 + ARROW_HEIGHT);
            contentScroll = new JScrollPane(textArea);
            contentScroll.setBorder(null);
            contentScroll.setOpaque(false);
            contentScroll.getViewport().setOpaque(false);
            contentScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            add(contentScroll);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    hideFootnoteBubble();
                }
            });
        }

        private void setArrowOnTop(boolean arrowOnTop) {
            this.arrowOnTop = arrowOnTop;
        }

        private void setArrowX(int arrowX) {
            this.arrowX = Math.max(24, Math.min(arrowX, preferredSize.width - 24));
        }

        @Override
        public Dimension getPreferredSize() {
            return preferredSize;
        }

        @Override
        public void doLayout() {
            int textY = PADDING + (arrowOnTop ? ARROW_HEIGHT : 0);
            contentScroll.setBounds(PADDING, textY, getWidth() - PADDING * 2,
                    getHeight() - PADDING * 2 - ARROW_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int bodyY = arrowOnTop ? ARROW_HEIGHT : 0;
                int bodyHeight = getHeight() - ARROW_HEIGHT;
                Polygon arrow = arrowOnTop
                        ? new Polygon(new int[] {arrowX, arrowX - 11, arrowX + 11},
                                new int[] {0, ARROW_HEIGHT + 2, ARROW_HEIGHT + 2}, 3)
                        : new Polygon(new int[] {arrowX, arrowX - 11, arrowX + 11},
                                new int[] {getHeight(), getHeight() - ARROW_HEIGHT - 2,
                                        getHeight() - ARROW_HEIGHT - 2}, 3);

                g.setColor(new Color(35, 35, 38, 235));
                g.fillRoundRect(0, bodyY, getWidth() - 1, bodyHeight - 1, ARC, ARC);
                g.fillPolygon(arrow);
                g.setColor(new Color(210, 210, 215, 210));
                g.setStroke(new BasicStroke(1.2f));
                g.drawRoundRect(0, bodyY, getWidth() - 1, bodyHeight - 1, ARC, ARC);
            } finally {
                g.dispose();
            }
        }
    }
}
