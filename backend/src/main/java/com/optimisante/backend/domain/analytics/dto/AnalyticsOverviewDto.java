package com.optimisante.backend.domain.analytics.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** Indicateurs clés de tête de tableau de bord analytique (une seule requête agrégée). */
@Getter
@Builder
public class AnalyticsOverviewDto {

    private BigDecimal revenueTotal;
    private BigDecimal revenueThisMonth;
    private BigDecimal averageOrderValue;
    private long ordersPaid;
    private long quotesTotal;
    private long payingCustomers;
    private long activeUsers;
    private long newUsers30d;
    private long leads30d;
    private long paidApplications;
    private long enrollmentsTotal;
    private long enrollmentsActive;
    /** Taux de conversion candidature payée -> inscription (%) sur la totalité de l'historique. */
    private BigDecimal applicationToEnrollmentRate;
}
