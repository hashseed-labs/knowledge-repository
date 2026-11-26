package com.knowledge.kb.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ChatMessageVO {
    private Long id;
    private String role;
    private String content;
    private List<CitationVO> citations;
    private LocalDateTime createdAt;
}
