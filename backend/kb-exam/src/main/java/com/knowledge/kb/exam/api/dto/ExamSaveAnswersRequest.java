package com.knowledge.kb.exam.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ExamSaveAnswersRequest {
    @NotEmpty
    @Valid
    private List<ExamAnswerItemRequest> answers;
}
