package com.knowledge.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_chunk")
public class KbChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long kbId;
    private Long docId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenEstimate;
    private Long milvusId;
    private LocalDateTime createdAt;
}
