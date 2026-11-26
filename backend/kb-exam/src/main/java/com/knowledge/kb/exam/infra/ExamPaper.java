package com.knowledge.kb.exam.infra;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("exam_paper")
public class ExamPaper {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long kbId;
    private String title;
    private String status;
    private String configJson;
    private String sourceDocIds;
    private Integer questionCount;
    private BigDecimal totalScore;
    private Long createdBy;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
