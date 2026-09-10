package com.optimisante.backend.domain.training.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class EnrollmentDetailDto {
    private UUID id;
    private String status;
    private String trainingTitle;
    private String doctorName;
    private String doctorEmail;
    private OffsetDateTime submittedAt;
    private String diplomaUrl;
    private String medicalBoardRegistrationUrl;
    private String passportUrl;
    private String conventionS3Key;
    private String attestationS3Key;

    // ---- Ajouts pour l'écran de suivi et de paiement du médecin ---------------------

    /** Établissement d'accueil (lieu de la session) — texte libre, pas une identité. */
    private String hostInstitution;

    /**
     * CHU partenaire dont relève la formation, remonté depuis
     * {@code session → training → partnerProfile}.
     *
     * <p>Distinct de {@link #hostInstitution}, qui n'est qu'un lieu saisi librement sur la
     * session (« Amphithéâtre B »). Filtrer les dossiers par établissement demande une
     * identité stable, pas un libellé : d'où l'identifiant, que l'interface utilise pour le
     * filtre, et le nom, qu'elle affiche.</p>
     */
    private UUID partnerProfileId;

    private String partnerName;

    /** Montant des frais de formation restant à régler, en euros. */
    private java.math.BigDecimal tuitionAmount;

    /**
     * Echeancier des frais de formation : acompte a l'admission, solde a la delivrance du visa.
     *
     * <p>Les trois montants sont envoyes ensemble pour que l'ecran n'ait aucun calcul a refaire.
     * Recalculer un pourcentage cote client ferait apparaitre, sur certains montants, un centime
     * d'ecart avec ce que la caisse prelevera reellement.</p>
     */
    private java.math.BigDecimal tuitionDepositAmount;
    private java.math.BigDecimal tuitionBalanceAmount;
    /** Part exigee a l'admission, en pourcentage. Alimente les libelles (« acompte de 60 % »). */
    private java.math.BigDecimal tuitionDepositRate;

    /** Ce qui reste du au titre de la formation, toutes echeances confondues. */
    private java.math.BigDecimal tuitionOutstanding;

    /**
     * Pièce ou correction réclamée. Renseignée en ACTION_REQUIRED — sans elle, le médecin
     * verrait son dossier bloqué sans savoir ce qu'on attend de lui.
     */
    private String actionRequiredNote;

    /** Motif de refus ou d'annulation, pour que la décision soit expliquée et non subie. */
    private String rejectionReason;
}
