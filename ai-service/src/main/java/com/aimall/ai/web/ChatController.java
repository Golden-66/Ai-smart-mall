package com.aimall.ai.web;

import com.aimall.ai.model.ChatReply;
import com.aimall.ai.shopping.ShoppingAssistant;
import com.aimall.common.dto.Result;
import com.aimall.common.web.BizException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class ChatController {

    public record ChatReq(String message, String conversationId) {
    }

    public record ConversationReq(String conversationId) {
    }

    private final ShoppingAssistant assistant;

    public ChatController(ShoppingAssistant assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/chat")
    public Result<ChatReply> chat(@RequestHeader("X-User-Id") long userId, @RequestBody ChatReq req) {
        if (req.message() == null || req.message().isBlank()) {
            throw new BizException("消息不能为空");
        }
        if (req.message().length() > 500) {
            throw new BizException("消息过长 (最多 500 字)");
        }
        return Result.ok(assistant.chat(userId, req.conversationId(), req.message().trim()));
    }

    /** "确认下单"按钮: 与回复"确认"等价, 但不经过大模型。 */
    @PostMapping("/orders/confirm")
    public Result<ChatReply> confirm(@RequestHeader("X-User-Id") long userId, @RequestBody ConversationReq req) {
        requireConversation(req);
        return Result.ok(assistant.confirm(userId, req.conversationId()));
    }

    @PostMapping("/orders/cancel")
    public Result<ChatReply> cancel(@RequestHeader("X-User-Id") long userId, @RequestBody ConversationReq req) {
        requireConversation(req);
        return Result.ok(assistant.cancel(userId, req.conversationId()));
    }

    private static void requireConversation(ConversationReq req) {
        if (req.conversationId() == null || req.conversationId().isBlank()) {
            throw new BizException("conversationId 不能为空");
        }
    }
}
