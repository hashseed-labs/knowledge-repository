package com.knowledge.kb.exam.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.kb.common.BizException;
import com.knowledge.kb.exam.api.dto.ExamOptionVO;
import com.knowledge.kb.exam.domain.ExamQuestionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JsonExamParser {

    private final ObjectMapper objectMapper;

    public List<ParsedQuestion> parse(String raw) {
        try {
            String json = extractJson(raw);
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.path("questions");
            if (!arr.isArray() || arr.isEmpty()) {
                throw new BizException("模型未返回 questions 数组");
            }
            List<ParsedQuestion> list = new ArrayList<>();
            for (JsonNode node : arr) {
                list.add(parseOne(node));
            }
            return list;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("试卷 JSON 解析失败: " + e.getMessage());
        }
    }

    private ParsedQuestion parseOne(JsonNode node) {
        String type = ExamQuestionType.normalize(node.path("type").asText());
        String stem = node.path("stem").asText("").trim();
        if (stem.isEmpty()) {
            throw new BizException("存在空题干题目");
        }
        List<ExamOptionVO> options = new ArrayList<>();
        JsonNode opts = node.path("options");
        if (opts.isArray()) {
            for (JsonNode o : opts) {
                options.add(new ExamOptionVO(o.path("key").asText(), o.path("text").asText()));
            }
        }
        if ((ExamQuestionType.SINGLE.equals(type) || ExamQuestionType.MULTI.equals(type)) && options.size() < 2) {
            throw new BizException("选择题选项不足: " + stem);
        }

        Object answer;
        JsonNode ans = node.get("answer");
        if (ans == null || ans.isNull()) {
            throw new BizException("题目缺少 answer: " + stem);
        }
        if (ans.isArray()) {
            List<String> keys = new ArrayList<>();
            ans.forEach(n -> keys.add(n.asText()));
            answer = keys;
        } else if (ans.isBoolean()) {
            answer = ans.asBoolean();
        } else if (ans.isNumber()) {
            answer = ans.asText();
        } else {
            answer = ans.asText();
        }

        List<Long> chunkIds = new ArrayList<>();
        JsonNode src = node.path("sourceChunkIds");
        if (src.isArray()) {
            for (JsonNode n : src) {
                if (n.canConvertToLong()) {
                    chunkIds.add(n.asLong());
                }
            }
        }
        if (chunkIds.isEmpty()) {
            throw new BizException("题目缺少 sourceChunkIds: " + stem);
        }

        BigDecimal score = node.has("score") && node.get("score").isNumber()
                ? BigDecimal.valueOf(node.get("score").asDouble())
                : BigDecimal.ONE;
        String analysis = node.path("analysis").asText(null);
        return new ParsedQuestion(type, stem, options, answer, analysis, score, chunkIds);
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("模型返回为空");
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNl = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                text = text.substring(firstNl + 1, lastFence).trim();
                if (text.startsWith("json")) {
                    text = text.substring(4).trim();
                }
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    public record ParsedQuestion(
            String type,
            String stem,
            List<ExamOptionVO> options,
            Object answer,
            String analysis,
            BigDecimal score,
            List<Long> sourceChunkIds
    ) {
    }
}
