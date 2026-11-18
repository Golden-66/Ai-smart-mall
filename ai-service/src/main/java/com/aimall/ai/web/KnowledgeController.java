package com.aimall.ai.web;

import com.aimall.ai.knowledge.KnowledgeService;
import com.aimall.ai.model.KnowledgeSource;
import com.aimall.common.dto.Result;
import com.aimall.common.web.BizException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/ai/knowledge")
public class KnowledgeController {

    public record AskReq(String question, String modelNo) {
    }

    private final KnowledgeService knowledge;

    public KnowledgeController(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    /** 上传商品说明书 / 规格文档 (仅管理员 admin)。 */
    @PostMapping("/documents")
    public Result<KnowledgeService.IngestResult> upload(@RequestHeader("X-User-Name") String username,
                                                        @RequestParam("modelNo") String modelNo,
                                                        @RequestParam("file") MultipartFile file) throws IOException {
        if (!"admin".equals(username)) {
            throw new BizException(403, "仅管理员可以上传知识库文档");
        }
        if (file.isEmpty()) {
            throw new BizException("文件为空");
        }
        return Result.ok(knowledge.ingest(modelNo.trim(), file.getOriginalFilename(), file.getBytes()));
    }

    /** 仅检索不生成, 用于调试与检索评测 (Hit@K)。 */
    @GetMapping("/search")
    public Result<List<KnowledgeSource>> search(@RequestParam("q") String q,
                                                @RequestParam(value = "modelNo", required = false) String modelNo,
                                                @RequestParam(value = "topK", defaultValue = "5") int topK) {
        return Result.ok(knowledge.search(q, modelNo, Math.max(1, Math.min(topK, 20))));
    }

    @PostMapping("/ask")
    public Result<KnowledgeService.Answer> ask(@RequestBody AskReq req) {
        if (req.question() == null || req.question().isBlank()) {
            throw new BizException("问题不能为空");
        }
        return Result.ok(knowledge.ask(req.question().trim(), req.modelNo()));
    }
}
