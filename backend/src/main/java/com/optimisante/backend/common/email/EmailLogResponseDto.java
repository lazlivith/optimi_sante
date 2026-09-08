package com.optimisante.backend.common.email;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class EmailLogResponseDto {
    private UUID id;
    private String recipient;
    private String subject;
    private String emailType;
    private String status;
    private String errorMessage;
    private ZonedDateTime sentAt;
    /** Vrai si le compte destinataire est encore connu : condition d'un renvoi possible. */
    private boolean canResend;
    private String recipientRole;
}
