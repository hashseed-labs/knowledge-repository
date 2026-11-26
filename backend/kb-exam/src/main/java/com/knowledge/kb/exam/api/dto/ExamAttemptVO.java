package com.knowledge.kb.exam.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExamAttemptVO {
    private Long id;
    private Long paperId;
    private String paperTitle;
    private String status;
    private BigDecimal score;
    private BigDecimal maxScore;
    private Integer durationMinutes;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime submittedAt;
    private Long remainSeconds;
    private Integer pendingShortCount;
    private List<ExamAttemptAnswerVO> questions;
}
