package com.aimall.ai.shopping;

import com.aimall.ai.client.FeignErrors;
import com.aimall.ai.client.ProductClient;
import com.aimall.ai.client.TradeClient;
import com.aimall.ai.knowledge.KnowledgeService;
import com.aimall.ai.llm.AiPrompts;
import com.aimall.ai.llm.Llm;
import com.aimall.ai.model.ChatAnalysis;
import com.aimall.ai.model.ChatReply;
import com.aimall.ai.model.Intent;
import com.aimall.ai.model.KnowledgeSource;
import com.aimall.ai.model.PendingOrder;
import com.aimall.ai.model.RecommendedItem;
import com.aimall.ai.model.RequirementSlots;
import com.aimall.ai.model.ShoppingSession;
import com.aimall.common.dto.CreateOrderReq;
import com.aimall.common.dto.OrderItemReq;
import com.aimall.common.dto.OrderView;
import com.aimall.common.dto.Result;
import com.aimall.common.dto.SkuView;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * AI 导购编排 (后端状态机 + 大模型各司其职):
 *
 *   COLLECTING --(槽位齐全)--> 推荐 --> RECOMMENDED --(选择款式/数量)--> CONFIRMING --(用户确认)--> 创建订单
 *
 * - 大模型负责: 理解用户一句话 (意图 + 本轮槽位)、用工具检索并写推荐理由、闲聊;
 * - 后端负责: 槽位合并与缺失追问、推荐结果校验、价格库存实时回查、订单草稿、确认下单;
 * - 下单必须经过用户明确确认, 且价格始终以商品服务实时价格为准。
 */
@Service
public class ShoppingAssistant {
    private static final Logger log = LoggerFactory.getLogger(ShoppingAssistant.class);

    static final int MAX_AI_QUANTITY = 5;
    private static final Pattern CONFIRM = Pattern.compile(
            "^\\s*(确认|确定|下单|是的?|好的?|可以|没问题|ok|yes|y)\\s*[!！。.]*\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CANCEL = Pattern.compile(
            "^\\s*(取消|不要了|不买了|算了|不用了|no|n)\\s*[!！。.]*\\s*$", Pattern.CASE_INSENSITIVE);

    private final Llm llm;
    private final SessionStore sessions;
    private final RecommendationService recommendationService;
    private final KnowledgeService knowledgeService;
    private final ProductClient products;
    private final TradeClient trade;
    private final ObjectMapper mapper;

    public ShoppingAssistant(Llm llm, SessionStore sessions, RecommendationService recommendationService,
                             KnowledgeService knowledgeService, ProductClient products, TradeClient trade,
                             ObjectMapper mapper) {
        this.llm = llm;
        this.sessions = sessions;
        this.recommendationService = recommendationService;
        this.knowledgeService = knowledgeService;
        this.products = products;
        this.trade = trade;
        this.mapper = mapper;
    }

    // ================================================================== 入口
    public ChatReply chat(long userId, String conversationId, String message) {
        String cid = conversationId == null || conversationId.isBlank() ? UUID.randomUUID().toString() : conversationId;
        ShoppingSession s = sessions.load(userId, cid);
        ChatReply reply;
        try {
            reply = route(s, userId, message);
        } catch (FeignException e) {
            log.warn("下游服务调用失败: {}", e.getMessage());
            reply = reply(s, "抱歉，" + FeignErrors.message(e));
        } catch (Exception e) {
            log.error("导购处理失败", e);
            reply = reply(s, "抱歉，处理时出了点问题，请稍后再试。");
        }
        s.addTurn("user", message);
        s.addTurn("assistant", reply.reply());
        sessions.save(s);
        return reply;
    }

    /** 页面上的"确认下单"按钮。 */
    public ChatReply confirm(long userId, String conversationId) {
        ShoppingSession s = sessions.load(userId, conversationId);
        ChatReply reply;
        try {
            reply = s.pendingOrder == null ? reply(s, "当前没有待确认的订单。") : confirmOrder(s, userId);
        } catch (FeignException e) {
            reply = reply(s, "抱歉，" + FeignErrors.message(e));
        }
        sessions.save(s);
        return reply;
    }

    public ChatReply cancel(long userId, String conversationId) {
        ShoppingSession s = sessions.load(userId, conversationId);
        ChatReply reply = cancelPending(s);
        sessions.save(s);
        return reply;
    }

    private ChatReply route(ShoppingSession s, long userId, String message) {
        // 订单草稿待确认时, "确认/取消"由后端用规则判定, 不交给大模型, 避免误触下单
        if (s.stage == ShoppingSession.Stage.CONFIRMING && s.pendingOrder != null) {
            if (CONFIRM.matcher(message).matches()) {
                return confirmOrder(s, userId);
            }
            if (CANCEL.matcher(message).matches()) {
                return cancelPending(s);
            }
        }

        ChatAnalysis analysis;
        try {
            analysis = analyze(s, message);
        } catch (Exception e) {
            log.warn("意图解析失败: {}", e.getMessage());
            return reply(s, "抱歉，我没有理解您的意思。您可以这样说：“预算 5000 左右，想买一台办公用的笔记本”。");
        }
        Intent intent = analysis.intent() == null ? Intent.CHITCHAT : analysis.intent();
        return switch (intent) {
            case SHOPPING -> shopping(s, analysis);
            case SELECT -> select(s, analysis);
            case PRODUCT_QA -> qa(s, analysis, message);
            case CHITCHAT -> chitchat(s, message);
        };
    }

    // ================================================================== 理解
    private ChatAnalysis analyze(ShoppingSession s, String message) throws Exception {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("stage", s.stage.name());
        ctx.put("currentRequirement", s.requirement);
        ctx.put("recommended", s.recommended.stream().map(r -> Map.of(
                "index", r.index(), "name", r.productName() + " " + r.skuName(), "modelNo", r.modelNo())).toList());
        ctx.put("hasPendingOrder", s.pendingOrder != null);

        StringBuilder user = new StringBuilder("【当前状态】\n").append(mapper.writeValueAsString(ctx)).append("\n\n【最近对话】\n");
        if (s.history.isEmpty()) {
            user.append("（无）\n");
        }
        for (ShoppingSession.Turn t : s.history) {
            user.append("user".equals(t.role()) ? "用户：" : "助手：").append(truncate(t.text(), 200)).append('\n');
        }
        user.append("\n【用户最新一句话】\n").append(message);
        return llm.structured(AiPrompts.ANALYZE_SYSTEM, user.toString(), ChatAnalysis.class);
    }

    // ================================================================== 选购 (多轮澄清 + 推荐)
    private ChatReply shopping(ShoppingSession s, ChatAnalysis a) {
        RequirementSlots incoming = a.requirement() == null ? RequirementSlots.empty() : a.requirement().normalized();
        RequirementSlots current = s.requirement;
        if (incoming.category() != null && current.category() != null && !incoming.category().equals(current.category())) {
            current = RequirementSlots.empty(); // 换了品类, 旧条件作废
            s.askedUsage = false;
        }
        s.requirement = current.merge(incoming);
        s.pendingOrder = null;
        s.recommended = new ArrayList<>();

        String clarify = clarifyingQuestion(s);
        if (clarify != null) {
            s.stage = ShoppingSession.Stage.COLLECTING;
            return reply(s, clarify);
        }

        RecommendationService.Recommendation rec = recommendationService.recommend(s.requirement);
        if (rec.items().isEmpty()) {
            s.stage = ShoppingSession.Stage.COLLECTING;
            return reply(s, "按“" + s.requirement.describe() + "”暂时没有找到合适且有库存的商品。"
                    + "您可以放宽预算、降低配置要求，或换一个用途试试。");
        }
        s.recommended = new ArrayList<>(rec.items());
        s.stage = ShoppingSession.Stage.RECOMMENDED;
        return reply(s, formatRecommendation(rec));
    }

    /** 缺失条件的追问: 品类和预算必填; 用途只追问一次, 用户说"都行"或忽略后也会继续推荐。 */
    String clarifyingQuestion(ShoppingSession s) {
        RequirementSlots r = s.requirement;
        List<String> asks = new ArrayList<>();
        if (!r.hasCategory()) {
            asks.add("您想选购哪类商品？我可以帮您挑选：" + String.join("、", RequirementSlots.CATEGORIES) + "。");
        }
        if (!r.hasBudget()) {
            asks.add("您的预算大概是多少元？（例如：5000 元以内）");
        }
        if (!r.hasUsage() && !s.askedUsage) {
            asks.add("主要用在哪些场景？比如办公、游戏、拍照、学习、通勤……（没有特别要求可以回复“都行”）");
        }
        if (asks.isEmpty()) {
            return null;
        }
        s.askedUsage = true;
        StringBuilder sb = new StringBuilder();
        if (r.hasCategory() || r.hasBudget() || r.hasUsage()) {
            sb.append("好的，目前了解到：").append(r.describe()).append("。\n");
        }
        sb.append("为了给您更精准的推荐，还想确认：\n");
        for (int i = 0; i < asks.size(); i++) {
            sb.append(i + 1).append(". ").append(asks.get(i)).append('\n');
        }
        return sb.toString().trim();
    }

    private String formatRecommendation(RecommendationService.Recommendation rec) {
        StringBuilder sb = new StringBuilder();
        sb.append(rec.summary() == null ? "为您推荐以下商品：" : rec.summary()).append("\n\n");
        for (RecommendedItem it : rec.items()) {
            sb.append("【").append(it.index()).append("】").append(it.productName()).append(" ").append(it.skuName())
                    .append("  ").append(money(it.price())).append("（库存 ").append(it.stock()).append("）\n");
            if (!it.reason().isBlank()) {
                sb.append("    ").append(it.reason()).append('\n');
            }
        }
        sb.append("\n告诉我您想买第几款、买几件即可；也可以继续调整预算/用途，或询问某一款的详细参数。");
        return sb.toString();
    }

    // ================================================================== 选择 -> 订单草稿
    private ChatReply select(ShoppingSession s, ChatAnalysis a) {
        if (s.recommended.isEmpty()) {
            return reply(s, "目前还没有可选的推荐商品。请先告诉我您想买什么、预算和用途，我来帮您挑选。");
        }
        RecommendedItem item = null;
        if (a.selectedIndex() != null) {
            item = s.recommended.stream().filter(r -> r.index() == a.selectedIndex()).findFirst().orElse(null);
        } else if (s.recommended.size() == 1) {
            item = s.recommended.get(0);
        }
        if (item == null) {
            return reply(s, "请告诉我您选择第几款（1-" + s.recommended.size() + "）？");
        }
        int qty = a.quantity() == null ? 1 : a.quantity();
        if (qty < 1 || qty > MAX_AI_QUANTITY) {
            return reply(s, "通过 AI 导购单次最多下单 " + MAX_AI_QUANTITY + " 件，请调整数量；更多数量可以到购物车下单。");
        }

        // 价格与库存以商品服务的实时数据为准
        SkuView live = products.skus(List.of(item.skuId())).stream().findFirst().orElse(null);
        if (live == null || live.stock() == null || live.stock() < qty) {
            return reply(s, "抱歉，" + item.productName() + " " + item.skuName() + " 当前库存不足 " + qty + " 件，请换一款或减少数量。");
        }
        BigDecimal total = live.price().multiply(BigDecimal.valueOf(qty));
        s.pendingOrder = new PendingOrder(UUID.randomUUID().toString().substring(0, 8), live.skuId(),
                live.productName(), live.skuName(), qty, live.price(), total);
        s.stage = ShoppingSession.Stage.CONFIRMING;
        return reply(s, draftText(s.pendingOrder));
    }

    private String draftText(PendingOrder p) {
        return "请确认订单：\n" + p.productName() + " " + p.skuName() + " × " + p.quantity()
                + "\n单价 " + money(p.unitPrice()) + "，合计 " + money(p.total())
                + "\n回复“确认”（或点击“确认下单”）即可提交订单，回复“取消”放弃。";
    }

    // ================================================================== 确认下单
    private ChatReply confirmOrder(ShoppingSession s, long userId) {
        PendingOrder p = s.pendingOrder;
        SkuView live = products.skus(List.of(p.skuId())).stream().findFirst().orElse(null);
        if (live == null || live.stock() == null || live.stock() < p.quantity()) {
            s.pendingOrder = null;
            s.stage = ShoppingSession.Stage.RECOMMENDED;
            return reply(s, "抱歉，该商品库存已不足，订单未提交。请重新选择。");
        }
        if (live.price().compareTo(p.unitPrice()) != 0) { // 价格在确认期间变动: 重新确认, 绝不按旧价下单
            BigDecimal total = live.price().multiply(BigDecimal.valueOf(p.quantity()));
            s.pendingOrder = new PendingOrder(p.token(), p.skuId(), live.productName(), live.skuName(),
                    p.quantity(), live.price(), total);
            return reply(s, "商品价格刚刚有调整，请重新确认：\n" + draftText(s.pendingOrder));
        }

        Result<OrderView> result = trade.createOrder(userId, "ai-" + s.conversationId + "-" + p.token(),
                new CreateOrderReq(List.of(new OrderItemReq(p.skuId(), p.quantity())), "AI 导购下单"));
        OrderView order = result.data();
        s.resetAfterOrder();
        ChatReply base = reply(s, "订单已提交 ✅\n订单号：" + order.orderNo() + "\n应付金额：" + money(order.totalAmount())
                + "\n系统正在锁定库存，几秒后即可在“我的订单”中支付；超时未支付会自动取消。");
        return new ChatReply(base.conversationId(), base.reply(), base.stage(), List.of(), null, order.orderNo(), List.of());
    }

    private ChatReply cancelPending(ShoppingSession s) {
        if (s.pendingOrder == null) {
            return reply(s, "当前没有待确认的订单。");
        }
        s.pendingOrder = null;
        s.stage = s.recommended.isEmpty() ? ShoppingSession.Stage.COLLECTING : ShoppingSession.Stage.RECOMMENDED;
        return reply(s, "好的，已取消本次下单。需要的话可以继续选择其他款式，或调整需求。");
    }

    // ================================================================== 商品知识问答
    private ChatReply qa(ShoppingSession s, ChatAnalysis a, String message) {
        String modelNo = null;
        if (a.modelNo() != null && !a.modelNo().isBlank()) {
            modelNo = knowledgeService.resolveModelNo(a.modelNo().trim());
            if (modelNo == null) {
                return reply(s, "没有找到型号为 " + a.modelNo().trim() + " 的商品，请核对型号后再问。");
            }
        } else if (a.selectedIndex() != null) {
            modelNo = s.recommended.stream().filter(r -> r.index() == a.selectedIndex())
                    .map(RecommendedItem::modelNo).findFirst().orElse(null);
        } else if (s.recommended.size() == 1) {
            modelNo = s.recommended.get(0).modelNo();
        }
        if (modelNo == null && !s.recommended.isEmpty()) {
            return reply(s, "您想了解第几款（1-" + s.recommended.size() + "），或直接告诉我商品型号？");
        }

        String question = a.question() == null || a.question().isBlank() ? message : a.question();
        KnowledgeService.Answer ans = knowledgeService.ask(question, modelNo);
        ChatReply base = reply(s, ans.answer());
        return new ChatReply(base.conversationId(), base.reply(), base.stage(), base.recommendations(),
                base.pendingOrder(), null, ans.sources());
    }

    private ChatReply chitchat(ShoppingSession s, String message) {
        try {
            return reply(s, llm.text(AiPrompts.CHITCHAT_SYSTEM, message));
        } catch (Exception e) {
            return reply(s, "您好！我是 AI 导购助手，告诉我想买什么、预算和用途，我来帮您挑选。");
        }
    }

    // ================================================================== helpers
    private ChatReply reply(ShoppingSession s, String text) {
        return new ChatReply(s.conversationId, text, s.stage.name(), List.copyOf(s.recommended),
                s.pendingOrder, null, List.<KnowledgeSource>of());
    }

    private static String money(BigDecimal v) {
        return "¥" + v.stripTrailingZeros().toPlainString();
    }

    private static String truncate(String t, int max) {
        return t == null ? "" : t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
