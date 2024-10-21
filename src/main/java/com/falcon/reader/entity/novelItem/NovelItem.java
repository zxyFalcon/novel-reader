package com.falcon.reader.entity.novelItem;

import lombok.Data;

/**
 * 小说记录
 *
 * @author zxy
 * @date 2024/10/18 16:05
 **/
@Data
public class NovelItem {
    private String fileName;
    private String filePath;

    public NovelItem(String filePath){

        // 找到最后一个反斜杠的位置
        int lastIndex = filePath.lastIndexOf('\\');
        this.filePath = filePath.substring(0, lastIndex + 1);
        // 截取最后一个反斜杠后面的内容
        this.fileName = (lastIndex != -1) ? filePath.substring(lastIndex + 1) : filePath;
    }
}

