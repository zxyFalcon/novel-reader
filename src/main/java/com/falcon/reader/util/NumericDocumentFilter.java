package com.falcon.reader.util;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * 数字过滤器
 *
 * @author zxy
 * @date 2024/10/21
 */
public class NumericDocumentFilter extends DocumentFilter {
    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws
            BadLocationException {
        if (text == null) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        super.replace(fb, offset, length, sb.toString(), attrs);
    }
}
