package com.knowledge.kb.exam.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ExamQuestionVO {
    private Long id;
    private String type;
    private String stem;
    private List<ExamOptionVO> options;
    private Object answer;
    private String analysis;
    private BigDecimal score;
    private List<Long> sourceDocIds;
    private List<Long> sourceChunkIds;
    private Integer sortNo;
}
