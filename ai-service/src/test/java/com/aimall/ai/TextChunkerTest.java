package com.aimall.ai;

import com.aimall.ai.knowledge.TextChunker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextChunkerTest {

    @Test
    void headingPathIsKeptAndNested() {
        String md = "# 手册\n\n## 安装\n\n### 首次配对\n\n长按按键 3 秒。\n\n## 故障排除\n\n重启设备。";
        List<TextChunker.Chunk> chunks = TextChunker.split(md, 200, 20);
        assertEquals(2, chunks.size());
        assertEquals("手册 > 安装 > 首次配对", chunks.get(0).heading());
        assertEquals("手册 > 故障排除", chunks.get(1).heading());
        assertTrue(chunks.get(0).text().contains("长按按键"));
    }

    @Test
    void headingInsideCodeFenceIsIgnored() {
        String md = "# A\n\n```\n# not a heading\n```\n\n正文";
        List<TextChunker.Chunk> chunks = TextChunker.split(md, 200, 20);
        assertEquals(1, chunks.size());
        assertEquals("A", chunks.get(0).heading());
    }

    @Test
    void longParagraphIsSplitWithinLimitAndOverlaps() {
        String sentence = "这是一个用于测试分块的句子。";
        String md = "# 标题\n\n" + sentence.repeat(60);
        List<TextChunker.Chunk> chunks = TextChunker.split(md, 120, 30);
        assertTrue(chunks.size() > 3);
        for (TextChunker.Chunk c : chunks) {
            assertTrue(c.text().length() <= 120 + 30, "chunk too long: " + c.text().length());
        }
        // 相邻分块存在重叠: 后一块以前一块结尾的 30 个字符开头
        String first = chunks.get(0).text();
        String tail = first.substring(first.length() - 30);
        assertTrue(chunks.get(1).text().startsWith(tail), "second chunk should start with the overlap tail");
    }

    @Test
    void smallTableStaysWholeAndLongTableRepeatsHeader() {
        String header = "| 项目 | 数值 |\n| --- | --- |";
        String small = header + "\n| 电池 | 70Wh |";
        assertEquals(1, TextChunker.split("# 规格\n\n" + small, 300, 20).size());

        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            rows.append("\n| 项目").append(i).append(" | ").append(i * 10).append(" |");
        }
        List<TextChunker.Chunk> chunks = TextChunker.split("# 规格\n\n" + header + rows, 200, 20);
        assertTrue(chunks.size() > 1);
        for (TextChunker.Chunk c : chunks) {
            assertTrue(c.text().startsWith("| 项目 | 数值 |"), "each piece should repeat the header");
        }
    }

    @Test
    void blankInputGivesNoChunks() {
        assertTrue(TextChunker.split("   \n\n  ", 200, 20).isEmpty());
        assertTrue(TextChunker.split(null, 200, 20).isEmpty());
    }
}
