package com.falcon.reader.ui.dialog;

import com.falcon.reader.domain.Chapter;
import com.falcon.reader.util.NumericDocumentFilter;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modal chapter list used to jump to detected chapter pages.
 */
public class ChapterDialog {
    private final JFrame frame;
    private final List<Chapter> chapters;
    private final Font font;
    private final int maxPages;
    private final int initialPage;
    private final int initialChapterIndex;
    private final boolean epubMode;
    private final Consumer<Integer> jumpCallback;
    private final Consumer<Chapter> chapterJumpCallback;

    public ChapterDialog(JFrame frame, List<Chapter> chapters, Font font, int maxPages, int initialPage,
            int initialChapterIndex, boolean epubMode, Consumer<Integer> jumpCallback,
            Consumer<Chapter> chapterJumpCallback) {
        this.frame = frame;
        this.chapters = chapters;
        this.font = font;
        this.maxPages = maxPages;
        this.initialPage = initialPage;
        this.initialChapterIndex = initialChapterIndex;
        this.epubMode = epubMode;
        this.jumpCallback = jumpCallback;
        this.chapterJumpCallback = chapterJumpCallback;
    }

    public void show() {
        DefaultListModel<Chapter> listModel = new DefaultListModel<>();
        chapters.forEach(listModel::addElement);

        JList<Chapter> chapterList = new JList<>(listModel);
        chapterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chapterList.setFont(font.deriveFont(Font.PLAIN, font.getSize()));
        if (epubMode) {
            chapterList.setCellRenderer((list, chapter, index, selected, focused) -> {
                JLabel label = new JLabel(chapter.getTitle() + "  ·  第 " + (chapter.getPageIndex() + 1) + " 页");
                label.setOpaque(true);
                label.setFont(list.getFont());
                label.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
                label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
                label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
                return label;
            });
        }
        chapterList.setFixedCellHeight(chapterList.getFontMetrics(chapterList.getFont()).getHeight() + 12);
        int currentChapterIndex = initialChapterIndex >= 0 ? initialChapterIndex : findCurrentChapterIndex();
        if (currentChapterIndex >= 0) {
            chapterList.setSelectedIndex(currentChapterIndex);
        }

        JDialog dialog = new JDialog(frame, "目录", Dialog.ModalityType.APPLICATION_MODAL);
        JScrollPane scrollPane = new JScrollPane(chapterList);
        scrollPane.setPreferredSize(new Dimension(Math.min(520, Math.max(320, frame.getWidth() - 120)),
                Math.min(520, Math.max(260, frame.getHeight() - 120))));
        JLabel emptyChapterLabel = new JLabel("未识别到目录", SwingConstants.CENTER);
        emptyChapterLabel.setFont(font.deriveFont(Font.PLAIN, font.getSize()));
        emptyChapterLabel.setForeground(Color.GRAY);
        emptyChapterLabel.setOpaque(true);
        emptyChapterLabel.setBackground(chapterList.getBackground());
        if (chapters.isEmpty()) {
            scrollPane.setViewportView(emptyChapterLabel);
        }

        JButton jumpButton = new JButton("跳转");
        JButton cancelButton = new JButton("取消");
        JTextField pageField = new JTextField(String.valueOf(initialPage + 1), 7);
        ((AbstractDocument) pageField.getDocument()).setDocumentFilter(new NumericDocumentFilter());

        chapterList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Chapter chapter = chapterList.getSelectedValue();
                if (chapter != null && !chapters.isEmpty()) {
                    pageField.setText(String.valueOf(chapter.getPageIndex() + 1));
                }
            }
        });

        jumpButton.addActionListener(e -> {
            Chapter selected = chapterList.getSelectedValue();
            if (epubMode && selected != null
                    && pageField.getText().equals(String.valueOf(selected.getPageIndex() + 1))) {
                jumpToChapter(selected, dialog);
            } else {
                jumpToPage(pageField, dialog);
            }
        });
        cancelButton.addActionListener(e -> dialog.dispose());

        chapterList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Chapter chapter = chapterList.getSelectedValue();
                    if (chapter != null && !chapters.isEmpty()) {
                        if (epubMode) {
                            jumpToChapter(chapter, dialog);
                            return;
                        }
                        pageField.setText(String.valueOf(chapter.getPageIndex() + 1));
                    }
                    jumpToPage(pageField, dialog);
                }
            }
        });

        JPanel jumpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        jumpPanel.add(new JLabel(epubMode ? "章节:" : "页码:"));
        jumpPanel.add(pageField);
        jumpPanel.add(new JLabel("/ " + maxPages));

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 1));
        actionPanel.add(jumpButton);
        actionPanel.add(cancelButton);

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(jumpPanel, BorderLayout.WEST);
        buttonPanel.add(actionPanel, BorderLayout.EAST);

        dialog.setLayout(new BorderLayout(8, 2));
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        if (currentChapterIndex >= 0) {
            // The viewport has no usable extent until the dialog is shown. Running
            // this before setVisible() therefore leaves long chapter lists at the
            // beginning even though the current chapter is selected.
            SwingUtilities.invokeLater(() -> {
                chapterList.setSelectedIndex(currentChapterIndex);
                chapterList.ensureIndexIsVisible(currentChapterIndex);
            });
        }
        dialog.setVisible(true);
    }

    private void jumpToPage(JTextField pageField, JDialog dialog) {
        if (pageField.getText().isEmpty()) {
            return;
        }
        int page = Integer.parseInt(pageField.getText());
        if (page < 1 || page > maxPages) {
            JOptionPane.showMessageDialog(dialog, "页码范围为 1 到 " + maxPages, "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        jumpCallback.accept(page - 1);
        dialog.dispose();
    }

    private int findCurrentChapterIndex() {
        int selectedIndex = -1;
        int selectedPage = Integer.MIN_VALUE;
        for (int i = 0; i < chapters.size(); i++) {
            int chapterPage = chapters.get(i).getPageIndex();
            if (chapterPage <= initialPage && chapterPage > selectedPage) {
                selectedIndex = i;
                selectedPage = chapterPage;
            }
        }
        return selectedIndex;
    }

    private void jumpToChapter(Chapter chapter, JDialog dialog) {
        if (chapterJumpCallback != null) {
            chapterJumpCallback.accept(chapter);
        } else {
            jumpCallback.accept(chapter.getPageIndex());
        }
        dialog.dispose();
    }
}
