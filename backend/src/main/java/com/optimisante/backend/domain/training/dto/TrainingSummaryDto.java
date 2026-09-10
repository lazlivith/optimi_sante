package com.optimisante.backend.domain.training.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class TrainingSummaryDto {
    private UUID id;
    private String title;
    private String medicalSpecialty;
    private String description;
    private Integer durationDays;
    private Boolean isLongStay;
    private String location;
    private String brochureUrl;
    private String imageUrl;
    private String videoUrl;
    private BigDecimal price;

    /**
     * Frais de dossier reellement applicables a cette formation, repli global deja resolu.
     *
     * <p>Le repli est calcule cote serveur, pas laisse au client : deux interfaces qui
     * dupliqueraient la regle finiraient par afficher deux montants differents — et l'un des
     * deux ne serait pas celui preleve.</p>
     */
    private BigDecimal applicationFee;
}
