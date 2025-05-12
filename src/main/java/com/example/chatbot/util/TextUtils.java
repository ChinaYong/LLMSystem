package com.example.chatbot.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本工具：将长文本拆分成多个短片段
 */
public class TextUtils {

    /**
     * 按最大长度切分文本，不按单词边界，简单粗暴。
     * @param text 原始文本
     * @param chunkSize 每块最大字符数，例如 500
     */
    public static List<String> chunkText(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        for (int start = 0; start < length; start += chunkSize) {
            int end = Math.min(length, start + chunkSize);
            chunks.add(text.substring(start, end));
        }
        return chunks;
    }
}
