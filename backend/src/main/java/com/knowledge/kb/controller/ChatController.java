package com.knowledge.kb.controller;

import com.knowledge.kb.common.ApiResponse;
import com.knowledge.kb.dto.ChatMessageVO;
import com.knowledge.kb.dto.ChatRequest;
import com.knowledge.kb.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Tag(name = "知识库问答")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "SSE 流式问答")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('kb:chat:ask')")
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        return chatService.stream(request.getQuestion(), request.getSessionId());
    }

    @Operation(summary = "会话消息列表")
    @GetMapping("/sessions/{sessionId}/messages")
    @PreAuthorize("hasAuthority('kb:chat:ask')")
    public ApiResponse<List<ChatMessageVO>> messages(@PathVariable Long sessionId) {
        return ApiResponse.ok(chatService.listMessages(sessionId));
    }
}
