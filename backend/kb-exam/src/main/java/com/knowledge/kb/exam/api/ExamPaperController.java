package com.knowledge.kb.exam.api;

import com.knowledge.kb.common.ApiResponse;
import com.knowledge.kb.dto.PageResult;
import com.knowledge.kb.exam.api.dto.ExamGenerateRequest;
import com.knowledge.kb.exam.api.dto.ExamPaperUpdateRequest;
import com.knowledge.kb.exam.api.dto.ExamPaperVO;
import com.knowledge.kb.exam.application.ExamGenerateService;
import com.knowledge.kb.exam.application.ExamPaperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "在线考试-试卷")
@RestController
@RequestMapping("/api/exam/papers")
@RequiredArgsConstructor
public class ExamPaperController {

    private final ExamGenerateService examGenerateService;
    private final ExamPaperService examPaperService;

    @Operation(summary = "根据文档生成试卷")
    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('exam:paper:generate')")
    public ApiResponse<ExamPaperVO> generate(@Valid @RequestBody ExamGenerateRequest request) {
        return ApiResponse.ok(examGenerateService.generate(request));
    }

    @Operation(summary = "试卷分页")
    @GetMapping
    @PreAuthorize("hasAuthority('exam:paper:read')")
    public ApiResponse<PageResult<ExamPaperVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(examPaperService.page(page, size));
    }

    @Operation(summary = "试卷详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('exam:paper:read')")
    public ApiResponse<ExamPaperVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(examPaperService.getDetail(id));
    }

    @Operation(summary = "更新试卷题目")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('exam:paper:manage')")
    public ApiResponse<ExamPaperVO> update(@PathVariable Long id, @Valid @RequestBody ExamPaperUpdateRequest request) {
        return ApiResponse.ok(examPaperService.update(id, request));
    }

    @Operation(summary = "发布试卷")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('exam:paper:manage')")
    public ApiResponse<ExamPaperVO> publish(@PathVariable Long id) {
        return ApiResponse.ok(examPaperService.publish(id));
    }

    @Operation(summary = "删除试卷")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('exam:paper:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        examPaperService.delete(id);
        return ApiResponse.ok(null);
    }
}
