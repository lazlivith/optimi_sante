package com.optimisante.backend.domain.governance.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Vérification de l'étanchéité du Soft Delete (exigence CDC reprise du Sprint 6 : "vérification
 * Soft Delete"). Pour chaque table concernée : combien de lignes actives / effacées logiquement /
 * anonymisées, et si l'entité JPA applique bien un filtre global ({@code @SQLRestriction}) qui
 * masque les lignes effacées à toute l'application.
 */
@Getter
@Builder
public class SoftDeleteReportDto {

    private List<TableReport> tables;
    private String verdict;
    private boolean healthy;

    @Getter
    @Builder
    public static class TableReport {
        private String table;
        private String entity;
        /** true si l'entité JPA porte @SQLRestriction("deleted_at IS NULL"). */
        private boolean globalFilterEnforced;
        private long totalRows;
        private long activeRows;
        private long softDeletedRows;
        private long anonymizedRows;
        private String note;
    }
}
