package com.optimisante.backend.domain.audit.dto;

import com.optimisante.backend.domain.audit.entity.AuditLog;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class AuditLogDto {

    private UUID id;
    private String actorEmail;
    private String actorRole;
    private String action;
    private String entityType;
    private String entityId;
    private String httpMethod;
    private String path;
    private Integer statusCode;
    private String ipAddress;
    private String summary;
    private String metadata;
    private OffsetDateTime createdAt;

    public static AuditLogDto from(AuditLog a) {
        return AuditLogDto.builder()
                .id(a.getId())
                .actorEmail(a.getActorEmail())
                .actorRole(a.getActorRole())
                .action(a.getAction())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .httpMethod(a.getHttpMethod())
                .path(a.getPath())
                .statusCode(a.getStatusCode())
                .ipAddress(a.getIpAddress())
                .summary(a.getSummary())
                .metadata(a.getMetadata())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
