package com.knowledge.kb.exam.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.kb.common.BizException;
import com.knowledge.kb.dto.PageResult;
import com.knowledge.kb.exam.api.dto.ExamAnswerItemRequest;
import com.knowledge.kb.exam.api.dto.ExamAttemptAnswerVO;
import com.knowledge.kb.exam.api.dto.ExamAttemptVO;
import com.knowledge.kb.exam.api.dto.ExamOptionVO;
import com.knowledge.kb.exam.api.dto.ExamSaveAnswersRequest;
import com.knowledge.kb.exam.domain.ExamAttemptStatus;
import com.knowledge.kb.exam.domain.ExamPaperStatus;
import com.knowledge.kb.exam.domain.ExamQuestionType;
import com.knowledge.kb.exam.infra.ExamAnswer;
import com.knowledge.kb.exam.infra.ExamAnswerMapper;
import com.knowledge.kb.exam.infra.ExamAttempt;
import com.knowledge.kb.exam.infra.ExamAttemptMapper;
import com.knowledge.kb.exam.infra.ExamPaper;
import com.knowledge.kb.exam.infra.ExamPaperMapper;
import com.knowledge.kb.exam.infra.ExamQuestion;
import com.knowledge.kb.exam.infra.ExamQuestionMapper;
import com.knowledge.kb.security.SecurityUtils;
import com.knowledge.kb.security.TenantContext;
import com.knowledge.kb.service.AuditService;
import com.knowledge.kb.service.KbSpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamAttemptService {

    private final ExamPaperMapper paperMapper;
    private final ExamQuestionMapper questionMapper;
    private final ExamAttemptMapper attemptMapper;
    private final ExamAnswerMapper answerMapper;
    private final ExamGrader examGrader;
    private final ObjectMapper objectMapper;
    private final KbSpaceService kbSpaceService;
    private final AuditService auditService;

    @Transactional
    public ExamAttemptVO start(Long paperId) {
        Long tenantId = TenantContext.requireTenantId();
        Long kbId = TenantContext.requireKbId();
        Long userId = SecurityUtils.currentUserId();
        kbSpaceService.assertKbAccess(kbId);

        ExamPaper paper = requireReadyPaper(paperId, tenantId, kbId);
        ExamAttempt existing = attemptMapper.selectOne(new LambdaQueryWrapper<ExamAttempt>()
                .eq(ExamAttempt::getPaperId, paperId)
                .eq(ExamAttempt::getUserId, userId)
                .eq(ExamAttempt::getStatus, ExamAttemptStatus.IN_PROGRESS)
                .orderByDesc(ExamAttempt::getId)
                .last("limit 1"));
        if (existing != null) {
            autoExpireIfNeeded(existing);
            if (ExamAttemptStatus.IN_PROGRESS.equals(existing.getStatus())) {
                return getDetail(existing.getId());
            }
        }

        int duration = readDurationMinutes(paper);
        LocalDateTime now = LocalDateTime.now();
        ExamAttempt attempt = new ExamAttempt();
        attempt.setPaperId(paperId);
        attempt.setTenantId(tenantId);
        attempt.setKbId(kbId);
        attempt.setUserId(userId);
        attempt.setStatus(ExamAttemptStatus.IN_PROGRESS);
        attempt.setMaxScore(paper.getTotalScore() == null ? BigDecimal.ZERO : paper.getTotalScore());
        attempt.setDurationMinutes(duration);
        attempt.setStartedAt(now);
        attempt.setExpiresAt(now.plusMinutes(duration));
        attemptMapper.insert(attempt);
        auditService.record("EXAM_START", "EXAM_ATTEMPT", String.valueOf(attempt.getId()), true,
                Map.of("paperId", paperId));
        return getDetail(attempt.getId());
    }

    public ExamAttemptVO getDetail(Long attemptId) {
        ExamAttempt attempt = requireOwnAttempt(attemptId);
        autoExpireIfNeeded(attempt);
        attempt = attemptMapper.selectById(attempt.getId());
        ExamPaper paper = paperMapper.selectById(attempt.getPaperId());
        List<ExamQuestion> questions = questionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getPaperId, attempt.getPaperId())
                .orderByAsc(ExamQuestion::getSortNo)
                .orderByAsc(ExamQuestion::getId));
        Map<Long, ExamAnswer> answerMap = answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                        .eq(ExamAnswer::getAttemptId, attemptId)).stream()
                .collect(Collectors.toMap(ExamAnswer::getQuestionId, Function.identity(), (a, b) -> a));

        boolean reveal = ExamAttemptStatus.SUBMITTED.equals(attempt.getStatus())
                || ExamAttemptStatus.EXPIRED.equals(attempt.getStatus());
        List<ExamAttemptAnswerVO> vos = new ArrayList<>();
        int pendingShort = 0;
        for (ExamQuestion q : questions) {
            ExamAnswer ans = answerMap.get(q.getId());
            Object userAnswer = ans == null ? null : readJson(ans.getAnswerJson(), Object.class);
            Boolean correct = ans == null ? null : (ans.getCorrect() == null ? null : ans.getCorrect() == 1);
            boolean pending = ExamQuestionType.SHORT.equals(q.getQType())
                    && (ans == null || ans.getCorrect() == null);
            if (reveal && pending) pendingShort++;
            vos.add(ExamAttemptAnswerVO.builder()
                    .questionId(q.getId())
                    .type(q.getQType())
                    .stem(q.getStem())
                    .options(readOptions(q.getOptionsJson()))
                    .score(q.getScore())
                    .sortNo(q.getSortNo())
                    .userAnswer(userAnswer)
                    .correctAnswer(reveal ? readJson(q.getAnswerJson(), Object.class) : null)
                    .analysis(reveal ? q.getAnalysisText() : null)
                    .correct(reveal ? correct : null)
                    .earnedScore(reveal && ans != null ? ans.getScore() : null)
                    .pendingReview(reveal ? pending : null)
                    .build());
        }

        return ExamAttemptVO.builder()
                .id(attempt.getId())
                .paperId(attempt.getPaperId())
                .paperTitle(paper == null ? null : paper.getTitle())
                .status(attempt.getStatus())
                .score(attempt.getScore())
                .maxScore(attempt.getMaxScore())
                .durationMinutes(attempt.getDurationMinutes())
                .startedAt(attempt.getStartedAt())
                .expiresAt(attempt.getExpiresAt())
                .submittedAt(attempt.getSubmittedAt())
                .remainSeconds(calcRemainSeconds(attempt))
                .pendingShortCount(reveal ? pendingShort : null)
                .questions(vos)
                .build();
    }

    @Transactional
    public ExamAttemptVO saveAnswers(Long attemptId, ExamSaveAnswersRequest request) {
        ExamAttempt attempt = requireOwnAttempt(attemptId);
        ensureInProgress(attempt);
        Map<Long, ExamQuestion> qmap = loadQuestionMap(attempt.getPaperId());
        LocalDateTime now = LocalDateTime.now();
        for (ExamAnswerItemRequest item : request.getAnswers()) {
            ExamQuestion q = qmap.get(item.getQuestionId());
            if (q == null) {
                throw new BizException("题目不属于本试卷: " + item.getQuestionId());
            }
            upsertAnswer(attemptId, item.getQuestionId(), item.getAnswer(), now);
        }
        return getDetail(attemptId);
    }

    @Transactional
    public ExamAttemptVO submit(Long attemptId, ExamSaveAnswersRequest request) {
        ExamAttempt attempt = requireOwnAttempt(attemptId);
        autoExpireIfNeeded(attempt);
        attempt = attemptMapper.selectById(attemptId);
        if (ExamAttemptStatus.EXPIRED.equals(attempt.getStatus())) {
            // still grade whatever answers exist
        } else if (!ExamAttemptStatus.IN_PROGRESS.equals(attempt.getStatus())) {
            throw new BizException("试卷已提交，无法再次交卷");
        } else {
            ensureNotTimedOut(attempt);
        }

        if (request != null && request.getAnswers() != null && !request.getAnswers().isEmpty()) {
            Map<Long, ExamQuestion> qmap = loadQuestionMap(attempt.getPaperId());
            LocalDateTime now = LocalDateTime.now();
            for (ExamAnswerItemRequest item : request.getAnswers()) {
                if (!qmap.containsKey(item.getQuestionId())) {
                    throw new BizException("题目不属于本试卷: " + item.getQuestionId());
                }
                upsertAnswer(attemptId, item.getQuestionId(), item.getAnswer(), now);
            }
        }

        return gradeAndFinish(attempt, ExamAttemptStatus.SUBMITTED);
    }

    public PageResult<ExamAttemptVO> myAttempts(long page, long size) {
        Long tenantId = TenantContext.requireTenantId();
        Long kbId = TenantContext.requireKbId();
        Long userId = SecurityUtils.currentUserId();
        kbSpaceService.assertKbAccess(kbId);
        Page<ExamAttempt> p = attemptMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ExamAttempt>()
                        .eq(ExamAttempt::getTenantId, tenantId)
                        .eq(ExamAttempt::getKbId, kbId)
                        .eq(ExamAttempt::getUserId, userId)
                        .orderByDesc(ExamAttempt::getId));
        List<ExamAttemptVO> records = p.getRecords().stream().map(a -> {
            ExamPaper paper = paperMapper.selectById(a.getPaperId());
            return ExamAttemptVO.builder()
                    .id(a.getId())
                    .paperId(a.getPaperId())
                    .paperTitle(paper == null ? null : paper.getTitle())
                    .status(a.getStatus())
                    .score(a.getScore())
                    .maxScore(a.getMaxScore())
                    .durationMinutes(a.getDurationMinutes())
                    .startedAt(a.getStartedAt())
                    .expiresAt(a.getExpiresAt())
                    .submittedAt(a.getSubmittedAt())
                    .remainSeconds(calcRemainSeconds(a))
                    .build();
        }).toList();
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), records);
    }

    private ExamAttemptVO gradeAndFinish(ExamAttempt attempt, String status) {
        List<ExamQuestion> questions = questionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getPaperId, attempt.getPaperId()));
        Map<Long, ExamAnswer> answerMap = answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                        .eq(ExamAnswer::getAttemptId, attempt.getId())).stream()
                .collect(Collectors.toMap(ExamAnswer::getQuestionId, Function.identity(), (a, b) -> a));

        BigDecimal total = BigDecimal.ZERO;
        LocalDateTime now = LocalDateTime.now();
        for (ExamQuestion q : questions) {
            ExamAnswer ans = answerMap.get(q.getId());
            Object userAnswer = ans == null ? null : readJson(ans.getAnswerJson(), Object.class);
            ExamGrader.GradeResult result = examGrader.grade(q, userAnswer);
            if (ans == null) {
                ans = new ExamAnswer();
                ans.setAttemptId(attempt.getId());
                ans.setQuestionId(q.getId());
                ans.setCreatedAt(now);
            }
            ans.setCorrect(result.correct() == null ? null : (result.correct() ? 1 : 0));
            ans.setScore(result.score());
            ans.setUpdatedAt(now);
            if (ans.getId() == null) {
                answerMapper.insert(ans);
            } else {
                answerMapper.updateById(ans);
            }
            if (!result.needsManualReview()) {
                total = total.add(result.score());
            }
        }

        attempt.setScore(total);
        attempt.setStatus(status);
        attempt.setSubmittedAt(now);
        attemptMapper.updateById(attempt);
        auditService.record("EXAM_SUBMIT", "EXAM_ATTEMPT", String.valueOf(attempt.getId()), true,
                Map.of("score", total, "status", status));
        return getDetail(attempt.getId());
    }

    private void upsertAnswer(Long attemptId, Long questionId, Object answer, LocalDateTime now) {
        ExamAnswer existed = answerMapper.selectOne(new LambdaQueryWrapper<ExamAnswer>()
                .eq(ExamAnswer::getAttemptId, attemptId)
                .eq(ExamAnswer::getQuestionId, questionId)
                .last("limit 1"));
        try {
            String json = objectMapper.writeValueAsString(answer);
            if (existed == null) {
                ExamAnswer row = new ExamAnswer();
                row.setAttemptId(attemptId);
                row.setQuestionId(questionId);
                row.setAnswerJson(json);
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                answerMapper.insert(row);
            } else {
                existed.setAnswerJson(json);
                existed.setUpdatedAt(now);
                // clear previous grade if any while still in progress
                existed.setCorrect(null);
                existed.setScore(null);
                answerMapper.updateById(existed);
            }
        } catch (Exception e) {
            throw new BizException("答案序列化失败");
        }
    }

    private void autoExpireIfNeeded(ExamAttempt attempt) {
        if (!ExamAttemptStatus.IN_PROGRESS.equals(attempt.getStatus())) {
            return;
        }
        if (attempt.getExpiresAt() != null && LocalDateTime.now().isAfter(attempt.getExpiresAt())) {
            gradeAndFinish(attempt, ExamAttemptStatus.EXPIRED);
        }
    }

    private void ensureInProgress(ExamAttempt attempt) {
        autoExpireIfNeeded(attempt);
        ExamAttempt latest = attemptMapper.selectById(attempt.getId());
        if (!ExamAttemptStatus.IN_PROGRESS.equals(latest.getStatus())) {
            throw new BizException("考试已结束，无法继续作答");
        }
        ensureNotTimedOut(latest);
    }

    private void ensureNotTimedOut(ExamAttempt attempt) {
        if (attempt.getExpiresAt() != null && LocalDateTime.now().isAfter(attempt.getExpiresAt())) {
            throw new BizException("考试时间已到");
        }
    }

    private ExamPaper requireReadyPaper(Long paperId, Long tenantId, Long kbId) {
        ExamPaper paper = paperMapper.selectById(paperId);
        if (paper == null || !tenantId.equals(paper.getTenantId()) || !kbId.equals(paper.getKbId())) {
            throw new BizException("试卷不存在");
        }
        if (!ExamPaperStatus.READY.equals(paper.getStatus())) {
            throw new BizException("仅已发布(READY)试卷可开考");
        }
        Long count = questionMapper.selectCount(new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getPaperId, paperId));
        if (count == null || count == 0) {
            throw new BizException("试卷没有题目");
        }
        return paper;
    }

    private ExamAttempt requireOwnAttempt(Long attemptId) {
        Long tenantId = TenantContext.requireTenantId();
        Long kbId = TenantContext.requireKbId();
        Long userId = SecurityUtils.currentUserId();
        kbSpaceService.assertKbAccess(kbId);
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null || !tenantId.equals(attempt.getTenantId()) || !kbId.equals(attempt.getKbId())
                || !userId.equals(attempt.getUserId())) {
            throw new BizException("考试记录不存在");
        }
        return attempt;
    }

    private Map<Long, ExamQuestion> loadQuestionMap(Long paperId) {
        return questionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getPaperId, paperId))
                .stream().collect(Collectors.toMap(ExamQuestion::getId, Function.identity(), (a, b) -> a, HashMap::new));
    }

    private int readDurationMinutes(ExamPaper paper) {
        try {
            if (paper.getConfigJson() != null) {
                JsonNode node = objectMapper.readTree(paper.getConfigJson());
                if (node.has("durationMinutes") && node.get("durationMinutes").asInt() > 0) {
                    return node.get("durationMinutes").asInt();
                }
            }
        } catch (Exception ignored) {
        }
        return 60;
    }

    private Long calcRemainSeconds(ExamAttempt attempt) {
        if (!ExamAttemptStatus.IN_PROGRESS.equals(attempt.getStatus()) || attempt.getExpiresAt() == null) {
            return 0L;
        }
        long sec = Duration.between(LocalDateTime.now(), attempt.getExpiresAt()).getSeconds();
        return Math.max(0, sec);
    }

    private List<ExamOptionVO> readOptions(String json) {
        List<ExamOptionVO> list = readJson(json, new TypeReference<>() {
        });
        return list == null ? List.of() : list;
    }

    private <T> T readJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }
}
