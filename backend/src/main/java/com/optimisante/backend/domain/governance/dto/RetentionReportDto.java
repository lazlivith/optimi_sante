package com.optimisante.backend.domain.governance.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Photographie de la rétention des données personnelles pour la gouvernance : comptes inactifs
 * de longue date, leads anciens, comptes déjà anonymisés, consentements manquants.
 */
@Getter
@Builder
public class RetentionReportDto {

    private int staleThresholdMonths;
    private long staleActiveUsers;
    private long oldLeads;
    private long anonymizedUsers;
    private long usersWithoutConsent;
    private List<Candidate> staleUserSample;

    @Getter
    @Builder
    public static class Candidate {
        private String userId;
        private String email;
        private String role;
        private String createdAt;
        private long ordersCount;
        private long enrollmentsCount;
    }
}
