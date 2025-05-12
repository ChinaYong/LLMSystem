package com.example.chatbot.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档解析工具：将 txt/pdf/docx 转为纯文本
 */
public class DocumentParser {

    /** 读取 txt 文件内容 */
    public static String parseTxt(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), "UTF-8");
    }

    /** 读取 PDF 文件内容 */
    public static String parsePdf(File file) throws IOException {
        try (PDDocument pdf = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(pdf);
        }
    }

    /** 读取 DOCX 文件内容 */
    public static String parseDocx(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * 根据文件后缀，自动选择解析方法
     * @param file 本地 File 对象
     */
    public static String parseToText(File file) throws IOException {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".txt")) {
            return parseTxt(file);
        } else if (name.endsWith(".pdf")) {
            return parsePdf(file);
        } else if (name.endsWith(".docx")) {
            return parseDocx(file);
        } else {
            throw new UnsupportedOperationException("不支持的文件格式：" + name);
        }
    }
}
