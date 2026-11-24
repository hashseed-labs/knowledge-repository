package com.knowledge.kb.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class HealthVO {
    private boolean healthy;
    private Map<String, Boolean> dependencies;
}
