package com.knowledge.kb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Builder
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresInSeconds;
    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private Long id;
        private String username;
        private String displayName;
        private Long tenantId;
        private String tenantName;
        private List<TenantItem> tenants;
        private Set<String> permissions;
        private Set<String> roles;
    }

    @Data
    @Builder
    public static class TenantItem {
        private Long id;
        private String code;
        private String name;
        @JsonProperty("isDefault")
        private boolean isDefault;
    }
}
