package com.knowledge.kb.exam.infra;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("exam_answer")
public class ExamAnswer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long attemptId;
    private Long questionId;
    private String answerJson;
    private Integer correct;
    private BigDecimal score;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
