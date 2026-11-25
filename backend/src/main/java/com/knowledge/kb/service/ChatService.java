package com.knowledge.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.kb.common.BizException;
import com.knowledge.kb.dto.ChatMessageVO;
import com.knowledge.kb.dto.CitationVO;
import com.knowledge.kb.entity.KbChatMessage;
import com.knowledge.kb.entity.KbChatSession;
import com.knowledge.kb.entity.KbChunk;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.integration.EtcdConfigService;
import com.knowledge.kb.integration.MilvusService;
import com.knowledge.kb.integration.OllamaClient;
import com.knowledge.kb.mapper.KbChatMessageMapper;
import com.knowledge.kb.mapper.KbChatSessionMapper;
import com.knowledge.kb.mapper.KbChunkMapper;
import com.knowledge.kb.mapper.KbDocumentMapper;
import com.knowledge.kb.security.SecurityUtils;
import com.knowledge.kb.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final OllamaClient ollamaClient;
    private final MilvusService milvusService;
    private final EtcdConfigService etcdConfigService;
    private final KbChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;
    private final KbChatSessionMapper sessionMapper;
    private final KbChatMessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final KbSpaceService kbSpaceService;
    private final AuditService auditService;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public SseEmitter stream(String question, Long sessionId) {
        Long tenantId = TenantContext.requireTenantId();
        Long kbId = TenantContext.requireKbId();
        Long userId = SecurityUtils.currentUserId();
        kbSpaceService.assertKbAccess(kbId);
        auditService.record("CHAT_ASK", "CHAT", null, true, Map.of("question", question, "kbId", kbId));

        SseEmitter emitter = new SseEmitter(0L);
        sseExecutor.execute(() -> {
            StringBuilder answer = new StringBuilder();
            Long sid = sessionId;
            try {
                if (sid == null) {
                    KbChatSession session = new KbChatSession();
                    session.setTenantId(tenantId);
                    session.setKbId(kbId);
                    session.setUserId(userId);
                    session.setTitle(question.length() > 40 ? question.substring(0, 40) : question);
                    session.setCreatedAt(LocalDateTime.now());
                    session.setUpdatedAt(LocalDateTime.now());
                    sessionMapper.insert(session);
                    sid = session.getId();
                } else {
                    KbChatSession existing = sessionMapper.selectById(sid);
                    if (existing == null || !tenantId.equals(existing.getTenantId())
                            || !kbId.equals(existing.getKbId()) || !userId.equals(existing.getUserId())) {
                        throw new BizException("会话不存在");
                    }
                }

                saveMessage(sid, "user", question, null);
                sendEvent(emitter, "session", Map.of("sessionId", sid));

                List<Float> embedding = ollamaClient.embed(question);
                int topK = etcdConfigService.getTopK();
                List<MilvusService.SearchHit> hits = milvusService.search(embedding, topK, tenantId, kbId);
                List<CitationVO> citations = buildCitations(hits);

                sendEvent(emitter, "meta", Map.of("citations", citations, "sessionId", sid));

                String prompt = buildPrompt(question, citations);
                Long finalSid = sid;
                double temperature = etcdConfigService.getTemperature();
                ollamaClient.generateStream(prompt, temperature, token -> {
                    answer.append(token);
                    try {
                        sendEvent(emitter, "token", Map.of("content", token));
                    } catch (IOException e) {
                        throw new BizException("SSE 推送失败: " + e.getMessage());
                    }
                });

                saveMessage(finalSid, "assistant", answer.toString(), citations);
                sendEvent(emitter, "done", Map.of(
                        "sessionId", finalSid,
                        "content", answer.toString(),
                        "citations", citations
                ));
                emitter.complete();
            } catch (Exception e) {
                log.error("Chat stream failed", e);
                try {
                    sendEvent(emitter, "error", Map.of("message", e.getMessage() == null ? "chat failed" : e.getMessage()));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    public List<ChatMessageVO> listMessages(Long sessionId) {
        Long tenantId = TenantContext.requireTenantId();
        Long kbId = TenantContext.requireKbId();
        Long userId = SecurityUtils.currentUserId();
        kbSpaceService.assertKbAccess(kbId);
        KbChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !tenantId.equals(session.getTenantId())
                || !kbId.equals(session.getKbId()) || !userId.equals(session.getUserId())) {
            throw new BizException("会话不存在");
        }
        List<KbChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<KbChatMessage>()
                .eq(KbChatMessage::getSessionId, sessionId)
                .orderByAsc(KbChatMessage::getId));
        return messages.stream().map(this::toVo).toList();
    }

    private List<CitationVO> buildCitations(List<MilvusService.SearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<Long> chunkIds = hits.stream().map(MilvusService.SearchHit::getChunkId).distinct().toList();
        Map<Long, KbChunk> chunkMap = chunkMapper.selectBatchIds(chunkIds).stream()
                .collect(Collectors.toMap(KbChunk::getId, c -> c, (a, b) -> a));
        List<Long> docIds = hits.stream().map(MilvusService.SearchHit::getDocId).distinct().toList();
        Map<Long, KbDocument> docMap = documentMapper.selectBatchIds(docIds).stream()
                .collect(Collectors.toMap(KbDocument::getId, d -> d, (a, b) -> a));

        List<CitationVO> citations = new ArrayList<>();
        for (MilvusService.SearchHit hit : hits) {
            KbChunk chunk = chunkMap.get(hit.getChunkId());
            KbDocument doc = docMap.get(hit.getDocId());
            if (chunk == null) {
                continue;
            }
            String preview = chunk.getContent();
            if (preview.length() > 240) {
                preview = preview.substring(0, 240) + "...";
            }
            citations.add(CitationVO.builder()
                    .docId(hit.getDocId())
                    .docTitle(doc == null ? "unknown" : doc.getTitle())
                    .chunkId(chunk.getId())
                    .chunkIndex(chunk.getChunkIndex())
                    .content(preview)
                    .score(hit.getScore())
                    .build());
        }
        return citations;
    }

    private String buildPrompt(String question, List<CitationVO> citations) {
        StringBuilder ctx = new StringBuilder();
        if (citations.isEmpty()) {
            ctx.append("（无检索到相关知识片段）\n");
        } else {
            int i = 1;
            for (CitationVO c : citations) {
                ctx.append("[").append(i++).append("] 来源: ").append(c.getDocTitle())
                        .append("\n").append(c.getContent()).append("\n\n");
            }
        }
        return etcdConfigService.getSystemPrompt()
                + "\n\n【检索上下文】\n" + ctx
                + "\n【用户问题】\n" + question
                + "\n\n【助手回答】\n";
    }

    private void saveMessage(Long sessionId, String role, String content, List<CitationVO> citations) throws Exception {
        KbChatMessage msg = new KbChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        if (citations != null) {
            msg.setCitations(objectMapper.writeValueAsString(citations));
        }
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);

        KbChatSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
    }

    private ChatMessageVO toVo(KbChatMessage msg) {
        List<CitationVO> citations = List.of();
        if (msg.getCitations() != null && !msg.getCitations().isBlank()) {
            try {
                citations = objectMapper.readValue(msg.getCitations(), new TypeReference<>() {
                });
            } catch (Exception ignored) {
            }
        }
        return ChatMessageVO.builder()
                .id(msg.getId())
                .role(msg.getRole())
                .content(msg.getContent())
                .citations(citations)
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private void sendEvent(SseEmitter emitter, String event, Object data) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        if (data instanceof Map<?, ?> map) {
            map.forEach((k, v) -> payload.put(String.valueOf(k), v));
        } else {
            payload.put("data", data);
        }
        emitter.send(SseEmitter.event().name(event).data(payload));
    }
}
