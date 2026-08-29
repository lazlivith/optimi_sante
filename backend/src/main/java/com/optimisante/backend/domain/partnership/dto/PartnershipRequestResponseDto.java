package com.optimisante.backend.domain.partnership.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class PartnershipRequestResponseDto {
    private UUID id;
    private String institutionName;
    private String finessAccreditation;
    private String contactPersonName;
    private String contactEmail;
    private String contactPhone;
    private String address;
    private String conventionFileKey;
    private String status;
    private String rejectionReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime reviewedAt;
}
