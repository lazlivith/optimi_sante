package com.optimisante.backend.domain.training.dto;

import com.optimisante.backend.domain.training.entity.InterviewScheduleStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Charges utiles de la planification d'entretien.
 *
 * <p>Regroupees dans un seul fichier, comme {@link DocumentRequestDtos} : elles decrivent un
 * meme echange a trois voix et se lisent mieux d'un bloc.</p>
 */
public final class InterviewDtos {

    private InterviewDtos() {}

    /** Un creneau, tel que le CHU le propose. */
    @Data
    public static class SlotRequest {
        @NotNull(message = "Le debut du creneau est obligatoire.")
        private OffsetDateTime startsAt;

        @NotNull(message = "La fin du creneau est obligatoire.")
        private OffsetDateTime endsAt;
    }

    /** Ce que le CHU depose : le lien de la reunion en ligne et les creneaux qu'il ouvre. */
    @Data
    public static class ProposeRequest {
        /** Lien Teams, Meet ou Zoom. Sans lui, le rendez-vous est injoignable. */
        @NotBlank(message = "Indiquez le lien de la réunion en ligne (Teams, Meet, Zoom…).")
        @Size(max = 512)
        private String meetingLink;

        @Size(max = 4000)
        private String partnerNote;

        /**
         * Proposer un seul creneau n'est pas un choix : c'est une convocation deguisee, sans
         * que le medecin puisse dire qu'il n'est pas disponible. Deux au minimum.
         */
        @NotEmpty(message = "Proposez au moins deux creneaux.")
        @Valid
        private List<SlotRequest> slots;
    }

    /** Ce qu'OptimiSante ajoute en transmettant les creneaux au medecin. */
    @Data
    public static class TransmitRequest {
        @Size(max = 4000)
        private String adminNote;
    }

    /** Le creneau retenu par le medecin. */
    @Data
    public static class ConfirmRequest {
        @NotNull(message = "Indiquez le creneau retenu.")
        private UUID slotId;
    }

    /** Motif d'abandon de la planification. */
    @Data
    public static class CancelRequest {
        @NotNull(message = "Un motif est obligatoire pour annuler un entretien.")
        @Size(min = 3, max = 2000)
        private String reason;
    }

    @Data
    @Builder
    public static class SlotView {
        private UUID id;
        private OffsetDateTime startsAt;
        private OffsetDateTime endsAt;
        /** Vrai pour le creneau que le medecin a retenu. */
        private boolean confirmed;
    }

    /** Un entretien, tel que le voient les trois acteurs. */
    @Data
    @Builder
    public static class ScheduleView {
        private UUID id;
        private UUID enrollmentId;
        private InterviewScheduleStatus status;
        private String meetingLink;
        private String partnerNote;
        private String adminNote;
        private OffsetDateTime proposedAt;
        private OffsetDateTime transmittedAt;
        private OffsetDateTime confirmedAt;
        private String cancelledReason;
        private List<SlotView> slots;

        /** Contexte du dossier, pour que les ecrans n'aient pas a le recharger separement. */
        private String doctorName;
        private String doctorEmail;
        private String trainingTitle;
        private String partnerInstitutionName;

        /** Convocation deposee au coffre, disponible des la confirmation. */
        private UUID convocationDocumentId;
    }
}
