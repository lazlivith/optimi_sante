package com.optimisante.backend.domain.analytics.controller;

import com.optimisante.backend.config.security.PlatformAdmin;

import com.optimisante.backend.common.export.CsvWriter;
import com.optimisante.backend.domain.analytics.dto.FunnelDto;
import com.optimisante.backend.domain.analytics.dto.LabelValueDto;
import com.optimisante.backend.domain.analytics.dto.TimeseriesPointDto;
import com.optimisante.backend.domain.analytics.dto.AnalyticsOverviewDto;
import com.optimisante.backend.domain.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Endpoints du tableau de bord analytique (Dashboard Governance). Lecture accessible à toute
 * l'administration. Chaque jeu de données est aussi exportable en CSV via /export.
 */
/**
 * Perimetre d'acces : supervision (SUPER_ADMIN, ADMIN herite).
 *
 * <p>Transverse : l'ecran melange chiffre d'affaires (negoce) et entonnoir de candidatures
 * (mobilite). L'ouvrir a un admin de perimetre lui montrerait les donnees de l'autre metier —
 * ce que l'etancheite interdit. Reserve a la supervision tant que les deux pipelines ne sont
 * pas cloisonnes a la source.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
@PlatformAdmin
public class AdminAnalyticsResource {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    public ResponseEntity<AnalyticsOverviewDto> overview() {
        return ResponseEntity.ok(analyticsService.overview());
    }

    @GetMapping("/revenue-timeseries")
    public ResponseEntity<List<TimeseriesPointDto>> revenueTimeseries(@RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(analyticsService.revenueTimeseries(days));
    }

    @GetMapping("/user-growth")
    public ResponseEntity<List<TimeseriesPointDto>> userGrowth(@RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(analyticsService.userGrowthTimeseries(days));
    }

    @GetMapping("/sales-by-category")
    public ResponseEntity<List<LabelValueDto>> salesByCategory() {
        return ResponseEntity.ok(analyticsService.salesByCategory());
    }

    @GetMapping("/enrollments-by-status")
    public ResponseEntity<List<LabelValueDto>> enrollmentsByStatus() {
        return ResponseEntity.ok(analyticsService.enrollmentsByStatus());
    }

    @GetMapping("/top-trainings")
    public ResponseEntity<List<LabelValueDto>> topTrainings(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.topTrainings(limit));
    }

    @GetMapping("/segments")
    public ResponseEntity<List<LabelValueDto>> segments() {
        return ResponseEntity.ok(analyticsService.revenueBySegment());
    }

    @GetMapping("/acquisition-funnel")
    public ResponseEntity<FunnelDto> acquisitionFunnel() {
        return ResponseEntity.ok(analyticsService.acquisitionFunnel());
    }

    /**
     * Export CSV d'un jeu de données analytique.
     * dataset ∈ { revenue-timeseries, user-growth, sales-by-category, enrollments-by-status,
     *             top-trainings, segments, acquisition-funnel }
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String dataset,
                                         @RequestParam(defaultValue = "90") int days,
                                         @RequestParam(defaultValue = "10") int limit) {
        String csv;
        String filename;
        switch (dataset) {
            case "revenue-timeseries" -> {
                csv = timeseriesCsv(analyticsService.revenueTimeseries(days), "Revenu (€)", "Commandes");
                filename = "revenu-quotidien.csv";
            }
            case "user-growth" -> {
                // La serie de croissance ne porte aucun revenu : sa premiere colonne vaut
                // structurellement zero. L'en-tete « Revenu (€) » etait recopie de l'export
                // ci-dessus et annoncait une donnee qui n'existe pas.
                csv = timeseriesCsv(analyticsService.userGrowthTimeseries(days), "", "Nouveaux comptes");
                filename = "croissance-comptes.csv";
            }
            case "sales-by-category" -> {
                csv = labelValueCsv(analyticsService.salesByCategory(), "Catégorie", "Revenu (€)", "Quantité");
                filename = "ventes-par-categorie.csv";
            }
            case "enrollments-by-status" -> {
                csv = labelValueCsv(analyticsService.enrollmentsByStatus(), "Statut", "Nombre", "");
                filename = "inscriptions-par-statut.csv";
            }
            case "top-trainings" -> {
                csv = labelValueCsv(analyticsService.topTrainings(limit), "Formation", "Inscriptions", "Revenu (€)");
                filename = "formations-top.csv";
            }
            case "segments" -> {
                csv = labelValueCsv(analyticsService.revenueBySegment(), "Segment", "Revenu (€)", "Commandes");
                filename = "revenu-par-segment.csv";
            }
            case "acquisition-funnel" -> {
                FunnelDto f = analyticsService.acquisitionFunnel();
                List<List<Object>> rows = f.getSteps().stream()
                        .map(s -> List.<Object>of(s.getLabel(), s.getCount(), s.getShareOfTop() + " %"))
                        .toList();
                csv = CsvWriter.toCsv(List.of("Étape", "Volume", "Part du sommet"), rows);
                filename = "entonnoir-acquisition.csv";
            }
            default -> throw new IllegalArgumentException("Jeu de données inconnu : " + dataset);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static String timeseriesCsv(List<TimeseriesPointDto> points, String valueHeader, String countHeader) {
        boolean withValue = valueHeader != null && !valueHeader.isBlank();
        List<String> headers = withValue
                ? List.of("Jour", valueHeader, countHeader)
                : List.of("Jour", countHeader);
        List<List<Object>> rows = points.stream()
                .map(p -> withValue
                        ? List.<Object>of(p.getDay(), p.getRevenue(), p.getCount())
                        : List.<Object>of(p.getDay(), p.getCount()))
                .toList();
        return CsvWriter.toCsv(headers, rows);
    }

    private static String labelValueCsv(List<LabelValueDto> items, String labelHeader, String valueHeader, String secondaryHeader) {
        boolean withSecondary = secondaryHeader != null && !secondaryHeader.isBlank();
        List<String> headers = withSecondary
                ? List.of(labelHeader, valueHeader, secondaryHeader)
                : List.of(labelHeader, valueHeader);
        List<List<Object>> rows = items.stream()
                .map(i -> withSecondary
                        ? List.<Object>of(i.getLabel(), i.getValue(), i.getSecondary())
                        : List.<Object>of(i.getLabel(), i.getValue()))
                .toList();
        return CsvWriter.toCsv(headers, rows);
    }
}
