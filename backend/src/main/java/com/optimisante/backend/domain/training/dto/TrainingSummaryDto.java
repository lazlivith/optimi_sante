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

    /**
     * Le centre qui dispense la formation, et l'adresse de ses locaux.
     *
     * <p>Rien d'autre du profil partenaire ne sort ici : ni le contact, ni le telephone, ni
     * le numero d'agrement. Un candidat a besoin de savoir ou se rendre, pas de joindre
     * directement le centre — les echanges passent par la plateforme.</p>
     */
    private String centerName;
    private String centerAddress;
}
