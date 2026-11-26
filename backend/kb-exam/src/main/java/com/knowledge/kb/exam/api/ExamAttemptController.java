package com.knowledge.kb.exam.api;

import com.knowledge.kb.common.ApiResponse;
import com.knowledge.kb.dto.PageResult;
import com.knowledge.kb.exam.api.dto.ExamAttemptVO;
import com.knowledge.kb.exam.api.dto.ExamSaveAnswersRequest;
import com.knowledge.kb.exam.application.ExamAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "在线考试-作答")
@RestController
@RequestMapping("/api/exam")
@RequiredArgsConstructor
public class ExamAttemptController {

    private final ExamAttemptService examAttemptService;

    @Operation(summary = "开始考试（已有进行中记录则恢复）")
    @PostMapping("/papers/{paperId}/attempts")
    @PreAuthorize("hasAuthority('exam:attempt:take')")
    public ApiResponse<ExamAttemptVO> start(@PathVariable Long paperId) {
        return ApiResponse.ok(examAttemptService.start(paperId));
    }

    @Operation(summary = "我的考试记录")
    @GetMapping("/attempts/mine")
    @PreAuthorize("hasAuthority('exam:attempt:read')")
    public ApiResponse<PageResult<ExamAttemptVO>> mine(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(examAttemptService.myAttempts(page, size));
    }

    @Operation(summary = "考试详情/继续作答")
    @GetMapping("/attempts/{id}")
    @PreAuthorize("hasAuthority('exam:attempt:take') or hasAuthority('exam:attempt:read')")
    public ApiResponse<ExamAttemptVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(examAttemptService.getDetail(id));
    }

    @Operation(summary = "保存答案")
    @PutMapping("/attempts/{id}/answers")
    @PreAuthorize("hasAuthority('exam:attempt:take')")
    public ApiResponse<ExamAttemptVO> saveAnswers(@PathVariable Long id,
                                                  @Valid @RequestBody ExamSaveAnswersRequest request) {
        return ApiResponse.ok(examAttemptService.saveAnswers(id, request));
    }

    @Operation(summary = "交卷并自动批改客观题")
    @PostMapping("/attempts/{id}/submit")
    @PreAuthorize("hasAuthority('exam:attempt:take')")
    public ApiResponse<ExamAttemptVO> submit(@PathVariable Long id,
                                             @RequestBody(required = false) ExamSaveAnswersRequest request) {
        return ApiResponse.ok(examAttemptService.submit(id, request));
    }
}
