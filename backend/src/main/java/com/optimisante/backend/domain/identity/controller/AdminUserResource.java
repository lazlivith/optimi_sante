package com.optimisante.backend.domain.identity.controller;

import com.optimisante.backend.domain.identity.dto.AdminUserSummaryDto;
import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.identity.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminUserResource {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<Page<AdminUserSummaryDto>> listUsers(
            @RequestParam(required = false) Role role,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminUserService.listUsers(role, pageable));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminUserSummaryDto> setUserActive(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(adminUserService.setUserActive(id, active));
    }
}
