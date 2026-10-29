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

    /*
     * Aucun champ de brochure ici, volontairement.
     *
     * Ce dto est celui de GET /api/v1/trainings, qui est en permitAll. Il portait
     * `brochureUrl`, un lien vers la brochure D'ORIGINE du partenaire : coordonnees du CHU
     * comprises, et accessible sans compte ni formulaire. Le champ est retire plutot que mis
     * a null, pour qu'aucun appel a un constructeur ne le remplisse de nouveau sans qu'on y
     * pense. La brochure reeditee par Optimi Sante aura son propre champ, quand elle existera.
     */
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
