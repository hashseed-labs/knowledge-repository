package com.knowledge.kb.controller;

import com.knowledge.kb.common.ApiResponse;
import com.knowledge.kb.entity.SysPermission;
import com.knowledge.kb.service.RoleAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "角色权限")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class RoleAdminController {

    private final RoleAdminService roleAdminService;

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('tenant:role:manage')")
    public ApiResponse<List<Map<String, Object>>> roles() {
        return ApiResponse.ok(roleAdminService.listRoles());
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('tenant:role:manage')")
    public ApiResponse<List<SysPermission>> permissions() {
        return ApiResponse.ok(roleAdminService.listPermissions());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('tenant:role:manage')")
    public ApiResponse<Long> create(@RequestBody RoleAdminService.RoleSaveReq req) {
        return ApiResponse.ok(roleAdminService.create(req));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('tenant:role:manage')")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody RoleAdminService.RoleSaveReq req) {
        roleAdminService.update(id, req);
        return ApiResponse.ok(null);
    }
}
