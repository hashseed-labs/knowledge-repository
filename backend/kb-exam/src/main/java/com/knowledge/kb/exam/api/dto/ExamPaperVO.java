package com.knowledge.kb.exam.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExamPaperVO {
    private Long id;
    private String title;
    private String status;
    private Object config;
    private List<Long> sourceDocIds;
    private Integer questionCount;
    private BigDecimal totalScore;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ExamQuestionVO> questions;
}
