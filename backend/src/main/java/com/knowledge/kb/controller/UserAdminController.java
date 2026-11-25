package com.knowledge.kb.controller;

import com.knowledge.kb.common.ApiResponse;
import com.knowledge.kb.dto.PageResult;
import com.knowledge.kb.service.UserAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.Map;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    @PreAuthorize("hasAuthority('tenant:user:manage')")
    public ApiResponse<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(userAdminService.page(page, size, keyword));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('tenant:user:manage')")
    public ApiResponse<Long> create(@RequestBody UserAdminService.UserCreateReq req) {
        return ApiResponse.ok(userAdminService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:user:manage')")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UserAdminService.UserUpdateReq req) {
        userAdminService.update(id, req);
        return ApiResponse.ok(null);
    }
}
