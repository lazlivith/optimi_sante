package com.optimisante.backend.domain.analytics.service;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.analytics.dto.*;
import com.optimisante.backend.domain.analytics.repository.AnalyticsRepository;
import com.optimisante.backend.domain.analytics.repository.AnalyticsRepository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reporting analytique du Dashboard Governance. Lecture seule, cloisonné par tenant, sans aucun
 * effet de bord sur le domaine métier — il ne fait qu'agréger l'existant (commandes, formations,
 * inscriptions, leads, candidatures) pour la direction.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int MAX_DAYS = 365;
    private static final int MIN_DAYS = 7;

    private final AnalyticsRepository analyticsRepository;

    @Transactional(readOnly = true)
    public AnalyticsOverviewDto overview() {
        OverviewRow r = analyticsRepository.overview(requireTenantId());

        BigDecimal revenueTotal = nz(r.getRevenueTotal());
        long ordersPaid = nz(r.getOrdersPaid());
        BigDecimal aov = ordersPaid > 0
                ? revenueTotal.divide(BigDecimal.valueOf(ordersPaid), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long paidApplications = nz(r.getPaidApplications());
        long enrollmentsTotal = nz(r.getEnrollmentsTotal());
        BigDecimal appToEnroll = paidApplications > 0
                ? BigDecimal.valueOf(enrollmentsTotal * 100.0 / paidApplications).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return AnalyticsOverviewDto.builder()
                .revenueTotal(revenueTotal)
                .revenueThisMonth(nz(r.getRevenueMonth()))
                .averageOrderValue(aov)
                .ordersPaid(ordersPaid)
                .quotesTotal(nz(r.getQuotesTotal()))
                .payingCustomers(nz(r.getPayingCustomers()))
                .activeUsers(nz(r.getActiveUsers()))
                .newUsers30d(nz(r.getNewUsers30d()))
                .leads30d(nz(r.getLeads30d()))
                .paidApplications(paidApplications)
                .enrollmentsTotal(enrollmentsTotal)
                .enrollmentsActive(nz(r.getEnrollmentsActive()))
                .applicationToEnrollmentRate(appToEnroll)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TimeseriesPointDto> revenueTimeseries(int days) {
        return analyticsRepository.revenueTimeseries(requireTenantId(), clampDays(days)).stream()
                .map(row -> TimeseriesPointDto.builder()
                        .day(row.getDay())
                        .revenue(nz(row.getRevenue()))
                        .count(nz(row.getOrders()))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TimeseriesPointDto> userGrowthTimeseries(int days) {
        return analyticsRepository.userGrowthTimeseries(requireTenantId(), clampDays(days)).stream()
                .map(row -> TimeseriesPointDto.builder()
                        .day(row.getDay())
                        .revenue(BigDecimal.ZERO)
                        .count(nz(row.getOrders()))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LabelValueDto> salesByCategory() {
        return map(analyticsRepository.salesByCategory(requireTenantId()));
    }

    @Transactional(readOnly = true)
    public List<LabelValueDto> enrollmentsByStatus() {
        return map(analyticsRepository.enrollmentsByStatus(requireTenantId()));
    }

    @Transactional(readOnly = true)
    public List<LabelValueDto> topTrainings(int limit) {
        int safe = Math.min(Math.max(limit, 1), 50);
        return map(analyticsRepository.topTrainings(requireTenantId(), safe));
    }

    @Transactional(readOnly = true)
    public List<LabelValueDto> revenueBySegment() {
        return map(analyticsRepository.revenueBySegment(requireTenantId()));
    }

    @Transactional(readOnly = true)
    public FunnelDto acquisitionFunnel() {
        FunnelRow r = analyticsRepository.acquisitionFunnel(requireTenantId());
        List<FunnelDto.Step> steps = new ArrayList<>();
        long top = Math.max(nz(r.getLeads()), 1);
        steps.add(step("Brochures téléchargées", nz(r.getLeads()), top));
        steps.add(step("Candidatures créées", nz(r.getApplications()), top));
        steps.add(step("Candidatures payées", nz(r.getApplicationsPaid()), top));
        steps.add(step("Inscriptions", nz(r.getEnrollments()), top));
        steps.add(step("Conventions émises", nz(r.getConventionsIssued()), top));
        steps.add(step("Visas obtenus", nz(r.getVisasGranted()), top));
        return FunnelDto.builder().steps(steps).build();
    }

    private static FunnelDto.Step step(String label, long count, long top) {
        return FunnelDto.Step.builder()
                .label(label)
                .count(count)
                .shareOfTop(Math.round(count * 1000.0 / top) / 10.0)
                .build();
    }

    private static List<LabelValueDto> map(List<LabelValueRow> rows) {
        return rows.stream()
                .map(row -> LabelValueDto.builder()
                        .label(row.getLabel())
                        .value(nz(row.getValue()))
                        .secondary(nz(row.getSecondary()))
                        .build())
                .toList();
    }

    private int clampDays(int days) {
        return Math.min(Math.max(days, MIN_DAYS), MAX_DAYS);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static long nz(Long v) {
        return v != null ? v : 0L;
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required");
        }
        return tenantId;
    }
}
