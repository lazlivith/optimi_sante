package com.optimisante.backend.domain.training.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class EnrollmentResponseDto {
    private UUID id;
    private String status;
    /** INTERNATIONAL_VISA ou LOCAL_FRANCE : l'interface en deduit le parcours a afficher. */
    private String registrationType;
    private String rppsNumber;
    // DP-4 : les pièces du candidat (diplôme, passeport, inscription à l'ordre) ne sont plus
    // renvoyées comme clés Cloudinary. Elles se consultent par le coffre-fort, après contrôle
    // de rôle. Cette réponse ne porte plus de public_id.
    private OffsetDateTime submittedAt;
}
