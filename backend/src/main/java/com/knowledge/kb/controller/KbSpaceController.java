package com.knowledge.kb.controller;

import com.knowledge.kb.common.ApiResponse;
import com.knowledge.kb.service.KbSpaceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "知识库")
@RestController
@RequestMapping("/api/kb/spaces")
@RequiredArgsConstructor
public class KbSpaceController {

    private final KbSpaceService kbSpaceService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(kbSpaceService.listAccessible());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('kb:space:manage')")
    public ApiResponse<Long> create(@RequestBody KbSpaceService.KbSaveReq req) {
        return ApiResponse.ok(kbSpaceService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('kb:space:manage')")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody KbSpaceService.KbSaveReq req) {
        kbSpaceService.update(id, req);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('kb:member:manage')")
    public ApiResponse<List<Map<String, Object>>> members(@PathVariable Long id) {
        return ApiResponse.ok(kbSpaceService.listMembers(id));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('kb:member:manage')")
    public ApiResponse<Void> addMember(@PathVariable Long id, @RequestBody KbSpaceService.MemberReq req) {
        kbSpaceService.addMember(id, req);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAuthority('kb:member:manage')")
    public ApiResponse<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        kbSpaceService.removeMember(id, userId);
        return ApiResponse.ok(null);
    }
}
