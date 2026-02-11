package com.falcon.reader.entity;

import lombok.Data;

import java.awt.*;

/**
 * 小说配置
 *
 * @author zxy
 * @date 2024/10/21 17:15
 **/
@Data
public class NovelConfig {
    private Color foreground = Color.WHITE;  // 默认设置为白色，与菜单字体颜色一致
    private Font font = new Font("Serif", Font.PLAIN, 16);  // 默认字体（可调整大小/样式）
}
