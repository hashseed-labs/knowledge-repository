package com.knowledge.kb.exam.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ExamPaperUpdateRequest {
    @NotBlank
    private String title;

    @NotEmpty
    @Valid
    private List<QuestionUpdate> questions;

    @Data
    public static class QuestionUpdate {
        private Long id;
        @NotBlank
        private String type;
        @NotBlank
        private String stem;
        private List<ExamOptionVO> options;
        private Object answer;
        private String analysis;
        private BigDecimal score;
        private List<Long> sourceDocIds;
        private List<Long> sourceChunkIds;
        private Integer sortNo;
    }
}
