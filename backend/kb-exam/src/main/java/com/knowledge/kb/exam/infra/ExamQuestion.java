package com.knowledge.kb.exam.infra;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("exam_question")
public class ExamQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private Long tenantId;
    private Long kbId;
    private String qType;
    private String stem;
    private String optionsJson;
    private String answerJson;
    private String analysisText;
    private BigDecimal score;
    private String sourceDocIds;
    private String sourceChunkIds;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
