package com.knowledge.kb.exam.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ExamAttemptAnswerVO {
    private Long questionId;
    private String type;
    private String stem;
    private List<ExamOptionVO> options;
    private BigDecimal score;
    private Integer sortNo;
    private Object userAnswer;
    /** 交卷后才返回 */
    private Object correctAnswer;
    private String analysis;
    private Boolean correct;
    private BigDecimal earnedScore;
    private Boolean pendingReview;
}
