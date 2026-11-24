package com.knowledge.kb.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentVO {
    private Long id;
    private String title;
    private String status;
    private Integer chunkCount;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
