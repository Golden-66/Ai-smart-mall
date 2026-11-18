package com.aimall.ai.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * Spring AI ChatClient 的薄封装。
 * 使用 messages(...) 而不是 system(String)/user(String): 后者会把文本当作模板解析 {占位符},
 * 而我们的提示词和上下文里含有 JSON 花括号。
 */
@Component
public class Llm {

    private final ChatClient client;

    public Llm(ChatClient.Builder builder) {
        this.client = builder.build();
    }

    /** 结构化输出 (+ 可选工具调用 Function Calling)。 */
    public <T> T structured(String system, String user, Class<T> type, Object... tools) {
        ChatClient.ChatClientRequestSpec spec =
                client.prompt().messages(new SystemMessage(system), new UserMessage(user));
        if (tools != null && tools.length > 0) {
            spec = spec.tools(tools);
        }
        return spec.call().entity(type);
    }

    public String text(String system, String user) {
        return client.prompt().messages(new SystemMessage(system), new UserMessage(user)).call().content();
    }
}
