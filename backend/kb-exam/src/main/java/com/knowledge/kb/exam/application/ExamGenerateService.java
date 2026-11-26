package com.knowledge.kb.exam.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.kb.common.BizException;
import com.knowledge.kb.entity.DocumentStatus;
import com.knowledge.kb.entity.KbChunk;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.exam.api.dto.ExamGenerateRequest;
import com.knowledge.kb.exam.api.dto.ExamPaperVO;
import com.knowledge.kb.exam.domain.ExamPaperStatus;
import com.knowledge.kb.exam.infra.ExamPaper;
import com.knowledge.kb.exam.infra.ExamPaperMapper;
import com.knowledge.kb.exam.infra.ExamQuestion;
import com.knowledge.kb.exam.infra.ExamQuestionMapper;
import com.knowledge.kb.exam.llm.ExamLlmPrompt;
import com.knowledge.kb.exam.llm.JsonExamParser;
import com.knowledge.kb.integration.EtcdConfigService;
import com.knowledge.kb.integration.LlmClient;
import com.knowledge.kb.mapper.KbChunkMapper;
import com.knowledge.kb.mapper.KbDocumentMapper;
import com.knowledge.kb.security.SecurityUtils;
import com.knowledge.kb.security.TenantContext;
import com.knowledge.kb.service.AuditService;
import com.knowledge.kb.service.KbSpaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamGenerateService {

    private static final int MAX_CHUNKS_PER_BATCH = 8;

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final ExamPaperMapper paperMapper;
    private final ExamQuestionMapper questionMapper;
    private final LlmClient llmClient;
    private final JsonExamParser jsonExamParser;
    private final ObjectMapper objectMapper;
    private final EtcdConfigService etcdConfigService;
    private final KbSpaceService kbSpaceService;
    private final AuditService auditService;
    private final ExamPaperService examPaperService;

    @Transactional
    public ExamPaperVO generate(ExamGenerateRequest request) {
        Long tenantId = TenantContext.requireTenantId();
        Long kbId = TenantContext.requireKbId();
        kbSpaceService.assertKbAccess(kbId);

        int totalNeed = request.getSingleCount() + request.getMultiCount()
                + request.getJudgeCount() + request.getShortCount();
        if (totalNeed <= 0) {
            throw new BizException("题量合计必须大于 0");
        }

        List<KbDocument> docs = loadReadyDocs(request.getDocIds(), tenantId, kbId);
        List<KbChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getTenantId, tenantId)
                .eq(KbChunk::getKbId, kbId)
                .in(KbChunk::getDocId, docs.stream().map(KbDocument::getId).toList())
                .orderByAsc(KbChunk::getDocId)
                .orderByAsc(KbChunk::getChunkIndex));
        if (chunks.isEmpty()) {
            throw new BizException("所选文档没有可用分块，请确认文档已向量化完成");
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("singleCount", request.getSingleCount());
        config.put("multiCount", request.getMultiCount());
        config.put("judgeCount", request.getJudgeCount());
        config.put("shortCount", request.getShortCount());
        config.put("difficulty", request.getDifficulty());
        config.put("durationMinutes", request.getDurationMinutes() <= 0 ? 60 : request.getDurationMinutes());
        config.put("provider", llmClient.provider());

        ExamPaper paper = new ExamPaper();
        paper.setTenantId(tenantId);
        paper.setKbId(kbId);
        paper.setTitle(request.getTitle().trim());
        paper.setStatus(ExamPaperStatus.DRAFT);
        try {
            paper.setConfigJson(objectMapper.writeValueAsString(config));
            paper.setSourceDocIds(objectMapper.writeValueAsString(
                    docs.stream().map(KbDocument::getId).toList()));
        } catch (Exception e) {
            throw new BizException("配置序列化失败");
        }
        paper.setQuestionCount(0);
        paper.setTotalScore(BigDecimal.ZERO);
        paper.setCreatedBy(SecurityUtils.currentUserId());
        paper.setCreatedAt(LocalDateTime.now());
        paper.setUpdatedAt(LocalDateTime.now());
        paperMapper.insert(paper);

        try {
            List<JsonExamParser.ParsedQuestion> parsed = generateByBatches(request, chunks);
            if (parsed.isEmpty()) {
                throw new BizException("未能生成任何题目");
            }
            persistQuestions(paper, parsed, chunks);
            paper.setQuestionCount(parsed.size());
            paper.setTotalScore(parsed.stream()
                    .map(JsonExamParser.ParsedQuestion::score)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            paper.setStatus(ExamPaperStatus.DRAFT);
            paper.setErrorMsg(null);
            paper.setUpdatedAt(LocalDateTime.now());
            paperMapper.updateById(paper);
            auditService.record("EXAM_GENERATE", "EXAM_PAPER", String.valueOf(paper.getId()), true,
                    Map.of("title", paper.getTitle(), "count", parsed.size()));
            return examPaperService.getDetail(paper.getId());
        } catch (Exception e) {
            log.error("Exam generate failed paperId={}", paper.getId(), e);
            paper.setStatus(ExamPaperStatus.FAILED);
            paper.setErrorMsg(trim(e.getMessage()));
            paper.setUpdatedAt(LocalDateTime.now());
            paperMapper.updateById(paper);
            auditService.record("EXAM_GENERATE", "EXAM_PAPER", String.valueOf(paper.getId()), false,
                    Map.of("error", String.valueOf(e.getMessage())));
            throw e instanceof BizException be ? be : new BizException("生成试卷失败: " + e.getMessage());
        }
    }

    private List<JsonExamParser.ParsedQuestion> generateByBatches(ExamGenerateRequest request, List<KbChunk> chunks) {
        List<List<KbChunk>> batches = partition(chunks, MAX_CHUNKS_PER_BATCH);
        Map<String, Integer> remaining = new HashMap<>();
        remaining.put("SINGLE", request.getSingleCount());
        remaining.put("MULTI", request.getMultiCount());
        remaining.put("JUDGE", request.getJudgeCount());
        remaining.put("SHORT", request.getShortCount());

        List<JsonExamParser.ParsedQuestion> all = new ArrayList<>();
        Set<String> seenStems = new LinkedHashSet<>();
        double temperature = Math.min(0.4, etcdConfigService.getTemperature());

        for (int i = 0; i < batches.size(); i++) {
            int left = remaining.values().stream().mapToInt(Integer::intValue).sum();
            if (left <= 0) break;
            Map<String, Integer> batchNeed = allocate(remaining, batches.size() - i);
            if (batchNeed.values().stream().mapToInt(Integer::intValue).sum() <= 0) break;

            String prompt = ExamLlmPrompt.build(request.getDifficulty(), batchNeed, batches.get(i));
            List<JsonExamParser.ParsedQuestion> parsed = callWithRetry(prompt, temperature);
            Set<Long> allowedChunkIds = batches.get(i).stream().map(KbChunk::getId).collect(Collectors.toSet());

            for (JsonExamParser.ParsedQuestion q : parsed) {
                String stemKey = q.stem().trim();
                if (seenStems.contains(stemKey)) continue;
                List<Long> validChunks = q.sourceChunkIds().stream().filter(allowedChunkIds::contains).toList();
                if (validChunks.isEmpty()) continue;
                String type = q.type();
                Integer need = remaining.get(type);
                if (need == null || need <= 0) continue;
                remaining.put(type, need - 1);
                seenStems.add(stemKey);
                all.add(new JsonExamParser.ParsedQuestion(
                        q.type(), q.stem(), q.options(), q.answer(), q.analysis(), q.score(), validChunks));
            }
        }
        return all;
    }

    private List<JsonExamParser.ParsedQuestion> callWithRetry(String prompt, double temperature) {
        try {
            return jsonExamParser.parse(llmClient.generate(prompt, temperature));
        } catch (Exception first) {
            log.warn("Exam LLM parse failed, retry once: {}", first.getMessage());
            String retryPrompt = prompt + "\n\n上次输出无法解析，请只输出合法 JSON 对象。";
            return jsonExamParser.parse(llmClient.generate(retryPrompt, Math.min(temperature, 0.2)));
        }
    }

    private void persistQuestions(ExamPaper paper,
                                  List<JsonExamParser.ParsedQuestion> parsed,
                                  List<KbChunk> chunks) throws Exception {
        Map<Long, Long> chunkToDoc = chunks.stream()
                .collect(Collectors.toMap(KbChunk::getId, KbChunk::getDocId, (a, b) -> a));
        int sort = 1;
        for (JsonExamParser.ParsedQuestion q : parsed) {
            ExamQuestion row = new ExamQuestion();
            row.setPaperId(paper.getId());
            row.setTenantId(paper.getTenantId());
            row.setKbId(paper.getKbId());
            row.setQType(q.type());
            row.setStem(q.stem());
            row.setOptionsJson(objectMapper.writeValueAsString(q.options()));
            row.setAnswerJson(objectMapper.writeValueAsString(q.answer()));
            row.setAnalysisText(q.analysis());
            row.setScore(q.score() == null ? BigDecimal.ONE : q.score());
            List<Long> docIds = q.sourceChunkIds().stream()
                    .map(chunkToDoc::get)
                    .filter(id -> id != null)
                    .distinct()
                    .toList();
            row.setSourceDocIds(objectMapper.writeValueAsString(docIds));
            row.setSourceChunkIds(objectMapper.writeValueAsString(q.sourceChunkIds()));
            row.setSortNo(sort++);
            row.setCreatedAt(LocalDateTime.now());
            row.setUpdatedAt(LocalDateTime.now());
            questionMapper.insert(row);
        }
    }

    private List<KbDocument> loadReadyDocs(List<Long> docIds, Long tenantId, Long kbId) {
        List<Long> ids = docIds.stream().distinct().toList();
        List<KbDocument> docs = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getTenantId, tenantId)
                .eq(KbDocument::getKbId, kbId)
                .in(KbDocument::getId, ids));
        if (docs.size() != ids.size()) {
            throw new BizException("部分文档不存在或不属于当前知识库");
        }
        for (KbDocument doc : docs) {
            if (!DocumentStatus.READY.equalsIgnoreCase(doc.getStatus())) {
                throw new BizException("文档未就绪，无法出题: " + doc.getTitle() + " (" + doc.getStatus() + ")");
            }
        }
        return docs;
    }

    private Map<String, Integer> allocate(Map<String, Integer> remaining, int batchesLeft) {
        Map<String, Integer> need = new HashMap<>();
        for (Map.Entry<String, Integer> e : remaining.entrySet()) {
            int left = Math.max(0, e.getValue());
            int take = batchesLeft <= 1 ? left : (int) Math.ceil(left * 1.0 / batchesLeft);
            need.put(e.getKey(), Math.min(left, take));
        }
        return need;
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }

    private String trim(String msg) {
        if (msg == null) return "unknown error";
        return msg.length() > 900 ? msg.substring(0, 900) : msg;
    }
}
