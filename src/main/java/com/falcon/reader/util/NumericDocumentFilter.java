package com.falcon.reader.util;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * 数字输入过滤器
 * 用于 Swing 文本组件（如 JTextField），限制只能输入数字字符（0-9）。
 * 通过继承 {@link DocumentFilter} 并重写 {@link #replace} 方法，
 * 在用户输入或粘贴内容时拦截并过滤掉非数字字符。
 *
 * @author zxy
 * @date 2024/10/21
 */
public class NumericDocumentFilter extends DocumentFilter {

    /**
     * 拦截文本替换操作（包括键入、粘贴、setText 等）
     * 在字符插入/替换到文档之前，对输入文本进行过滤，仅保留数字字符。
     *
     * @param fb     过滤器转发对象，用于执行实际替换操作
     * @param offset 插入位置的偏移量
     * @param length 要删除的字符数（替换操作时被替换的字符长度）
     * @param text   将要插入的文本（可能包含非数字字符）
     * @param attrs  文本属性集（通常为 null）
     * @throws BadLocationException 当偏移量无效时抛出
     */
    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {
        // 如果插入文本为空，则直接返回（不执行任何操作）
        if (text == null) {
            return;
        }

        // 过滤：遍历输入字符串的每个字符，仅保留数字字符
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }

        // 调用父类方法执行实际的文档替换操作（此时已过滤为非数字字符串）
        super.replace(fb, offset, length, sb.toString(), attrs);
    }

    // 注：通常不需要重写 insertString 和 remove 方法，remove 不涉及字符内容，
    // insertString 可通过 replace 统一处理（JTextComponent 会调用 replace）。
}