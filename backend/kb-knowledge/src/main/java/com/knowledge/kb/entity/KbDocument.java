package com.knowledge.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_document")
public class KbDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long kbId;
    private Long createdBy;
    private String title;
    private String objectKey;
    private String status;
    private Integer chunkCount;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
