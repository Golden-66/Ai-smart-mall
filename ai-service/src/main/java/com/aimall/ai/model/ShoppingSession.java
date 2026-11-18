package com.aimall.ai.model;

import java.util.ArrayList;
import java.util.List;

/** 一次导购会话的状态, 存放在 Redis (key 含 userId, 不同用户互相隔离)。 */
public class ShoppingSession {

    public enum Stage {
        /** 收集需求中 */
        COLLECTING,
        /** 已给出推荐, 等待用户选择 */
        RECOMMENDED,
        /** 已生成订单草稿, 等待用户确认 */
        CONFIRMING
    }

    public record Turn(String role, String text) {
    }

    public String conversationId;
    public long userId;
    public Stage stage = Stage.COLLECTING;
    public RequirementSlots requirement = RequirementSlots.empty();
    public boolean askedUsage = false;
    public List<RecommendedItem> recommended = new ArrayList<>();
    public PendingOrder pendingOrder;
    public List<Turn> history = new ArrayList<>();

    public ShoppingSession() {
    }

    public ShoppingSession(String conversationId, long userId) {
        this.conversationId = conversationId;
        this.userId = userId;
    }

    public void addTurn(String role, String text) {
        history.add(new Turn(role, text == null ? "" : text));
        while (history.size() > 8) {
            history.remove(0);
        }
    }

    public void resetAfterOrder() {
        stage = Stage.COLLECTING;
        requirement = RequirementSlots.empty();
        askedUsage = false;
        recommended = new ArrayList<>();
        pendingOrder = null;
    }
}
