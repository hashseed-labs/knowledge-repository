package com.knowledge.kb.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.kb.exam.domain.ExamQuestionType;
import com.knowledge.kb.exam.infra.ExamQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
@RequiredArgsConstructor
public class ExamGrader {

    private final ObjectMapper objectMapper;

    public GradeResult grade(ExamQuestion question, Object userAnswer) {
        String type = question.getQType();
        if (ExamQuestionType.SHORT.equals(type)) {
            return GradeResult.manual();
        }
        try {
            JsonNode expected = objectMapper.readTree(question.getAnswerJson());
            boolean ok = switch (type) {
                case ExamQuestionType.SINGLE -> equalsIgnoreCaseText(expected, userAnswer);
                case ExamQuestionType.MULTI -> equalsAsSet(expected, userAnswer);
                case ExamQuestionType.JUDGE -> equalsJudge(expected, userAnswer);
                default -> false;
            };
            BigDecimal full = question.getScore() == null ? BigDecimal.ONE : question.getScore();
            return ok ? GradeResult.pass(full) : GradeResult.fail();
        } catch (Exception e) {
            return GradeResult.fail();
        }
    }

    private boolean equalsIgnoreCaseText(JsonNode expected, Object userAnswer) {
        String exp = normalizeKey(expected.isTextual() ? expected.asText() : expected.toString());
        String act = normalizeKey(stringify(userAnswer));
        return !exp.isEmpty() && exp.equals(act);
    }

    private boolean equalsAsSet(JsonNode expected, Object userAnswer) {
        Set<String> exp = toKeySet(expected);
        Set<String> act = toKeySet(userAnswer);
        return !exp.isEmpty() && exp.equals(act);
    }

    private boolean equalsJudge(JsonNode expected, Object userAnswer) {
        Boolean exp = toBool(expected.isValueNode() ? expected.asText() : expected.toString());
        Boolean act = toBool(stringify(userAnswer));
        return exp != null && exp.equals(act);
    }

    private Set<String> toKeySet(Object value) {
        Set<String> set = new TreeSet<>();
        try {
            if (value instanceof Iterable<?> it) {
                for (Object o : it) {
                    String k = normalizeKey(String.valueOf(o));
                    if (!k.isEmpty()) set.add(k);
                }
                return set;
            }
            JsonNode node = value instanceof JsonNode j ? j : objectMapper.readTree(objectMapper.writeValueAsString(value));
            if (node.isArray()) {
                return StreamSupport.stream(node.spliterator(), false)
                        .map(n -> normalizeKey(n.asText()))
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toCollection(TreeSet::new));
            }
            String raw = normalizeKey(node.asText());
            if (raw.contains(",")) {
                for (String p : raw.split(",")) {
                    String k = normalizeKey(p);
                    if (!k.isEmpty()) set.add(k);
                }
            } else if (!raw.isEmpty()) {
                set.add(raw);
            }
        } catch (Exception ignored) {
        }
        return set;
    }

    private Boolean toBool(String raw) {
        String v = normalizeKey(raw);
        if (v.equals("TRUE") || v.equals("正确") || v.equals("是") || v.equals("Y") || v.equals("YES") || v.equals("1")) {
            return true;
        }
        if (v.equals("FALSE") || v.equals("错误") || v.equals("否") || v.equals("N") || v.equals("NO") || v.equals("0")) {
            return false;
        }
        return null;
    }

    private String stringify(Object value) {
        if (value == null) return "";
        if (value instanceof String s) return s;
        try {
            JsonNode node = objectMapper.valueToTree(value);
            if (node.isTextual() || node.isBoolean() || node.isNumber()) {
                return node.asText();
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String normalizeKey(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase(Locale.ROOT).replace("\"", "");
    }

    public static final class GradeResult {
        private final Boolean correct;
        private final BigDecimal score;
        private final boolean needsManualReview;

        private GradeResult(Boolean correct, BigDecimal score, boolean needsManualReview) {
            this.correct = correct;
            this.score = score;
            this.needsManualReview = needsManualReview;
        }

        public static GradeResult pass(BigDecimal score) {
            return new GradeResult(true, score, false);
        }

        public static GradeResult fail() {
            return new GradeResult(false, BigDecimal.ZERO, false);
        }

        public static GradeResult manual() {
            return new GradeResult(null, BigDecimal.ZERO, true);
        }

        public Boolean correct() {
            return correct;
        }

        public BigDecimal score() {
            return score;
        }

        public boolean needsManualReview() {
            return needsManualReview;
        }
    }
}
