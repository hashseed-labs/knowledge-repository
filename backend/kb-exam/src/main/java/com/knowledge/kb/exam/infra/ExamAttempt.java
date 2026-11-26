package com.knowledge.kb.exam.infra;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("exam_attempt")
public class ExamAttempt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private Long tenantId;
    private Long kbId;
    private Long userId;
    private String status;
    private BigDecimal score;
    private BigDecimal maxScore;
    private Integer durationMinutes;
    private LocalDateTime expiresAt;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
}
