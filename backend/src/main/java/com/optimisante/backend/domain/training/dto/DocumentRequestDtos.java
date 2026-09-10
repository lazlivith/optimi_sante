package com.optimisante.backend.domain.training.dto;

import com.optimisante.backend.domain.training.entity.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Charges utiles de l'espace « pieces du dossier ».
 *
 * <p>Regroupees dans un seul fichier parce qu'elles n'ont de sens qu'ensemble : les separer en
 * cinq fichiers de dix lignes disperserait un contrat qui se lit d'un bloc.</p>
 */
public final class DocumentRequestDtos {

    private DocumentRequestDtos() {}

    /** Ce que l'administration envoie pour reclamer une piece. */
    @Data
    public static class CreateRequest {
        @NotNull(message = "Le type de document est obligatoire.")
        private DocumentType documentType;

        /**
         * Ce que le medecin lira. Obligatoire meme quand le type est explicite : « PASSPORT »
         * ne dit pas s'il faut la page d'identite ou le document entier.
         */
        @NotBlank(message = "Precisez la piece attendue : c'est ce que le medecin lira.")
        @Size(max = 255)
        private String label;

        @Size(max = 4000)
        private String instructions;

        private LocalDate dueDate;
    }

    /** Decision de l'administration sur une piece deposee. */
    @Data
    public static class ReviewRequest {
        @NotNull
        private Boolean accepted;

        /** Obligatoire en cas de refus : un refus sans motif est un blocage sans issue. */
        @Size(max = 2000)
        private String rejectionReason;
    }

    /** Une demande, telle que la voient l'administration et le medecin. */
    @Data
    @Builder
    public static class RequestView {
        private UUID id;
        private String documentType;
        private String label;
        private String instructions;
        private String status;
        private LocalDate dueDate;
        private OffsetDateTime requestedAt;
        private OffsetDateTime submittedAt;
        private OffsetDateTime reviewedAt;
        private String rejectionReason;

        /** Identifiant de la piece deposee, {@code null} tant que rien ne l'a satisfaite. */
        private UUID documentId;

        /** Vrai tant que la demande appelle une action de l'un ou de l'autre. */
        private boolean open;
    }

    /**
     * Etat d'avancement du dossier de pieces.
     *
     * <p>Repond a la seule question que se posent le medecin comme l'administration :
     * « que reste-t-il a fournir ? ». Sans ce recapitulatif, il faut compter les lignes.</p>
     */
    @Data
    @Builder
    public static class DossierSummary {
        private int total;
        private int accepted;
        private int awaitingDoctor;   // PENDING + REJECTED : la balle est chez le medecin
        private int awaitingReview;   // SUBMITTED : la balle est chez l'administration
        private boolean complete;
        private List<RequestView> requests;
    }
}
