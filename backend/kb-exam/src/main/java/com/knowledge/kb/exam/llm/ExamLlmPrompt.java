package com.knowledge.kb.exam.llm;

import com.knowledge.kb.entity.KbChunk;

import java.util.List;
import java.util.Map;

public final class ExamLlmPrompt {

    private ExamLlmPrompt() {
    }

    public static String build(String difficulty,
                               Map<String, Integer> typeCounts,
                               List<KbChunk> chunks) {
        StringBuilder ctx = new StringBuilder();
        for (KbChunk chunk : chunks) {
            String content = chunk.getContent();
            if (content != null && content.length() > 1800) {
                content = content.substring(0, 1800) + "...";
            }
            ctx.append("### chunkId=").append(chunk.getId())
                    .append(" docId=").append(chunk.getDocId()).append('\n')
                    .append(content == null ? "" : content).append("\n\n");
        }

        return """
                你是企业培训出题专家。请严格依据【资料片段】出题，禁止编造资料中不存在的事实。
                难度：%s

                题量要求：
                - 单选题(SINGLE)：%d
                - 多选题(MULTI)：%d
                - 判断题(JUDGE)：%d
                - 简答题(SHORT)：%d

                输出要求：
                1. 只输出一个 JSON 对象，不要 Markdown 代码块，不要额外说明。
                2. JSON schema：
                {"questions":[{"type":"SINGLE|MULTI|JUDGE|SHORT","stem":"题干","options":[{"key":"A","text":"..."}],"answer":"A 或 [\\"A\\",\\"C\\"] 或 true/false 或 参考答案文本","analysis":"解析","score":分值数字,"sourceChunkIds":[chunkId数字]}]}
                3. SINGLE/MULTI 必须有 2~6 个 options；JUDGE/SHORT 的 options 可为 []。
                4. 每题 sourceChunkIds 必须来自下方资料的 chunkId，且至少 1 个。
                5. 题目必须能被资料直接支撑。

                【资料片段】
                %s
                """.formatted(
                difficulty == null ? "medium" : difficulty,
                typeCounts.getOrDefault("SINGLE", 0),
                typeCounts.getOrDefault("MULTI", 0),
                typeCounts.getOrDefault("JUDGE", 0),
                typeCounts.getOrDefault("SHORT", 0),
                ctx
        );
    }
}
