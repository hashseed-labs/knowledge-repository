package com.knowledge.kb.exam.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
public class ExamGenerateRequest {
    @NotBlank
    private String title;

    @NotEmpty
    private List<Long> docIds;

    @Min(0)
    @Max(50)
    private int singleCount = 3;

    @Min(0)
    @Max(50)
    private int multiCount = 1;

    @Min(0)
    @Max(50)
    private int judgeCount = 2;

    @Min(0)
    @Max(50)
    private int shortCount = 1;

    /** easy | medium | hard */
    private String difficulty = "medium";

    /** 考试时长（分钟），发布后开考使用 */
    @Min(1)
    @Max(600)
    private int durationMinutes = 60;
}
