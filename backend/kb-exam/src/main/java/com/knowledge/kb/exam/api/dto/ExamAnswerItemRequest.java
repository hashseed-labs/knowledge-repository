package com.knowledge.kb.exam.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExamAnswerItemRequest {
    @NotNull
    private Long questionId;
    private Object answer;
}
