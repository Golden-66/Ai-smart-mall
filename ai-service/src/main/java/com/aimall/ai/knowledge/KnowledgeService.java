package com.aimall.ai.knowledge;

import com.aimall.ai.client.ProductClient;
import com.aimall.ai.llm.AiPrompts;
import com.aimall.ai.llm.Llm;
import com.aimall.ai.model.KnowledgeSource;
import com.aimall.common.dto.ProductView;
import com.aimall.common.web.BizException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 商品知识问答 (RAG):
 * 入库: 提取文本 -> 标题感知分块 -> 每块写入 modelNo/docName/chunkIndex 元数据 -> 向量化写入 Milvus;
 * 检索: 向量相似度 + 元数据过滤 (modelNo == 当前商品型号), 避免串到别的商品的说明书;
 * 回答: 仅依据检索到的片段生成, 带片段编号引用, 没有依据时明确说"未找到"。
 */
@Service
public class KnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    public record Answer(String answer, List<KnowledgeSource> sources, boolean found) {
    }

    public record IngestResult(String modelNo, String docName, int chunks) {
    }

    private final VectorStore vectorStore;
    private final DocumentExtractor extractor;
    private final ProductClient products;
    private final Llm llm;
    private final int chunkChars;
    private final int chunkOverlap;
    private final int batchSize;
    private final double similarityThreshold;
    private final int topK;

    public KnowledgeService(VectorStore vectorStore, DocumentExtractor extractor, ProductClient products, Llm llm,
                            @Value("${ai.rag.chunk-chars:600}") int chunkChars,
                            @Value("${ai.rag.chunk-overlap:80}") int chunkOverlap,
                            @Value("${ai.rag.embedding-batch-size:10}") int batchSize,
                            @Value("${ai.rag.similarity-threshold:0.3}") double similarityThreshold,
                            @Value("${ai.rag.top-k:5}") int topK) {
        this.vectorStore = vectorStore;
        this.extractor = extractor;
        this.products = products;
        this.llm = llm;
        this.chunkChars = chunkChars;
        this.chunkOverlap = chunkOverlap;
        this.batchSize = Math.max(1, batchSize);
        this.similarityThreshold = similarityThreshold;
        this.topK = topK;
    }

    /** 校验型号存在并返回规范型号; 不存在返回 null。 */
    public String resolveModelNo(String modelNo) {
        try {
            return products.byModel(modelNo).modelNo();
        } catch (FeignException.NotFound e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 入库
    public IngestResult ingest(String modelNo, String filename, byte[] content) {
        ProductView product;
        try {
            product = products.byModel(modelNo);
        } catch (FeignException.NotFound e) {
            throw new BizException("商品型号不存在: " + modelNo);
        }
        String docName = sanitizeName(filename);
        String text = extractor.extract(filename, content);
        List<TextChunker.Chunk> chunks = TextChunker.split(text, chunkChars, chunkOverlap);
        if (chunks.isEmpty()) {
            throw new BizException("文档中没有可提取的文本 (扫描版 PDF 需要先做 OCR)");
        }

        // 重复上传同一文档: 先删除旧分块, 保证幂等
        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            vectorStore.delete(b.and(b.eq("modelNo", product.modelNo()), b.eq("docName", docName)).build());
        } catch (Exception e) {
            log.debug("delete old chunks skipped: {}", e.getMessage());
        }

        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            TextChunker.Chunk c = chunks.get(i);
            // 在分块文本前拼接 "商品名 + 型号 + 章节", 让向量同时携带商品与章节语义
            String content2 = "【" + product.name() + " " + product.modelNo() + "】"
                    + (c.heading().isBlank() ? "" : " " + c.heading()) + "\n" + c.text();
            Map<String, Object> meta = new HashMap<>();
            meta.put("modelNo", product.modelNo());
            meta.put("docName", docName);
            meta.put("chunkIndex", i);
            String id = UUID.nameUUIDFromBytes((product.modelNo() + "|" + docName + "|" + i).getBytes(StandardCharsets.UTF_8)).toString();
            docs.add(new Document(id, content2, meta));
        }
        // 嵌入模型接口通常限制单次请求条数 (如通义千问 10 条), 分批写入
        for (int i = 0; i < docs.size(); i += batchSize) {
            vectorStore.add(docs.subList(i, Math.min(i + batchSize, docs.size())));
        }
        log.info("知识库入库: model={} doc={} chunks={}", product.modelNo(), docName, docs.size());
        return new IngestResult(product.modelNo(), docName, docs.size());
    }

    // ------------------------------------------------------------------ 检索
    public List<KnowledgeSource> search(String query, String modelNo, int k) {
        SearchRequest.Builder builder = SearchRequest.builder().query(query).topK(k).similarityThreshold(similarityThreshold);
        if (modelNo != null && !modelNo.isBlank()) {
            builder.filterExpression(new FilterExpressionBuilder().eq("modelNo", modelNo).build());
        }
        List<Document> docs = vectorStore.similaritySearch(builder.build());
        List<KnowledgeSource> out = new ArrayList<>();
        if (docs == null) {
            return out;
        }
        for (Document d : docs) {
            Map<String, Object> m = d.getMetadata();
            Object idx = m.get("chunkIndex");
            out.add(new KnowledgeSource(
                    String.valueOf(m.getOrDefault("docName", "")),
                    String.valueOf(m.getOrDefault("modelNo", "")),
                    idx instanceof Number n ? n.intValue() : null,
                    d.getScore(),
                    d.getText()));
        }
        return out;
    }

    // ------------------------------------------------------------------ 问答
    public Answer ask(String question, String modelNo) {
        List<KnowledgeSource> sources = search(question, modelNo, topK);
        if (sources.isEmpty()) {
            return new Answer("知识库中暂未找到相关信息。", List.of(), false);
        }
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            KnowledgeSource s = sources.get(i);
            ctx.append('[').append(i + 1).append("] (").append(s.modelNo()).append(" / ").append(s.docName()).append(")\n")
                    .append(s.text()).append("\n\n");
        }
        String user = "【资料】\n" + ctx + "【问题】\n" + question;
        String answer = llm.text(AiPrompts.RAG_SYSTEM, user);
        return new Answer(answer, sources, true);
    }

    private static String sanitizeName(String filename) {
        String n = filename == null ? "document" : filename.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        return n.isBlank() ? "document" : n;
    }
}
