package com.falcon.reader.model;

import com.falcon.reader.entity.Chapter;

import javax.swing.*;
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
    private final Consumer<Integer> jumpCallback;

    public ChapterDialog(JFrame frame, List<Chapter> chapters, Font font, Consumer<Integer> jumpCallback) {
        this.frame = frame;
        this.chapters = chapters;
        this.font = font;
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
        jumpButton.addActionListener(e -> jumpToSelected(chapterList, dialog));
        cancelButton.addActionListener(e -> dialog.dispose());

        chapterList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    jumpToSelected(chapterList, dialog);
                }
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 1));
        buttonPanel.add(jumpButton);
        buttonPanel.add(cancelButton);

        dialog.setLayout(new BorderLayout(8, 2));
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void jumpToSelected(JList<Chapter> chapterList, JDialog dialog) {
        Chapter chapter = chapterList.getSelectedValue();
        if (chapter == null) {
            return;
        }
        jumpCallback.accept(chapter.getPageIndex());
        dialog.dispose();
    }
}
