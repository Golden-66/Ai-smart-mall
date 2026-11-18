package com.aimall.ai.knowledge;

import com.aimall.common.web.BizException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** 从上传文件中提取纯文本: PDF (PDFBox) / Markdown / TXT。扫描版 PDF 没有文字层, 需先 OCR。 */
@Component
public class DocumentExtractor {

    public String extract(String filename, byte[] content) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".pdf")) {
                try (PDDocument doc = Loader.loadPDF(content)) {
                    return new PDFTextStripper().getText(doc);
                }
            }
            if (name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".txt")) {
                return new String(content, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new BizException("文档解析失败: " + e.getMessage());
        }
        throw new BizException(415, "仅支持 PDF / Markdown / TXT 文档");
    }
}
