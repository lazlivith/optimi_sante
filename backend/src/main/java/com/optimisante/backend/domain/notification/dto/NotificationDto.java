package com.optimisante.backend.domain.notification.dto;

import com.optimisante.backend.domain.notification.entity.Notification;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class NotificationDto {

    private final UUID id;
    private final String type;
    private final String severity;
    private final String title;
    private final String body;
    private final String linkUrl;
    private final boolean read;
    private final boolean acknowledged;
    private final int groupCount;
    private final OffsetDateTime snoozedUntil;
    private final OffsetDateTime createdAt;

    public static NotificationDto from(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .type(n.getType())
                .severity(n.getSeverity() == null ? "INFO" : n.getSeverity().name())
                .title(n.getTitle())
                .body(n.getBody())
                .linkUrl(n.getLinkUrl())
                .read(n.getReadAt() != null)
                .acknowledged(n.getAcknowledgedAt() != null)
                .groupCount(n.getGroupCount() == null ? 1 : n.getGroupCount())
                .snoozedUntil(n.getSnoozedUntil())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
