package com.knowledge.kb.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CitationVO {
    private Long docId;
    private String docTitle;
    private Long chunkId;
    private Integer chunkIndex;
    private String content;
    private Float score;
}
