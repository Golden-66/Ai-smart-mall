package com.aimall.ai.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 说明书/规格文档切分 (纯 Java, 无外部依赖, 便于单测):
 * 1. 识别 Markdown 标题, 每个分块携带标题路径 ("安装 > 首次配对");
 * 2. 同一章节内按段落装包, 不超过 maxChars;
 * 3. 超长段落按句子切分, 相邻分块保留 overlap 个字符的重叠, 避免答案被切断在边界上;
 * 4. Markdown 表格 (连续以 | 开头的行) 作为整体块处理。
 */
public final class TextChunker {

    public record Chunk(String heading, String text) {
    }

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");

    private TextChunker() {
    }

    public static List<Chunk> split(String raw, int maxChars, int overlap) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        List<Chunk> out = new ArrayList<>();
        List<int[]> levels = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        String heading = "";
        boolean inFence = false;

        for (String line : text.split("\n", -1)) {
            if (line.trim().startsWith("```")) {
                inFence = !inFence;
            }
            Matcher m = inFence ? null : HEADING.matcher(line);
            if (m != null && m.matches()) {
                endBlock(cur, blocks);
                emit(heading, blocks, maxChars, overlap, out);
                blocks.clear();
                int level = m.group(1).length();
                while (!levels.isEmpty() && levels.get(levels.size() - 1)[0] >= level) {
                    levels.remove(levels.size() - 1);
                    titles.remove(titles.size() - 1);
                }
                levels.add(new int[]{level});
                titles.add(m.group(2));
                heading = String.join(" > ", titles);
            } else if (line.isBlank() && !inFence) {
                endBlock(cur, blocks);
            } else {
                if (cur.length() > 0) {
                    cur.append('\n');
                }
                cur.append(line.stripTrailing());
            }
        }
        endBlock(cur, blocks);
        emit(heading, blocks, maxChars, overlap, out);
        return out;
    }

    private static void endBlock(StringBuilder cur, List<String> blocks) {
        String b = cur.toString().strip();
        if (!b.isEmpty()) {
            blocks.add(b);
        }
        cur.setLength(0);
    }

    private static void emit(String heading, List<String> blocks, int maxChars, int overlap, List<Chunk> out) {
        for (String piece : pack(blocks, maxChars, overlap)) {
            out.add(new Chunk(heading, piece));
        }
    }

    static List<String> pack(List<String> blocks, int max, int overlap) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String b : blocks) {
            if (b.length() > max) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                out.addAll(isTable(b) ? splitTable(b, max) : splitSentences(b, max, overlap));
                continue;
            }
            if (cur.length() > 0 && cur.length() + 2 + b.length() > max) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) {
                cur.append("\n\n");
            }
            cur.append(b);
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    static boolean isTable(String block) {
        String[] lines = block.split("\n");
        if (lines.length < 2) {
            return false;
        }
        for (String l : lines) {
            if (!l.strip().startsWith("|")) {
                return false;
            }
        }
        return true;
    }

    /** 超长表格按行拆分, 每片都重复表头。 */
    static List<String> splitTable(String block, int max) {
        String[] lines = block.split("\n");
        List<String> header = new ArrayList<>();
        int start = 0;
        if (lines.length >= 2 && lines[1].matches("^\\s*\\|?\\s*:?-{2,}.*")) {
            header.add(lines[0]);
            header.add(lines[1]);
            start = 2;
        }
        String head = String.join("\n", header);
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder(head);
        boolean hasRow = false;
        for (int i = start; i < lines.length; i++) {
            if (hasRow && cur.length() + 1 + lines[i].length() > max) {
                out.add(cur.toString());
                cur = new StringBuilder(head);
                hasRow = false;
            }
            if (cur.length() > 0) {
                cur.append('\n');
            }
            cur.append(lines[i]);
            hasRow = true;
        }
        if (hasRow) {
            out.add(cur.toString());
        }
        return out.isEmpty() ? List.of(block) : out;
    }

    static List<String> splitSentences(String block, int max, int overlap) {
        int ov = Math.max(0, Math.min(overlap, max / 2));
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String sentence : block.split("(?<=[。！？!?；;\\n])")) {
            String piece = sentence;
            if (piece.isEmpty()) {
                continue;
            }
            while (piece.length() > max) { // 单句超长, 硬切
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                out.add(piece.substring(0, max));
                piece = piece.substring(max - ov);
            }
            if (cur.length() > 0 && cur.length() + piece.length() > max) {
                String prev = cur.toString();
                out.add(prev);
                cur.setLength(0);
                if (ov > 0) {
                    cur.append(prev, Math.max(0, prev.length() - ov), prev.length());
                }
            }
            cur.append(piece);
        }
        if (cur.toString().strip().length() > 0) {
            out.add(cur.toString());
        }
        List<String> cleaned = new ArrayList<>();
        for (String o : out) {
            if (!o.isBlank()) {
                cleaned.add(o.strip());
            }
        }
        return cleaned;
    }
}
