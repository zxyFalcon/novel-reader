package com.falcon.reader.model;

import com.falcon.reader.entity.Chapter;
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
    private final Consumer<Integer> jumpCallback;

    public ChapterDialog(JFrame frame, List<Chapter> chapters, Font font, int maxPages, int initialPage, Consumer<Integer> jumpCallback) {
        this.frame = frame;
        this.chapters = chapters;
        this.font = font;
        this.maxPages = maxPages;
        this.initialPage = initialPage;
        this.jumpCallback = jumpCallback;
    }

    public void show() {
        if (chapters.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "未识别到目录", "目录", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        DefaultListModel<Chapter> listModel = new DefaultListModel<>();
        chapters.forEach(listModel::addElement);

        JList<Chapter> chapterList = new JList<>(listModel);
        chapterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chapterList.setFont(font.deriveFont(Font.PLAIN, font.getSize()));
        chapterList.setFixedCellHeight(chapterList.getFontMetrics(chapterList.getFont()).getHeight() + 12);

        JDialog dialog = new JDialog(frame, "目录", Dialog.ModalityType.APPLICATION_MODAL);
        JScrollPane scrollPane = new JScrollPane(chapterList);
        scrollPane.setPreferredSize(new Dimension(Math.min(520, Math.max(320, frame.getWidth() - 120)),
                Math.min(520, Math.max(260, frame.getHeight() - 120))));

        JButton jumpButton = new JButton("跳转");
        JButton cancelButton = new JButton("取消");
        JTextField pageField = new JTextField(String.valueOf(initialPage + 1), 6);
        ((AbstractDocument) pageField.getDocument()).setDocumentFilter(new NumericDocumentFilter());

        chapterList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Chapter chapter = chapterList.getSelectedValue();
                if (chapter != null) {
                    pageField.setText(String.valueOf(chapter.getPageIndex() + 1));
                }
            }
        });

        jumpButton.addActionListener(e -> jumpToPage(pageField, dialog));
        cancelButton.addActionListener(e -> dialog.dispose());

        chapterList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Chapter chapter = chapterList.getSelectedValue();
                    if (chapter != null) {
                        pageField.setText(String.valueOf(chapter.getPageIndex() + 1));
                    }
                    jumpToPage(pageField, dialog);
                }
            }
        });

        JPanel jumpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        jumpPanel.add(new JLabel("页码:"));
        jumpPanel.add(pageField);

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
}
