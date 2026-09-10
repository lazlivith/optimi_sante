package com.optimisante.backend.domain.identity.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Données personnelles d'un utilisateur, telles qu'il a le droit de les consulter et de les
 * récupérer (RGPD, articles 15 et 20).
 *
 * <p>Rassemble ce que la plateforme détient <b>sur lui</b>, y compris ce qu'il n'a pas saisi
 * lui-même : les emails qui lui ont été envoyés en font partie. Un export qui ne rendrait que
 * les champs du formulaire d'inscription ne répondrait pas à la demande.</p>
 *
 * <p>Ce qui en est <b>délibérément absent</b> : l'empreinte du mot de passe. Ce n'est pas une
 * donnée que l'utilisateur peut exploiter, et la restituer reviendrait à en multiplier les
 * copies hors du système.</p>
 */
@Data
@Builder
public class PersonalDataDto {

    private Identity identity;
    private Company company;
    private List<OrderLine> orders;
    private List<EmailLine> emails;
    private int leadCount;

    /** Date de génération : un export daté vaut mieux qu'un fichier dont on ignore l'âge. */
    private OffsetDateTime generatedAt;

    @Data
    @Builder
    public static class Identity {
        private String email;
        private String firstName;
        private String lastName;
        private String phone;
        private String role;
        private OffsetDateTime createdAt;
        /** Identifiant client chez le prestataire de paiement — pseudonyme, mais bien une
         *  donnée le concernant. */
        private String paymentProviderId;
    }

    /** Renseigné pour un compte professionnel uniquement. */
    @Data
    @Builder
    public static class Company {
        private String companyName;
        private String taxId;
        private String vatNumber;
        private String billingAddress;
        private String country;
        private String facilityType;
        private String contactName;
        private BigDecimal b2bDiscountRate;
    }

    @Data
    @Builder
    public static class OrderLine {
        private String orderNumber;
        private OffsetDateTime createdAt;
        private String status;
        private String paymentStatus;
        private String paymentMethod;
        private Boolean isQuote;
        private BigDecimal totalAmount;
        private int itemCount;
    }

    @Data
    @Builder
    public static class EmailLine {
        private String type;
        private String subject;
        private String status;
        private OffsetDateTime sentAt;
    }
}
