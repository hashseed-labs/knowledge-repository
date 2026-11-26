package com.knowledge.kb.exam.domain;

import java.util.Locale;
import java.util.Set;

public final class ExamQuestionType {
    public static final String SINGLE = "SINGLE";
    public static final String MULTI = "MULTI";
    public static final String JUDGE = "JUDGE";
    public static final String SHORT = "SHORT";

    private static final Set<String> ALL = Set.of(SINGLE, MULTI, JUDGE, SHORT);

    private ExamQuestionType() {
    }

    public static boolean isValid(String type) {
        return type != null && ALL.contains(type.trim().toUpperCase(Locale.ROOT));
    }

    public static String normalize(String type) {
        if (type == null) {
            throw new IllegalArgumentException("question type required");
        }
        String t = type.trim().toUpperCase(Locale.ROOT);
        if (!ALL.contains(t)) {
            throw new IllegalArgumentException("unsupported question type: " + type);
        }
        return t;
    }
}
