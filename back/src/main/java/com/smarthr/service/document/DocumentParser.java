/**
 * 文档解析服务 - 解析 PDF/Word/TXT 简历文件
 *
 * @author QinFeng Luo
 * @date 2026/01/12
 */
package com.smarthr.service.document;

import com.smarthr.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Slf4j
@Service
public class DocumentParser {

    /**
     * 解析上传的文档
     */
    public String parse(MultipartFile file) {
        String filename = Objects.requireNonNull(file.getOriginalFilename()).toLowerCase();
        //方法内部校验（兜底）
        try {
            if (filename.endsWith(".pdf")) {
                return parsePdf(file.getInputStream());
            } else if (filename.endsWith(".docx")) {
                return parseDocx(file.getInputStream());
            } else if (filename.endsWith(".doc")) {
                throw new BusinessException("暂不支持 .doc 格式，请转换为 .docx");
            } else if (filename.endsWith(".txt")) {
                return parseTxt(file.getInputStream());
            } else {
                throw new BusinessException("不支持的文件格式: " + filename);
            }
        } catch (IOException e) {
            log.error("文档解析失败: {}", filename, e);
            throw new BusinessException("文档解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析 PDF 文件
     */
    private String parsePdf(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return cleanText(text);
        }
    }

    /**
     * 解析 Word 文档 (.docx)
     */
    private String parseDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder content = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    content.append(text).append("\n");
                }
            }
            return cleanText(content.toString());
        }
    }

    /**
     * 解析纯文本文件
     */
    private String parseTxt(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        return cleanText(new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * 清理文本内容
     */
    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        // Drop embedded null bytes that Postgres rejects
        String sanitized = text.replace("\u0000", "");
        // 移除多余空白字符
        return sanitized.replaceAll("\\s+", " ")
                .replaceAll("\\n+", "\n")
                .trim();
    }

    /**
     * 检查文件类型是否支持（该方法为文档类型前置校验）
     */
    public boolean isSupported(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".pdf") || 
               lower.endsWith(".docx") || 
               lower.endsWith(".txt");
    }
}
