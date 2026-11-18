package com.aimall.ai.model;

import com.aimall.common.dto.SkuView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 选购需求槽位 (预算 / 品类 / 用途 / 配置偏好)。
 * 大模型每轮只抽取"本轮新增或修改"的槽位, 由后端合并到会话状态里, 缺失的必填槽位由后端决定是否追问。
 */
public record RequirementSlots(
        String category,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        List<String> usages,
        Integer minRamGb,
        Integer minStorageGb,
        List<String> brands) {

    public static final List<String> CATEGORIES = List.of("手机", "笔记本", "耳机", "平板", "智能手表");

    private static final Map<String, String> ALIASES = Map.of(
            "智能手机", "手机", "笔记本电脑", "笔记本", "电脑", "笔记本", "蓝牙耳机", "耳机",
            "平板电脑", "平板", "手表", "智能手表", "手环", "智能手表");

    public static RequirementSlots empty() {
        return new RequirementSlots(null, null, null, List.of(), null, null, List.of());
    }

    public static String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String c = raw.trim();
        if (CATEGORIES.contains(c)) {
            return c;
        }
        if (ALIASES.containsKey(c)) {
            return ALIASES.get(c);
        }
        for (String cat : CATEGORIES) {
            if (c.contains(cat)) {
                return cat;
            }
        }
        for (Map.Entry<String, String> e : ALIASES.entrySet()) {
            if (c.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 清洗大模型输出: 品类归一化、去空/去重、非法数值置空。 */
    public RequirementSlots normalized() {
        BigDecimal min = budgetMin != null && budgetMin.signum() > 0 ? budgetMin : null;
        BigDecimal max = budgetMax != null && budgetMax.signum() > 0 ? budgetMax : null;
        if (min != null && max != null && min.compareTo(max) > 0) {
            BigDecimal t = min;
            min = max;
            max = t;
        }
        return new RequirementSlots(
                normalizeCategory(category), min, max, clean(usages),
                minRamGb != null && minRamGb > 0 ? minRamGb : null,
                minStorageGb != null && minStorageGb > 0 ? minStorageGb : null,
                clean(brands));
    }

    private static List<String> clean(List<String> in) {
        if (in == null) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String s : in) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return new ArrayList<>(out);
    }

    /** 把本轮抽取到的槽位合并进当前槽位: 标量有新值则覆盖, 用途取并集, 品牌有新值则替换。 */
    public RequirementSlots merge(RequirementSlots incoming) {
        if (incoming == null) {
            return this;
        }
        RequirementSlots n = incoming.normalized();
        Set<String> mergedUsages = new LinkedHashSet<>(clean(usages));
        mergedUsages.addAll(n.usages());
        return new RequirementSlots(
                n.category() != null ? n.category() : category,
                n.budgetMin() != null ? n.budgetMin() : budgetMin,
                n.budgetMax() != null ? n.budgetMax() : budgetMax,
                new ArrayList<>(mergedUsages),
                n.minRamGb() != null ? n.minRamGb() : minRamGb,
                n.minStorageGb() != null ? n.minStorageGb() : minStorageGb,
                n.brands().isEmpty() ? clean(brands) : n.brands());
    }

    /** 后端硬性约束: 有库存、品类一致、不超预算、满足最低配置。大模型推荐的每个 SKU 都要通过它才会被采纳。 */
    public boolean accepts(SkuView s) {
        if (s == null || s.stock() == null || s.stock() <= 0 || s.price() == null) {
            return false;
        }
        if (category != null && !category.equals(s.category())) {
            return false;
        }
        if (budgetMax != null && s.price().compareTo(budgetMax) > 0) {
            return false;
        }
        if (budgetMin != null && s.price().compareTo(budgetMin) < 0) {
            return false;
        }
        if (minRamGb != null && (s.ramGb() == null || s.ramGb() < minRamGb)) {
            return false;
        }
        return minStorageGb == null || (s.storageGb() != null && s.storageGb() >= minStorageGb);
    }

    public boolean hasCategory() {
        return category != null;
    }

    public boolean hasBudget() {
        return budgetMax != null;
    }

    public boolean hasUsage() {
        return usages != null && !usages.isEmpty();
    }

    /** 用于检索的用途标签 (去掉"通用")。 */
    public List<String> usageTags() {
        return clean(usages).stream().filter(u -> !"通用".equals(u)).toList();
    }

    /** 给用户和大模型看的一句话描述。 */
    public String describe() {
        List<String> parts = new ArrayList<>();
        if (category != null) {
            parts.add("品类：" + category);
        }
        if (budgetMax != null) {
            String min = budgetMin != null ? budgetMin.stripTrailingZeros().toPlainString() + " - " : "≤ ";
            parts.add("预算：" + min + budgetMax.stripTrailingZeros().toPlainString() + " 元");
        }
        if (hasUsage()) {
            parts.add("用途：" + String.join("、", usages));
        }
        if (minRamGb != null) {
            parts.add("内存 ≥ " + minRamGb + "GB");
        }
        if (minStorageGb != null) {
            parts.add("存储 ≥ " + minStorageGb + "GB");
        }
        if (brands != null && !brands.isEmpty()) {
            parts.add("偏好品牌：" + String.join("、", brands));
        }
        return parts.isEmpty() ? "（暂无）" : String.join("；", parts);
    }
}
