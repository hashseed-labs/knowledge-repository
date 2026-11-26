package com.knowledge.kb.controller;

import com.knowledge.kb.common.ApiResponse;
import com.knowledge.kb.dto.PageResult;
import com.knowledge.kb.service.TenantAdminService;
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

import java.util.List;
import java.util.Map;

@Tag(name = "组织管理")
@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;

    @Operation(summary = "组织分页")
    @GetMapping
    @PreAuthorize("hasAuthority('tenant:org:manage')")
    public ApiResponse<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(tenantAdminService.page(page, size, keyword));
    }

    @Operation(summary = "组织列表")
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('tenant:org:manage')")
    public ApiResponse<List<Map<String, Object>>> all() {
        return ApiResponse.ok(tenantAdminService.listAll());
    }

    @Operation(summary = "新建组织")
    @PostMapping
    @PreAuthorize("hasAuthority('tenant:org:manage')")
    public ApiResponse<Long> create(@Valid @RequestBody TenantAdminService.TenantCreateReq req) {
        return ApiResponse.ok(tenantAdminService.create(req));
    }

    @Operation(summary = "更新组织")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:org:manage')")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @RequestBody TenantAdminService.TenantUpdateReq req) {
        tenantAdminService.update(id, req);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "删除组织（软删除为 DISABLED）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:org:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tenantAdminService.delete(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "组织成员列表")
    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('tenant:org:manage')")
    public ApiResponse<List<Map<String, Object>>> members(@PathVariable Long id) {
        return ApiResponse.ok(tenantAdminService.listMembers(id));
    }

    @Operation(summary = "可加入组织的候选用户")
    @GetMapping("/{id}/member-candidates")
    @PreAuthorize("hasAuthority('tenant:org:manage')")
    public ApiResponse<List<Map<String, Object>>> candidates(
            @PathVariable Long id,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(tenantAdminService.listCandidates(id, keyword));
    }

    @Operation(summary = "添加组织成员")
    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('tenant:org:manage')")
    public ApiResponse<Void> addMember(@PathVariable Long id,
                                       @Valid @RequestBody TenantAdminService.TenantMemberReq req) {
        tenantAdminService.addMember(id, req);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "更新组织成员角色")
    @PutMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAuthority('tenant:org:manage')")
    public ApiResponse<Void> updateMember(@PathVariable Long id,
                                          @PathVariable Long userId,
                                          @RequestBody TenantAdminService.TenantMemberRolesReq req) {
        tenantAdminService.updateMemberRoles(id, userId, req);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "移除组织成员")
    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAuthority('tenant:org:manage')")
    public ApiResponse<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        tenantAdminService.removeMember(id, userId);
        return ApiResponse.ok(null);
    }
}
