package com.optimisante.backend.domain.training.dto;

import com.optimisante.backend.domain.training.entity.ServiceOptionStatus;
import com.optimisante.backend.domain.training.entity.ServiceOptionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Charges utiles des services souscrits autour d'une formation.
 *
 * <p>Regroupees dans un seul fichier, comme {@link DocumentRequestDtos} et
 * {@link InterviewDtos} : elles decrivent un meme parcours et se lisent mieux d'un bloc.</p>
 */
public final class ServiceOptionDtos {

    private ServiceOptionDtos() {}

    // ------------------------------------------------------------------ Catalogue admin ----

    /** Ce que l'administration de la mobilite depose au catalogue d'une formation. */
    @Data
    public static class CatalogOptionRequest {
        @NotNull(message = "Précisez le type de service.")
        private ServiceOptionType optionType;

        @NotBlank(message = "Décrivez le service : c'est ce que le médecin lira.")
        @Size(max = 255)
        private String label;

        @Size(max = 4000)
        private String description;

        /**
         * Zero est accepte : un service peut etre offert sur une formation donnee. Ce n'est pas
         * la meme chose que de ne pas le proposer du tout.
         */
        @NotNull(message = "Le tarif est obligatoire.")
        @DecimalMin(value = "0.00", message = "Le tarif ne peut pas être négatif.")
        private BigDecimal price;
    }

    /** Une offre du catalogue. */
    @Data
    @Builder
    public static class CatalogOptionView {
        private UUID id;
        private UUID trainingId;
        private ServiceOptionType optionType;
        private String label;
        private String description;
        private BigDecimal price;
        private boolean active;
        private OffsetDateTime createdAt;
    }

    // -------------------------------------------------------------------- Cote medecin ----

    /** Les services que le medecin retient. */
    @Data
    public static class SelectOptionsRequest {
        @NotEmpty(message = "Choisissez au moins un service.")
        private List<UUID> catalogOptionIds;
    }

    /** Un service souscrit sur un dossier. */
    @Data
    @Builder
    public static class SubscriptionView {
        private UUID id;
        private ServiceOptionType optionType;
        private String label;
        private BigDecimal unitPrice;
        private ServiceOptionStatus status;
        private OffsetDateTime selectedAt;
        private OffsetDateTime paidAt;
    }

    /** Etat des services d'un dossier : ce qui est offert, ce qui est retenu, ce qui reste dû. */
    @Data
    @Builder
    public static class ServiceSummary {
        /** Offres encore disponibles, celles deja souscrites exclues. */
        private List<CatalogOptionView> available;
        private List<SubscriptionView> subscriptions;

        /** Somme des services retenus mais non encore regles. */
        private BigDecimal amountDue;
        /** Somme des services deja regles. */
        private BigDecimal amountPaid;

        /** L'attestation deposee au coffre au dernier reglement, si elle existe. */
        private UUID subscriptionDocumentId;
    }

    /** Ouverture du paiement : ce dont le formulaire Stripe a besoin. */
    @Data
    @Builder
    public static class ServiceCheckoutView {
        private String clientSecret;
        private BigDecimal amount;
        private String currency;
    }
}
