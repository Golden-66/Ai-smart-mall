package com.aimall.ai;

import com.aimall.ai.model.RequirementSlots;
import com.aimall.common.dto.SkuView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementSlotsTest {

    private static RequirementSlots slots(String category, Integer max, List<String> usages, Integer ram) {
        return new RequirementSlots(category, null, max == null ? null : BigDecimal.valueOf(max), usages, ram, null, List.of());
    }

    private static SkuView sku(String category, int price, int stock, Integer ram, Integer storage) {
        return new SkuView(1L, 10L, "星河 Book Air 13", "星河", category, "LT-1001", "银色 / 16G+512G",
                ram, storage, BigDecimal.valueOf(price), stock, "办公,轻薄");
    }

    @Test
    void categoryIsNormalized() {
        assertEquals("笔记本", RequirementSlots.normalizeCategory("笔记本电脑"));
        assertEquals("笔记本", RequirementSlots.normalizeCategory("电脑"));
        assertEquals("智能手表", RequirementSlots.normalizeCategory("手环"));
        assertEquals("耳机", RequirementSlots.normalizeCategory("蓝牙耳机"));
        assertNull(RequirementSlots.normalizeCategory("冰箱"));
        assertNull(RequirementSlots.normalizeCategory("  "));
    }

    @Test
    void mergeKeepsOldSlotsAndOverridesWithNewOnes() {
        RequirementSlots turn1 = slots("笔记本", null, List.of(), null);
        RequirementSlots turn2 = slots(null, 5000, List.of("办公"), 16);
        RequirementSlots merged = turn1.merge(turn2);
        assertEquals("笔记本", merged.category());
        assertEquals(0, merged.budgetMax().compareTo(BigDecimal.valueOf(5000)));
        assertEquals(List.of("办公"), merged.usages());
        assertEquals(16, merged.minRamGb());

        RequirementSlots turn3 = merged.merge(slots(null, 4000, List.of("编程"), null));
        assertEquals(0, turn3.budgetMax().compareTo(BigDecimal.valueOf(4000)));
        assertEquals(List.of("办公", "编程"), turn3.usages()); // 用途取并集
    }

    @Test
    void normalizedCleansBadModelOutput() {
        RequirementSlots dirty = new RequirementSlots("笔记本电脑", BigDecimal.valueOf(6000), BigDecimal.valueOf(3000),
                java.util.Arrays.asList("办公", " ", null, "办公"), -1, 0, null);
        RequirementSlots clean = dirty.normalized();
        assertEquals("笔记本", clean.category());
        assertEquals(0, clean.budgetMin().compareTo(BigDecimal.valueOf(3000))); // 上下限颠倒会被纠正
        assertEquals(0, clean.budgetMax().compareTo(BigDecimal.valueOf(6000)));
        assertEquals(List.of("办公"), clean.usages());
        assertNull(clean.minRamGb());
        assertNull(clean.minStorageGb());
        assertTrue(clean.brands().isEmpty());
    }

    @Test
    void generalUsageIsNotUsedAsSearchTag() {
        RequirementSlots r = slots("手机", 3000, List.of("通用"), null);
        assertTrue(r.hasUsage());
        assertTrue(r.usageTags().isEmpty());
    }

    @Test
    void acceptsEnforcesHardConstraints() {
        RequirementSlots r = slots("笔记本", 5000, List.of("办公"), 16);
        assertTrue(r.accepts(sku("笔记本", 4999, 10, 16, 512)));
        assertFalse(r.accepts(sku("笔记本", 5001, 10, 16, 512)), "超预算");
        assertFalse(r.accepts(sku("笔记本", 4000, 0, 16, 512)), "无库存");
        assertFalse(r.accepts(sku("手机", 4000, 10, 16, 512)), "品类不符");
        assertFalse(r.accepts(sku("笔记本", 4000, 10, 8, 512)), "内存不足");
        assertFalse(r.accepts(sku("笔记本", 4000, 10, null, 512)), "缺少内存信息视为不满足");
        assertFalse(r.accepts(null));
    }

    @Test
    void describeIsReadable() {
        String text = slots("笔记本", 5000, List.of("办公", "编程"), 16).describe();
        assertTrue(text.contains("笔记本"));
        assertTrue(text.contains("≤ 5000"));
        assertTrue(text.contains("办公、编程"));
        assertTrue(text.contains("16GB"));
        assertEquals("（暂无）", RequirementSlots.empty().describe());
    }
}
