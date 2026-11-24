package com.knowledge.kb.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "question 不能为空")
    private String question;

    private Long sessionId;
}
