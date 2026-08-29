package com.optimisante.backend.domain.doctorapplication.dto;

import com.optimisante.backend.domain.doctorapplication.entity.DoctorApplicationStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class DoctorApplicationResponseDto {
    private UUID id;
    private DoctorApplicationStatus status;
    private BigDecimal feeAmount;
    private String trainingTitle;
    private OffsetDateTime createdAt;
    private OffsetDateTime paidAt;

    /** Uniquement renseigné à la création : client_secret pour initialiser le Payment Element intégré (ui_mode "elements"). */
    private String clientSecret;
}
