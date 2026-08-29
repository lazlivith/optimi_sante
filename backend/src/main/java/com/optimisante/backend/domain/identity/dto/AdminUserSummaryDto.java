package com.optimisante.backend.domain.identity.dto;

import com.optimisante.backend.domain.identity.entity.Role;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class AdminUserSummaryDto {
    private UUID id;
    private String email;
    private Role role;
    private Boolean isActive;
    private String displayName;
    private ZonedDateTime createdAt;
}
