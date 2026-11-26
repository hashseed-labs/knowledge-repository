package com.knowledge.kb.exam.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.kb.common.BizException;
import com.knowledge.kb.dto.PageResult;
import com.knowledge.kb.exam.api.dto.ExamOptionVO;
import com.knowledge.kb.exam.api.dto.ExamPaperUpdateRequest;
import com.knowledge.kb.exam.api.dto.ExamPaperVO;
import com.knowledge.kb.exam.api.dto.ExamQuestionVO;
import com.knowledge.kb.exam.domain.ExamPaperStatus;
import com.knowledge.kb.exam.domain.ExamQuestionType;
import com.knowledge.kb.exam.infra.ExamPaper;
import com.knowledge.kb.exam.infra.ExamPaperMapper;
import com.knowledge.kb.exam.infra.ExamQuestion;
import com.knowledge.kb.exam.infra.ExamQuestionMapper;
import com.knowledge.kb.security.TenantContext;
import com.knowledge.kb.service.AuditService;
import com.knowledge.kb.service.KbSpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExamPaperService {

    private final ExamPaperMapper paperMapper;
    private final ExamQuestionMapper questionMapper;
    private final ObjectMapper objectMapper;
    private final KbSpaceService kbSpaceService;
    private final AuditService auditService;

    public PageResult<ExamPaperVO> page(long page, long size) {
        Long tenantId = TenantContext.requireTenantId();
        Long kbId = TenantContext.requireKbId();
        kbSpaceService.assertKbAccess(kbId);
        Page<ExamPaper> p = paperMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ExamPaper>()
                        .eq(ExamPaper::getTenantId, tenantId)
                        .eq(ExamPaper::getKbId, kbId)
                        .orderByDesc(ExamPaper::getId));
        List<ExamPaperVO> records = p.getRecords().stream().map(this::toSummary).toList();
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), records);
    }

    public ExamPaperVO getDetail(Long id) {
        ExamPaper paper = requirePaper(id);
        List<ExamQuestion> questions = questionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getPaperId, id)
                .orderByAsc(ExamQuestion::getSortNo)
                .orderByAsc(ExamQuestion::getId));
        return toDetail(paper, questions);
    }

    @Transactional
    public ExamPaperVO update(Long id, ExamPaperUpdateRequest request) {
        ExamPaper paper = requirePaper(id);
        if (ExamPaperStatus.ARCHIVED.equals(paper.getStatus())) {
            throw new BizException("已归档试卷不可编辑");
        }
        paper.setTitle(request.getTitle().trim());
        paper.setUpdatedAt(LocalDateTime.now());

        questionMapper.delete(new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getPaperId, id));
        BigDecimal total = BigDecimal.ZERO;
        int sort = 1;
        for (ExamPaperUpdateRequest.QuestionUpdate q : request.getQuestions()) {
            ExamQuestion row = new ExamQuestion();
            row.setPaperId(id);
            row.setTenantId(paper.getTenantId());
            row.setKbId(paper.getKbId());
            row.setQType(ExamQuestionType.normalize(q.getType()));
            row.setStem(q.getStem().trim());
            try {
                row.setOptionsJson(objectMapper.writeValueAsString(q.getOptions() == null ? List.of() : q.getOptions()));
                row.setAnswerJson(objectMapper.writeValueAsString(q.getAnswer()));
                row.setSourceDocIds(objectMapper.writeValueAsString(q.getSourceDocIds() == null ? List.of() : q.getSourceDocIds()));
                row.setSourceChunkIds(objectMapper.writeValueAsString(q.getSourceChunkIds() == null ? List.of() : q.getSourceChunkIds()));
            } catch (Exception e) {
                throw new BizException("题目序列化失败");
            }
            row.setAnalysisText(q.getAnalysis());
            BigDecimal score = q.getScore() == null ? BigDecimal.ONE : q.getScore();
            row.setScore(score);
            total = total.add(score);
            row.setSortNo(q.getSortNo() == null ? sort : q.getSortNo());
            sort++;
            row.setCreatedAt(LocalDateTime.now());
            row.setUpdatedAt(LocalDateTime.now());
            questionMapper.insert(row);
        }
        paper.setQuestionCount(request.getQuestions().size());
        paper.setTotalScore(total);
        paper.setStatus(ExamPaperStatus.DRAFT);
        paperMapper.updateById(paper);
        auditService.record("EXAM_UPDATE", "EXAM_PAPER", String.valueOf(id), true, Map.of("count", request.getQuestions().size()));
        return getDetail(id);
    }

    @Transactional
    public ExamPaperVO publish(Long id) {
        ExamPaper paper = requirePaper(id);
        Long count = questionMapper.selectCount(new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getPaperId, id));
        if (count == null || count == 0) {
            throw new BizException("试卷没有题目，无法发布");
        }
        if (ExamPaperStatus.FAILED.equals(paper.getStatus())) {
            throw new BizException("失败状态试卷不可发布，请重新生成或编辑");
        }
        paper.setStatus(ExamPaperStatus.READY);
        paper.setUpdatedAt(LocalDateTime.now());
        paperMapper.updateById(paper);
        auditService.record("EXAM_PUBLISH", "EXAM_PAPER", String.valueOf(id), true, Map.of());
        return getDetail(id);
    }

    @Transactional
    public void delete(Long id) {
        ExamPaper paper = requirePaper(id);
        questionMapper.delete(new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getPaperId, id));
        paperMapper.deleteById(id);
        auditService.record("EXAM_DELETE", "EXAM_PAPER", String.valueOf(id), true, Map.of("title", paper.getTitle()));
    }

    private ExamPaper requirePaper(Long id) {
        Long tenantId = TenantContext.requireTenantId();
        Long kbId = TenantContext.requireKbId();
        kbSpaceService.assertKbAccess(kbId);
        ExamPaper paper = paperMapper.selectById(id);
        if (paper == null || !tenantId.equals(paper.getTenantId()) || !kbId.equals(paper.getKbId())) {
            throw new BizException("试卷不存在");
        }
        return paper;
    }

    private ExamPaperVO toSummary(ExamPaper paper) {
        return ExamPaperVO.builder()
                .id(paper.getId())
                .title(paper.getTitle())
                .status(paper.getStatus())
                .config(readJson(paper.getConfigJson(), Object.class))
                .sourceDocIds(readLongList(paper.getSourceDocIds()))
                .questionCount(paper.getQuestionCount())
                .totalScore(paper.getTotalScore())
                .errorMsg(paper.getErrorMsg())
                .createdAt(paper.getCreatedAt())
                .updatedAt(paper.getUpdatedAt())
                .build();
    }

    private ExamPaperVO toDetail(ExamPaper paper, List<ExamQuestion> questions) {
        ExamPaperVO vo = toSummary(paper);
        List<ExamQuestionVO> qvos = new ArrayList<>();
        for (ExamQuestion q : questions) {
            qvos.add(ExamQuestionVO.builder()
                    .id(q.getId())
                    .type(q.getQType())
                    .stem(q.getStem())
                    .options(readOptions(q.getOptionsJson()))
                    .answer(readJson(q.getAnswerJson(), Object.class))
                    .analysis(q.getAnalysisText())
                    .score(q.getScore())
                    .sourceDocIds(readLongList(q.getSourceDocIds()))
                    .sourceChunkIds(readLongList(q.getSourceChunkIds()))
                    .sortNo(q.getSortNo())
                    .build());
        }
        vo.setQuestions(qvos);
        return vo;
    }

    private List<ExamOptionVO> readOptions(String json) {
        List<ExamOptionVO> list = readJson(json, new TypeReference<List<ExamOptionVO>>() {
        });
        return list == null ? List.of() : list;
    }

    private List<Long> readLongList(String json) {
        List<Long> list = readJson(json, new TypeReference<List<Long>>() {
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
